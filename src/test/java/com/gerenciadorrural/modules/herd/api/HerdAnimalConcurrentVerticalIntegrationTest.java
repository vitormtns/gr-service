package com.gerenciadorrural.modules.herd.api;

import com.gerenciadorrural.infrastructure.database.PostgresTestEnvironment;
import com.gerenciadorrural.infrastructure.database.SpringPostgresTestSupport;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.sql.Connection;
import java.time.Instant;
import java.util.Date;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@SpringBootTest(
    properties = {
        "app.security.supabase.mode=HMAC",
        "app.security.supabase.algorithm=HS256",
        "app.security.supabase.issuer=https://auth.example.test/auth/v1",
        "app.security.supabase.hmac-secret=test-only-hmac-key-with-at-least-32-bytes",
        "app.security.supabase.audiences=authenticated",
        "app.security.supabase.accepted-token-roles=authenticated"
    }
)
@AutoConfigureMockMvc
class HerdAnimalConcurrentVerticalIntegrationTest
    extends SpringPostgresTestSupport {

    private static final String ISSUER = "https://auth.example.test/auth/v1";
    private static final String AUDIENCE = "authenticated";
    private static final String HMAC_SECRET =
        "test-only-hmac-key-with-at-least-32-bytes";
    private static final int ITERATIONS_PER_CONTEXT = 15;

    @Autowired
    private MockMvc mvc;

    private UUID userAId;
    private UUID tenantAId;
    private UUID farmAId;
    private UUID userBId;
    private UUID tenantBId;
    private UUID farmBId;

    @BeforeEach
    void seed() throws Exception {
        PostgresTestEnvironment.clearUsers();

        userAId = UUID.randomUUID();
        tenantAId = UUID.randomUUID();
        farmAId = UUID.randomUUID();
        userBId = UUID.randomUUID();
        tenantBId = UUID.randomUUID();
        farmBId = UUID.randomUUID();

        String sql = """
            insert into app.users (id, status) values (?, 'ACTIVE'), (?, 'ACTIVE');

            insert into app.organizations (id, name, status) values
                (?, 'A', 'ACTIVE'),
                (?, 'B', 'ACTIVE');

            insert into app.organization_memberships (
                id, tenant_id, user_id, role_key, status, farm_scope_mode
            ) values
                (?, ?, ?, 'OWNER', 'ACTIVE', 'SELECTED_FARMS'),
                (?, ?, ?, 'OWNER', 'ACTIVE', 'SELECTED_FARMS');

            insert into app.farms (id, tenant_id, name, status) values
                (?, ?, 'Fazenda A', 'ACTIVE'),
                (?, ?, 'Fazenda B', 'ACTIVE');

            insert into app.membership_farm_scopes (tenant_id, membership_id, farm_id)
            values (?, ?, ?), (?, ?, ?);

            insert into app.animals (
                id, tenant_id, farm_id, identification, name, sex, birth_date, status
            ) values
                (?, ?, ?, 'A-001', 'Animal A', 'MALE', null, 'ACTIVE'),
                (?, ?, ?, 'B-001', 'Animal B', 'FEMALE', null, 'ACTIVE');
            """;

        UUID membershipAId = UUID.randomUUID();
        UUID membershipBId = UUID.randomUUID();
        try (
            Connection connection = PostgresTestEnvironment.adminConnection();
            var statement = connection.prepareStatement(sql)
        ) {
            int index = 1;
            statement.setObject(index++, userAId);
            statement.setObject(index++, userBId);
            statement.setObject(index++, tenantAId);
            statement.setObject(index++, tenantBId);
            statement.setObject(index++, membershipAId);
            statement.setObject(index++, tenantAId);
            statement.setObject(index++, userAId);
            statement.setObject(index++, membershipBId);
            statement.setObject(index++, tenantBId);
            statement.setObject(index++, userBId);
            statement.setObject(index++, farmAId);
            statement.setObject(index++, tenantAId);
            statement.setObject(index++, farmBId);
            statement.setObject(index++, tenantBId);
            statement.setObject(index++, tenantAId);
            statement.setObject(index++, membershipAId);
            statement.setObject(index++, farmAId);
            statement.setObject(index++, tenantBId);
            statement.setObject(index++, membershipBId);
            statement.setObject(index++, farmBId);
            statement.setObject(index++, UUID.randomUUID());
            statement.setObject(index++, tenantAId);
            statement.setObject(index++, farmAId);
            statement.setObject(index++, UUID.randomUUID());
            statement.setObject(index++, tenantBId);
            statement.setObject(index++, farmBId);
            statement.executeUpdate();
        }
    }

    @Test
    void isolatesTenantContextsAcrossConcurrentAnimalListingRequests()
        throws Exception {
        CyclicBarrier startBarrier = new CyclicBarrier(2);
        Set<Long> requestThreadIds = ConcurrentHashMap.newKeySet();
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<Void> contextA = executor.submit(requestTask(
                startBarrier,
                requestThreadIds,
                userAId,
                tenantAId,
                farmAId,
                "A-001",
                "B-001"
            ));
            Future<Void> contextB = executor.submit(requestTask(
                startBarrier,
                requestThreadIds,
                userBId,
                tenantBId,
                farmBId,
                "B-001",
                "A-001"
            ));

            contextA.get(30, TimeUnit.SECONDS);
            contextB.get(30, TimeUnit.SECONDS);

            assertThat(requestThreadIds).hasSize(2);
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private Callable<Void> requestTask(
        CyclicBarrier startBarrier,
        Set<Long> requestThreadIds,
        UUID userId,
        UUID tenantId,
        UUID farmId,
        String expectedIdentification,
        String unexpectedIdentification
    ) {
        return () -> {
            requestThreadIds.add(Thread.currentThread().threadId());
            startBarrier.await(10, TimeUnit.SECONDS);

            for (int iteration = 0; iteration < ITERATIONS_PER_CONTEXT; iteration++) {
                mvc.perform(
                        get("/api/v1/herd/animals")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(userId))
                            .header("X-Organization-Id", tenantId)
                            .header("X-Farm-Id", farmId)
                    )
                    .andExpect(status().isOk())
                    .andExpect(
                        header().string(
                            HttpHeaders.CACHE_CONTROL,
                            containsString("no-store")
                        )
                    )
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.items", hasSize(1)))
                    .andExpect(
                        jsonPath("$.items[0].identification")
                            .value(expectedIdentification)
                    )
                    .andExpect(content().string(not(containsString(unexpectedIdentification))))
                    .andExpect(jsonPath("$.items[0].tenantId").doesNotExist())
                    .andExpect(jsonPath("$.items[0].farmId").doesNotExist());
            }

            return null;
        };
    }

    private String token(UUID userId) throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
            .issuer(ISSUER)
            .audience(AUDIENCE)
            .subject(userId.toString())
            .claim("role", "authenticated")
            .issueTime(new Date())
            .expirationTime(Date.from(Instant.now().plusSeconds(300)))
            .build();

        SignedJWT signedJwt = new SignedJWT(
            new JWSHeader(JWSAlgorithm.HS256),
            claims
        );
        signedJwt.sign(new MACSigner(HMAC_SECRET.getBytes()));
        return signedJwt.serialize();
    }
}
