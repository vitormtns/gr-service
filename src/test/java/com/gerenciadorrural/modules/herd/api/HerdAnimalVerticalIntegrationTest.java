package com.gerenciadorrural.modules.herd.api;

import com.gerenciadorrural.infrastructure.database.PostgresTestEnvironment;
import com.gerenciadorrural.infrastructure.database.SpringPostgresTestSupport;
import com.gerenciadorrural.modules.herd.domain.HerdAnimalQueryRepository;
import com.gerenciadorrural.modules.herd.infrastructure.JdbcHerdAnimalQueryRepository;
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
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.sql.Connection;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
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
class HerdAnimalVerticalIntegrationTest extends SpringPostgresTestSupport {

    private static final String ISSUER = "https://auth.example.test/auth/v1";
    private static final String AUDIENCE = "authenticated";
    private static final String HMAC_SECRET =
        "test-only-hmac-key-with-at-least-32-bytes";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ApplicationContext applicationContext;

    private UUID userId;
    private UUID tenantAId;
    private UUID tenantBId;
    private UUID farmA1Id;
    private UUID farmA2Id;
    private UUID farmB1Id;

    @BeforeEach
    void seed() throws Exception {
        PostgresTestEnvironment.clearUsers();

        userId = UUID.randomUUID();
        tenantAId = UUID.randomUUID();
        tenantBId = UUID.randomUUID();
        farmA1Id = UUID.randomUUID();
        farmA2Id = UUID.randomUUID();
        farmB1Id = UUID.randomUUID();

        UUID membershipId = UUID.randomUUID();

        String sql = """
            insert into app.users (
                id,
                status
            ) values (
                ?,
                'ACTIVE'
            );

            insert into app.organizations (
                id,
                name,
                status
            ) values
                (?, 'A', 'ACTIVE'),
                (?, 'B', 'ACTIVE');

            insert into app.organization_memberships (
                id,
                tenant_id,
                user_id,
                role_key,
                status,
                farm_scope_mode
            ) values (
                ?,
                ?,
                ?,
                'OWNER',
                'ACTIVE',
                'SELECTED_FARMS'
            );

            insert into app.farms (
                id,
                tenant_id,
                name,
                status
            ) values
                (?, ?, ?, 'ACTIVE'),
                (?, ?, ?, 'ACTIVE'),
                (?, ?, ?, 'ACTIVE');

            insert into app.membership_farm_scopes (
                tenant_id,
                membership_id,
                farm_id
            ) values (
                ?,
                ?,
                ?
            );

            insert into app.animals (
                id,
                tenant_id,
                farm_id,
                identification,
                name,
                sex,
                birth_date,
                status
            ) values
                (?, ?, ?, ?, ?, 'FEMALE', '2024-03-15', 'ACTIVE'),
                (?, ?, ?, ?, ?, 'MALE', null, 'ACTIVE'),
                (?, ?, ?, ?, ?, 'FEMALE', null, 'ACTIVE'),
                (?, ?, ?, ?, ?, 'MALE', null, 'ACTIVE');
            """;

        try (
            Connection connection = PostgresTestEnvironment.adminConnection();
            var statement = connection.prepareStatement(sql)
        ) {
            int index = 1;

            statement.setObject(index++, userId);

            statement.setObject(index++, tenantAId);
            statement.setObject(index++, tenantBId);

            statement.setObject(index++, membershipId);
            statement.setObject(index++, tenantAId);
            statement.setObject(index++, userId);

            statement.setObject(index++, farmA1Id);
            statement.setObject(index++, tenantAId);
            statement.setObject(index++, "A1");

            statement.setObject(index++, farmA2Id);
            statement.setObject(index++, tenantAId);
            statement.setObject(index++, "A2");

            statement.setObject(index++, farmB1Id);
            statement.setObject(index++, tenantBId);
            statement.setObject(index++, "B1");

            statement.setObject(index++, tenantAId);
            statement.setObject(index++, membershipId);
            statement.setObject(index++, farmA1Id);

            statement.setObject(index++, UUID.randomUUID());
            statement.setObject(index++, tenantAId);
            statement.setObject(index++, farmA1Id);
            statement.setObject(index++, "B-002");
            statement.setObject(index++, "Brisa");

            statement.setObject(index++, UUID.randomUUID());
            statement.setObject(index++, tenantAId);
            statement.setObject(index++, farmA1Id);
            statement.setObject(index++, "A-001");
            statement.setObject(index++, null);

            statement.setObject(index++, UUID.randomUUID());
            statement.setObject(index++, tenantAId);
            statement.setObject(index++, farmA2Id);
            statement.setObject(index++, "C-003");
            statement.setObject(index++, "Outra Fazenda");

            statement.setObject(index++, UUID.randomUUID());
            statement.setObject(index++, tenantBId);
            statement.setObject(index++, farmB1Id);
            statement.setObject(index++, "D-004");
            statement.setObject(index++, "Outro Tenant");

            statement.executeUpdate();
        }
    }

    @Test
    void listsOnlyAnimalsFromTheAuthorizedCurrentFarmThroughTheRealVerticalFlow()
        throws Exception {

        assertThat(applicationContext.getBean(HerdAnimalQueryRepository.class))
            .isInstanceOf(JdbcHerdAnimalQueryRepository.class);

        mvc.perform(
                get("/api/v1/herd/animals")
                    .header(
                        HttpHeaders.AUTHORIZATION,
                        "Bearer " + token()
                    )
                    .header("X-Organization-Id", tenantAId)
                    .header("X-Farm-Id", farmA1Id)
            )
            .andExpect(status().isOk())
            .andExpect(
                header().string(
                    HttpHeaders.CACHE_CONTROL,
                    containsString("no-store")
                )
            )
            .andExpect(jsonPath("$.*", hasSize(5)))
            .andExpect(jsonPath("$.page").value(0))
            .andExpect(jsonPath("$.size").value(50))
            .andExpect(jsonPath("$.totalElements").value(2))
            .andExpect(jsonPath("$.totalPages").value(1))
            .andExpect(jsonPath("$.items", hasSize(2)))

            .andExpect(jsonPath("$.items[0].*", hasSize(7)))
            .andExpect(jsonPath("$.items[0].id").isNotEmpty())
            .andExpect(jsonPath("$.items[0].identification").value("A-001"))
            .andExpect(jsonPath("$.items[0].name").value(nullValue()))
            .andExpect(jsonPath("$.items[0].sex").value("MALE"))
            .andExpect(jsonPath("$.items[0].birthDate").value(nullValue()))
            .andExpect(jsonPath("$.items[0].status").value("ACTIVE"))
            .andExpect(jsonPath("$.items[0].version").value(0))

            .andExpect(jsonPath("$.items[1].*", hasSize(7)))
            .andExpect(jsonPath("$.items[1].id").isNotEmpty())
            .andExpect(jsonPath("$.items[1].identification").value("B-002"))
            .andExpect(jsonPath("$.items[1].name").value("Brisa"))
            .andExpect(jsonPath("$.items[1].sex").value("FEMALE"))
            .andExpect(jsonPath("$.items[1].birthDate").value("2024-03-15"))
            .andExpect(jsonPath("$.items[1].status").value("ACTIVE"))
            .andExpect(jsonPath("$.items[1].version").value(0))

            .andExpect(jsonPath("$.items[0].tenantId").doesNotExist())
            .andExpect(jsonPath("$.items[0].farmId").doesNotExist())
            .andExpect(jsonPath("$.items[0].createdAt").doesNotExist())
            .andExpect(jsonPath("$.items[0].updatedAt").doesNotExist())
            .andExpect(jsonPath("$.items[0].userId").doesNotExist())
            .andExpect(jsonPath("$.items[0].membershipId").doesNotExist())
            .andExpect(jsonPath("$.items[0].role").doesNotExist())
            .andExpect(jsonPath("$.items[0].farmScopeMode").doesNotExist())
            .andExpect(jsonPath("$.items[0].token").doesNotExist())
            .andExpect(jsonPath("$.items[0].email").doesNotExist())
            .andExpect(jsonPath("$.items[0].claims").doesNotExist())

            .andExpect(content().string(not(containsString("C-003"))))
            .andExpect(content().string(not(containsString("D-004"))))
            .andExpect(content().string(not(containsString("Outra Fazenda"))))
            .andExpect(content().string(not(containsString("Outro Tenant"))));
    }

    @Test
    void paginatesAuthorizedFarmAnimalsWithStableOrdering() throws Exception {
        String bearerToken = "Bearer " + token();

        mvc.perform(
                get("/api/v1/herd/animals")
                    .queryParam("page", "0")
                    .queryParam("size", "1")
                    .header(HttpHeaders.AUTHORIZATION, bearerToken)
                    .header("X-Organization-Id", tenantAId)
                    .header("X-Farm-Id", farmA1Id)
            )
            .andExpect(status().isOk())
            .andExpect(
                header().string(
                    HttpHeaders.CACHE_CONTROL,
                    containsString("no-store")
                )
            )
            .andExpect(jsonPath("$.*", hasSize(5)))
            .andExpect(jsonPath("$.page").value(0))
            .andExpect(jsonPath("$.size").value(1))
            .andExpect(jsonPath("$.totalElements").value(2))
            .andExpect(jsonPath("$.totalPages").value(2))
            .andExpect(jsonPath("$.items", hasSize(1)))
            .andExpect(jsonPath("$.items[0].*", hasSize(7)))
            .andExpect(jsonPath("$.items[0].identification").value("A-001"))
            .andExpect(content().string(not(containsString("B-002"))))
            .andExpect(content().string(not(containsString("C-003"))))
            .andExpect(content().string(not(containsString("D-004"))));

        mvc.perform(
                get("/api/v1/herd/animals")
                    .queryParam("page", "1")
                    .queryParam("size", "1")
                    .header(HttpHeaders.AUTHORIZATION, bearerToken)
                    .header("X-Organization-Id", tenantAId)
                    .header("X-Farm-Id", farmA1Id)
            )
            .andExpect(status().isOk())
            .andExpect(
                header().string(
                    HttpHeaders.CACHE_CONTROL,
                    containsString("no-store")
                )
            )
            .andExpect(jsonPath("$.*", hasSize(5)))
            .andExpect(jsonPath("$.page").value(1))
            .andExpect(jsonPath("$.size").value(1))
            .andExpect(jsonPath("$.totalElements").value(2))
            .andExpect(jsonPath("$.totalPages").value(2))
            .andExpect(jsonPath("$.items", hasSize(1)))
            .andExpect(jsonPath("$.items[0].*", hasSize(7)))
            .andExpect(jsonPath("$.items[0].identification").value("B-002"))
            .andExpect(content().string(not(containsString("A-001"))))
            .andExpect(content().string(not(containsString("C-003"))))
            .andExpect(content().string(not(containsString("D-004"))));

        mvc.perform(
                get("/api/v1/herd/animals")
                    .queryParam("page", "2")
                    .queryParam("size", "1")
                    .header(HttpHeaders.AUTHORIZATION, bearerToken)
                    .header("X-Organization-Id", tenantAId)
                    .header("X-Farm-Id", farmA1Id)
            )
            .andExpect(status().isOk())
            .andExpect(
                header().string(
                    HttpHeaders.CACHE_CONTROL,
                    containsString("no-store")
                )
            )
            .andExpect(jsonPath("$.*", hasSize(5)))
            .andExpect(jsonPath("$.page").value(2))
            .andExpect(jsonPath("$.size").value(1))
            .andExpect(jsonPath("$.totalElements").value(2))
            .andExpect(jsonPath("$.totalPages").value(2))
            .andExpect(jsonPath("$.items").isEmpty())
            .andExpect(content().string(not(containsString("A-001"))))
            .andExpect(content().string(not(containsString("B-002"))))
            .andExpect(content().string(not(containsString("C-003"))))
            .andExpect(content().string(not(containsString("D-004"))));
    }

    @Test
    void filtersAuthorizedFarmAnimalsThroughTheRealVerticalFlow()
        throws Exception {
        String bearerToken = "Bearer " + token();

        mvc.perform(
                get("/api/v1/herd/animals")
                    .queryParam("sex", "MALE")
                    .header(HttpHeaders.AUTHORIZATION, bearerToken)
                    .header("X-Organization-Id", tenantAId)
                    .header("X-Farm-Id", farmA1Id)
            )
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.items", hasSize(1)))
            .andExpect(jsonPath("$.items[0].identification").value("A-001"))
            .andExpect(content().string(not(containsString("B-002"))))
            .andExpect(content().string(not(containsString("C-003"))))
            .andExpect(content().string(not(containsString("D-004"))))
            .andExpect(jsonPath("$.items[0].tenantId").doesNotExist())
            .andExpect(jsonPath("$.items[0].farmId").doesNotExist());

        mvc.perform(
                get("/api/v1/herd/animals")
                    .queryParam("sex", "FEMALE")
                    .header(HttpHeaders.AUTHORIZATION, bearerToken)
                    .header("X-Organization-Id", tenantAId)
                    .header("X-Farm-Id", farmA1Id)
            )
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.items", hasSize(1)))
            .andExpect(jsonPath("$.items[0].identification").value("B-002"))
            .andExpect(content().string(not(containsString("A-001"))))
            .andExpect(content().string(not(containsString("C-003"))))
            .andExpect(content().string(not(containsString("D-004"))))
            .andExpect(jsonPath("$.items[0].tenantId").doesNotExist())
            .andExpect(jsonPath("$.items[0].farmId").doesNotExist());

        mvc.perform(
                get("/api/v1/herd/animals")
                    .queryParam("search", "a-001")
                    .header(HttpHeaders.AUTHORIZATION, bearerToken)
                    .header("X-Organization-Id", tenantAId)
                    .header("X-Farm-Id", farmA1Id)
            )
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.items", hasSize(1)))
            .andExpect(jsonPath("$.items[0].identification").value("A-001"))
            .andExpect(content().string(not(containsString("B-002"))))
            .andExpect(content().string(not(containsString("C-003"))))
            .andExpect(content().string(not(containsString("D-004"))))
            .andExpect(jsonPath("$.items[0].tenantId").doesNotExist())
            .andExpect(jsonPath("$.items[0].farmId").doesNotExist());

        mvc.perform(
                get("/api/v1/herd/animals")
                    .queryParam("search", "brisa")
                    .header(HttpHeaders.AUTHORIZATION, bearerToken)
                    .header("X-Organization-Id", tenantAId)
                    .header("X-Farm-Id", farmA1Id)
            )
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.items", hasSize(1)))
            .andExpect(jsonPath("$.items[0].identification").value("B-002"))
            .andExpect(jsonPath("$.items[0].name").value("Brisa"))
            .andExpect(content().string(not(containsString("A-001"))))
            .andExpect(content().string(not(containsString("C-003"))))
            .andExpect(content().string(not(containsString("D-004"))))
            .andExpect(jsonPath("$.items[0].tenantId").doesNotExist())
            .andExpect(jsonPath("$.items[0].farmId").doesNotExist());

        mvc.perform(
                get("/api/v1/herd/animals")
                    .queryParam("search", "inexistente")
                    .header(HttpHeaders.AUTHORIZATION, bearerToken)
                    .header("X-Organization-Id", tenantAId)
                    .header("X-Farm-Id", farmA1Id)
            )
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
            .andExpect(jsonPath("$.items").isEmpty())
            .andExpect(jsonPath("$.totalElements").value(0))
            .andExpect(jsonPath("$.totalPages").value(0))
            .andExpect(content().string(not(containsString("A-001"))))
            .andExpect(content().string(not(containsString("B-002"))))
            .andExpect(content().string(not(containsString("C-003"))))
            .andExpect(content().string(not(containsString("D-004"))))
            .andExpect(jsonPath("$.items[0].tenantId").doesNotExist())
            .andExpect(jsonPath("$.items[0].farmId").doesNotExist());

        mvc.perform(
                get("/api/v1/herd/animals")
                    .queryParam("search", "brisa")
                    .queryParam("sex", "FEMALE")
                    .queryParam("status", "ACTIVE")
                    .header(HttpHeaders.AUTHORIZATION, bearerToken)
                    .header("X-Organization-Id", tenantAId)
                    .header("X-Farm-Id", farmA1Id)
            )
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.items", hasSize(1)))
            .andExpect(jsonPath("$.items[0].identification").value("B-002"))
            .andExpect(content().string(not(containsString("A-001"))))
            .andExpect(content().string(not(containsString("C-003"))))
            .andExpect(content().string(not(containsString("D-004"))))
            .andExpect(jsonPath("$.items[0].tenantId").doesNotExist())
            .andExpect(jsonPath("$.items[0].farmId").doesNotExist());
    }

    private String token() throws Exception {
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

        signedJwt.sign(
            new MACSigner(HMAC_SECRET.getBytes())
        );

        return signedJwt.serialize();
    }
}
