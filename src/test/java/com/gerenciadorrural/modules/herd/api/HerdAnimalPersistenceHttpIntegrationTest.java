package com.gerenciadorrural.modules.herd.api;

import com.gerenciadorrural.infrastructure.database.PostgresTestEnvironment;
import com.gerenciadorrural.infrastructure.database.SpringPostgresTestSupport;
import com.gerenciadorrural.modules.herd.infrastructure.JdbcHerdAnimalQueryRepository;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import java.sql.Connection;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verifyNoInteractions;
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
class HerdAnimalPersistenceHttpIntegrationTest extends SpringPostgresTestSupport {

    private static final String ISSUER = "https://auth.example.test/auth/v1";
    private static final String AUDIENCE = "authenticated";
    private static final String HMAC_SECRET =
        "test-only-hmac-key-with-at-least-32-bytes";

    @Autowired
    private MockMvc mvc;

    @MockitoSpyBean
    private JdbcHerdAnimalQueryRepository herdAnimalQueryRepository;

    private UUID userId;
    private UUID tenantId;
    private UUID farmId;

    @BeforeEach
    void seed() throws Exception {
        PostgresTestEnvironment.clearUsers();

        userId = UUID.randomUUID();
        tenantId = UUID.randomUUID();
        farmId = UUID.randomUUID();
        UUID membershipId = UUID.randomUUID();

        String sql = """
            insert into app.users (id, status) values (?, 'ACTIVE');
            insert into app.organizations (id, name, status) values (?, 'Organização', 'ACTIVE');
            insert into app.organization_memberships (
                id, tenant_id, user_id, role_key, status, farm_scope_mode
            ) values (?, ?, ?, 'OWNER', 'ACTIVE', 'SELECTED_FARMS');
            insert into app.farms (id, tenant_id, name, status) values (?, ?, 'Fazenda', 'ACTIVE');
            insert into app.membership_farm_scopes (tenant_id, membership_id, farm_id) values (?, ?, ?);
            """;

        try (
            Connection connection = PostgresTestEnvironment.adminConnection();
            var statement = connection.prepareStatement(sql)
        ) {
            int index = 1;
            statement.setObject(index++, userId);
            statement.setObject(index++, tenantId);
            statement.setObject(index++, membershipId);
            statement.setObject(index++, tenantId);
            statement.setObject(index++, userId);
            statement.setObject(index++, farmId);
            statement.setObject(index++, tenantId);
            statement.setObject(index++, tenantId);
            statement.setObject(index++, membershipId);
            statement.setObject(index++, farmId);
            statement.executeUpdate();
        }
    }

    @AfterEach
    void resetRepositorySpy() {
        reset(herdAnimalQueryRepository);
    }

    @Test
    void persistenceFailureReturnsSanitized503() throws Exception {
        String internalMessage = "SQLException DataAccessException JdbcTemplate "
            + "select * from app.animals where tenant_id = ? and farm_id = ? "
            + "jdbc:postgresql://localhost:5432/rural user=postgres password=secret";
        doThrow(new DataAccessResourceFailureException(internalMessage))
            .when(herdAnimalQueryRepository)
            .list(any(), any(), any());

        mvc.perform(authorizedRequest().header("X-Request-ID", "herd-persistence-failure"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.code").value("HERD_PERSISTENCE_UNAVAILABLE"))
            .andExpect(
                jsonPath("$.message")
                    .value("A consulta de animais está temporariamente indisponível")
            )
            .andExpect(jsonPath("$.requestId").value("herd-persistence-failure"))
            .andExpect(content().string(not(containsString("SQLException"))))
            .andExpect(content().string(not(containsString("DataAccessException"))))
            .andExpect(content().string(not(containsString("JdbcTemplate"))))
            .andExpect(content().string(not(containsString("app.animals"))))
            .andExpect(content().string(not(containsString("tenant_id"))))
            .andExpect(content().string(not(containsString("farm_id"))))
            .andExpect(content().string(not(containsString("jdbc:postgresql"))))
            .andExpect(content().string(not(containsString("localhost"))))
            .andExpect(content().string(not(containsString("password=secret"))))
            .andExpect(content().string(not(containsString(internalMessage))));
    }

    @Test
    void invalidQueryRemainsBadRequest() throws Exception {
        mvc.perform(authorizedRequest().queryParam("sex", "INVALID"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("HERD_QUERY_INVALID"));

        verifyNoInteractions(herdAnimalQueryRepository);
    }

    @Test
    void missingAuthenticationRemainsUnauthorized() throws Exception {
        mvc.perform(get("/api/v1/herd/animals"))
            .andExpect(status().isUnauthorized());

        verifyNoInteractions(herdAnimalQueryRepository);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
        authorizedRequest() throws Exception {

        return get("/api/v1/herd/animals")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token())
            .header("X-Organization-Id", tenantId)
            .header("X-Farm-Id", farmId);
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
        signedJwt.sign(new MACSigner(HMAC_SECRET.getBytes()));
        return signedJwt.serialize();
    }
}
