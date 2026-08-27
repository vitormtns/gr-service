package com.gerenciadorrural.shared.security.infrastructure;

import com.gerenciadorrural.infrastructure.database.PostgresTestEnvironment;
import com.gerenciadorrural.infrastructure.database.SpringPostgresTestSupport;
import com.gerenciadorrural.modules.identity.domain.InternalUserRepository;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;

@ActiveProfiles("test")
@SpringBootTest(properties = {
        "app.security.supabase.mode=HMAC",
        "app.security.supabase.algorithm=HS256",
        "app.security.supabase.issuer=https://auth.example.test/auth/v1",
        "app.security.supabase.hmac-secret=test-only-hmac-key-with-at-least-32-bytes",
        "app.security.supabase.audiences=authenticated",
        "app.security.supabase.accepted-token-roles=authenticated",
        "app.security.supabase.clock-skew=1s"
})
@AutoConfigureMockMvc
class SecurityHttpIntegrationTest extends SpringPostgresTestSupport {

    private static final String ISSUER = "https://auth.example.test/auth/v1";
    private static final String SECRET = "test-only-hmac-key-with-at-least-32-bytes";
    private static final String OTHER_SECRET = "different-test-key-with-at-least-32-bytes";

    @Autowired
    private MockMvc mockMvc;

    @MockitoSpyBean
    private InternalUserRepository internalUserRepository;

    @BeforeEach
    void clearDatabase() throws SQLException {
        PostgresTestEnvironment.clearUsers();
    }

    @AfterEach
    void resetRepositorySpy() {
        reset(internalUserRepository);
    }

    @Test
    void healthIsPublic() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void meWithoutTokenReturnsJson401WithRequestId() throws Exception {
        mockMvc.perform(get("/api/v1/me").header("X-Request-ID", "request-without-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.code").value("authentication_required"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.requestId").value("request-without-token"))
                .andExpect(header().string("X-Request-ID", "request-without-token"));
    }

    @Test
    void validTokenCreatesAndReturnsThePersistedInternalIdentity() throws Exception {
        UUID userId = UUID.randomUUID();
        String sessionId = UUID.randomUUID().toString();
        String token = sign(validClaims(userId)
                .claim("email", "pessoa@example.test")
                .claim("session_id", sessionId)
                .claim("aal", "aal1"), SECRET);

        mockMvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(userId.toString()))
                .andExpect(jsonPath("$.email").value("pessoa@example.test"))
                .andExpect(jsonPath("$.displayName").isEmpty())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.createdAt").isString())
                .andExpect(jsonPath("$.updatedAt").isString())
                .andExpect(jsonPath("$.authentication.sessionId").value(sessionId))
                .andExpect(jsonPath("$.authentication.authenticationLevel").value("aal1"))
                .andExpect(jsonPath("$.authentication.issuedAt").isString())
                .andExpect(jsonPath("$.authentication.expiresAt").isString())
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(jsonPath("$.organizationId").doesNotExist())
                .andExpect(jsonPath("$.farmId").doesNotExist());

        assertThatNoTenantOrOrganizationalRoleWasCreated();
    }

    @Test
    void repeatedRequestIsIdempotentAndChangedEmailUsesOptimisticLocking() throws Exception {
        UUID userId = UUID.randomUUID();
        String firstToken = sign(validClaims(userId).claim("email", "first@example.test"), SECRET);
        String changedToken = sign(validClaims(userId).claim("email", "changed@example.test"), SECRET);

        mockMvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + firstToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(0));
        mockMvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + firstToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(0));
        mockMvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + changedToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("changed@example.test"))
                .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    void tokenWithoutEmailStillCreatesAnInternalUser() throws Exception {
        String token = sign(validClaims(UUID.randomUUID()), SECRET);

        mockMvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").isEmpty())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void suspendedInternalUserReturnsSafe403() throws Exception {
        UUID userId = insertBlockedUser("SUSPENDED");
        String token = sign(validClaims(userId), SECRET);

        mockMvc.perform(get("/api/v1/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("X-Request-ID", "suspended-request"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("INTERNAL_USER_SUSPENDED"))
                .andExpect(jsonPath("$.requestId").value("suspended-request"))
                .andExpect(jsonPath("$.message").value("O acesso deste usuário está suspenso"));
    }

    @Test
    void deactivatedInternalUserReturnsSafe403() throws Exception {
        UUID userId = insertBlockedUser("DEACTIVATED");
        String token = sign(validClaims(userId), SECRET);

        mockMvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("INTERNAL_USER_DEACTIVATED"));
    }

    @Test
    void databaseFailureReturnsSafe503WithoutInternalDetails() throws Exception {
        doThrow(new CannotGetJdbcConnectionException("database-host.example.test:5432"))
                .when(internalUserRepository).findById(any());
        String token = sign(validClaims(UUID.randomUUID()), SECRET);

        mockMvc.perform(get("/api/v1/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("X-Request-ID", "database-failure"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("IDENTITY_PERSISTENCE_UNAVAILABLE"))
                .andExpect(jsonPath("$.requestId").value("database-failure"))
                .andExpect(jsonPath("$.message")
                        .value("O serviço de identidade está temporariamente indisponível"))
                .andExpect(jsonPath("$.message").value(not(containsString("database-host"))));
    }

    @Test
    void expiredTokenReturns401() throws Exception {
        Instant now = Instant.now();
        JWTClaimsSet.Builder claims = baseClaims(UUID.randomUUID())
                .issueTime(Date.from(now.minusSeconds(120)))
                .expirationTime(Date.from(now.minusSeconds(30)));

        assertUnauthorized(sign(claims, SECRET));
    }

    @Test
    void futureNotBeforeReturns401() throws Exception {
        JWTClaimsSet.Builder claims = validClaims(UUID.randomUUID())
                .notBeforeTime(Date.from(Instant.now().plusSeconds(60)));

        assertUnauthorized(sign(claims, SECRET));
    }

    @Test
    void missingIssuedAtReturns401() throws Exception {
        JWTClaimsSet.Builder claims = baseClaims(UUID.randomUUID())
                .expirationTime(Date.from(Instant.now().plusSeconds(300)));

        assertUnauthorized(sign(claims, SECRET));
    }

    @Test
    void missingExpirationReturns401() throws Exception {
        JWTClaimsSet.Builder claims = baseClaims(UUID.randomUUID())
                .issueTime(Date.from(Instant.now().minusSeconds(5)));

        assertUnauthorized(sign(claims, SECRET));
    }

    @Test
    void incorrectIssuerReturns401() throws Exception {
        assertUnauthorized(sign(validClaims(UUID.randomUUID()).issuer("https://wrong.example.test"), SECRET));
    }

    @Test
    void incorrectAudienceReturns401() throws Exception {
        assertUnauthorized(sign(validClaims(UUID.randomUUID()).audience(List.of("other")), SECRET));
    }

    @Test
    void missingSubjectReturns401() throws Exception {
        assertUnauthorized(sign(validClaims(null), SECRET));
    }

    @Test
    void nonUuidSubjectReturns401() throws Exception {
        assertUnauthorized(sign(validClaims(null).subject("invalid-subject"), SECRET));
    }

    @Test
    void incorrectSignatureReturns401() throws Exception {
        assertUnauthorized(sign(validClaims(UUID.randomUUID()), OTHER_SECRET));
    }

    @Test
    void nonexistentApiEndpointIsNotPublic() throws Exception {
        mockMvc.perform(get("/api/v1/not-found"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("authentication_required"));
    }

    @Test
    void actuatorInfoIsUnavailableAnd403IncludesRequestIdForAuthenticatedUser() throws Exception {
        String token = sign(validClaims(UUID.randomUUID()), SECRET);

        mockMvc.perform(get("/actuator/info")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("X-Request-ID", "forbidden-request"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("access_denied"))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.requestId").value("forbidden-request"));
    }

    private void assertUnauthorized(String token) throws Exception {
        mockMvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.code").value("authentication_required"))
                .andExpect(jsonPath("$.message").value("Autenticação necessária ou token inválido"));
    }

    private JWTClaimsSet.Builder validClaims(UUID subject) {
        Instant now = Instant.now();
        return baseClaims(subject)
                .issueTime(Date.from(now.minusSeconds(5)))
                .expirationTime(Date.from(now.plusSeconds(300)));
    }

    private JWTClaimsSet.Builder baseClaims(UUID subject) {
        JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .audience(List.of("authenticated"))
                .claim("role", "authenticated");
        if (subject != null) {
            builder.subject(subject.toString());
        }
        return builder;
    }

    private String sign(JWTClaimsSet.Builder claims, String secret) throws Exception {
        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.HS256).type(JOSEObjectType.JWT).build(),
                claims.build());
        JWSSigner signer = new MACSigner(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        jwt.sign(signer);
        return jwt.serialize();
    }

    private static UUID insertBlockedUser(String status) throws SQLException {
        UUID id = UUID.randomUUID();
        try (Connection connection = PostgresTestEnvironment.adminConnection();
             var statement = connection.prepareStatement("insert into app.users (id, status) values (?, ?)")) {
            statement.setObject(1, id);
            statement.setString(2, status);
            statement.executeUpdate();
        }
        return id;
    }

    private static void assertThatNoTenantOrOrganizationalRoleWasCreated() throws SQLException {
        try (Connection connection = PostgresTestEnvironment.adminConnection();
             var statement = connection.createStatement();
             var result = statement.executeQuery("""
                     select (select count(*) from app.organizations)
                          + (select count(*) from app.organization_memberships)
                     """)) {
            result.next();
            org.assertj.core.api.Assertions.assertThat(result.getLong(1)).isZero();
        }
    }
}
