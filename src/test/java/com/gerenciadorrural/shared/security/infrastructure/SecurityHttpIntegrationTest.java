package com.gerenciadorrural.shared.security.infrastructure;

import com.gerenciadorrural.infrastructure.database.PostgresTestEnvironment;
import com.gerenciadorrural.infrastructure.database.SpringPostgresTestSupport;
import com.gerenciadorrural.modules.identity.domain.InternalUserRepository;
import com.gerenciadorrural.modules.organizations.domain.AccessibleOrganizationRepository;
import com.gerenciadorrural.modules.organizations.application.ResolveTenantContext;
import com.gerenciadorrural.modules.organizations.application.TenantContextNotAvailableException;
import com.gerenciadorrural.shared.tenancy.TenantContext;
import com.gerenciadorrural.shared.tenancy.TenantId;
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
import static org.mockito.Mockito.doReturn;
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

    @MockitoSpyBean
    private AccessibleOrganizationRepository accessibleOrganizationRepository;

    @MockitoSpyBean
    private ResolveTenantContext resolveTenantContext;

    @BeforeEach
    void clearDatabase() throws SQLException {
        PostgresTestEnvironment.clearUsers();
    }

    @AfterEach
    void resetRepositorySpy() {
        reset(internalUserRepository, accessibleOrganizationRepository, resolveTenantContext);
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
    void organizationsWithoutTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/me/organizations"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("authentication_required"));
    }

    @Test
    void farmsWithoutTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/me/organizations/00000000-0000-0000-0000-000000000001/farms"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void farmsWithAnInvalidOrganizationIdReturn400() throws Exception {
        String token = sign(validClaims(UUID.randomUUID()), SECRET);
        mockMvc.perform(get("/api/v1/me/organizations/invalid/farms")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void contextWithoutTokenReturnsExistingJson401() throws Exception {
        mockMvc.perform(get("/api/v1/context").header("X-Request-ID", "context-without-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("authentication_required"))
                .andExpect(jsonPath("$.requestId").value("context-without-token"));
    }

    @Test
    void contextMissingOrInvalidHeadersReturnsSafe400() throws Exception {
        String token = sign(validClaims(UUID.randomUUID()), SECRET);
        mockMvc.perform(get("/api/v1/context").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("TENANT_CONTEXT_HEADER_MISSING"));
        mockMvc.perform(get("/api/v1/context").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("X-Organization-Id", "invalid").header("X-Farm-Id", UUID.randomUUID().toString()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("TENANT_CONTEXT_HEADER_INVALID"));
    }

    @Test
    void validContextReturnsOnlyTheResolvedContractAndNoStore() throws Exception {
        UUID userId=UUID.randomUUID(), organizationId=UUID.randomUUID(), farmId=UUID.randomUUID(), membershipId=UUID.randomUUID();
        TenantContext context=new TenantContext(new TenantId(organizationId),userId,farmId,membershipId,"MANAGER","SELECTED_FARMS");
        doReturn(new ResolveTenantContext.Resolved(context,"Organização","Fazenda")).when(resolveTenantContext).execute(organizationId,farmId);
        String token=sign(validClaims(userId).claim("email","hidden@example.test"),SECRET);
        mockMvc.perform(get("/api/v1/context").header(HttpHeaders.AUTHORIZATION,"Bearer "+token).header("X-Organization-Id",organizationId).header("X-Farm-Id",farmId))
                .andExpect(status().isOk()).andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
                .andExpect(jsonPath("$.userId").value(userId.toString())).andExpect(jsonPath("$.organization.id").value(organizationId.toString()))
                .andExpect(jsonPath("$.farm.id").value(farmId.toString())).andExpect(jsonPath("$.membership.id").value(membershipId.toString()))
                .andExpect(jsonPath("$.membership.role").value("MANAGER")).andExpect(jsonPath("$.membership.farmScopeMode").value("SELECTED_FARMS"))
                .andExpect(jsonPath("$.accessToken").doesNotExist()).andExpect(jsonPath("$.email").doesNotExist()).andExpect(jsonPath("$.permissions").doesNotExist());
    }

    @Test
    void inaccessibleContextReturnsIndistinguishable404() throws Exception {
        UUID organizationId=UUID.randomUUID(),farmId=UUID.randomUUID();
        doThrow(new TenantContextNotAvailableException()).when(resolveTenantContext).execute(organizationId,farmId);
        mockMvc.perform(get("/api/v1/context").header(HttpHeaders.AUTHORIZATION,"Bearer "+sign(validClaims(UUID.randomUUID()),SECRET)).header("X-Organization-Id",organizationId).header("X-Farm-Id",farmId))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("TENANT_CONTEXT_NOT_AVAILABLE"))
                .andExpect(jsonPath("$.message", not(containsString(organizationId.toString()))));
    }

    @Test
    void contextEndpointUsesTheRealTransactionalPathAndRejectsCrossTenantCombinations() throws Exception {
        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();
        TenantContextData contextA = insertTenantContextData(userA, "Organização A", "Fazenda A", "ALL_FARMS");
        TenantContextData contextB = insertTenantContextData(userB, "Organização B", "Fazenda B", "SELECTED_FARMS");
        String tokenA = sign(validClaims(userA), SECRET);

        mockMvc.perform(get("/api/v1/context")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
                        .header("X-Organization-Id", contextA.organizationId())
                        .header("X-Farm-Id", contextA.farmId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(userA.toString()))
                .andExpect(jsonPath("$.organization.id").value(contextA.organizationId().toString()))
                .andExpect(jsonPath("$.farm.id").value(contextA.farmId().toString()))
                .andExpect(jsonPath("$.membership.id").value(contextA.membershipId().toString()))
                .andExpect(jsonPath("$.membership.role").value("VIEWER"))
                .andExpect(jsonPath("$.membership.farmScopeMode").value("ALL_FARMS"));

        mockMvc.perform(get("/api/v1/context")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
                        .header("X-Organization-Id", contextB.organizationId())
                        .header("X-Farm-Id", contextB.farmId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TENANT_CONTEXT_NOT_AVAILABLE"));

        mockMvc.perform(get("/api/v1/context")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
                        .header("X-Organization-Id", contextA.organizationId())
                        .header("X-Farm-Id", contextB.farmId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TENANT_CONTEXT_NOT_AVAILABLE"));
    }

    @Test
    void validTokenSynchronizesTheUserAndReturnsAnEmptyOrganizationList() throws Exception {
        UUID userId = UUID.randomUUID();
        String token = sign(validClaims(userId), SECRET);

        mockMvc.perform(get("/api/v1/me/organizations").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    void organizationsEndpointReturnsOnlyActiveMembershipsOfTheAuthenticatedUser() throws Exception {
        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();
        insertActiveUser(userA);
        insertActiveUser(userB);
        UUID visibleMembership = insertOrganizationMembership(userA, "Organização visível", "ACTIVE", "ACTIVE",
                "OWNER", "ALL_FARMS");
        insertOrganizationMembership(userA, "Organização bloqueada", "ACTIVE", "SUSPENDED", "ADMIN", "ALL_FARMS");
        insertOrganizationMembership(userB, "Organização de outra pessoa", "ACTIVE", "ACTIVE", "VIEWER", "SELECTED_FARMS");
        String token = sign(validClaims(userA).claim("role", "authenticated"), SECRET);

        mockMvc.perform(get("/api/v1/me/organizations").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].membershipId").value(visibleMembership.toString()))
                .andExpect(jsonPath("$.items[0].organizationName").value("Organização visível"))
                .andExpect(jsonPath("$.items[0].role").value("OWNER"))
                .andExpect(jsonPath("$.items[0].farmScopeMode").value("ALL_FARMS"))
                .andExpect(jsonPath("$.items[0].accessToken").doesNotExist())
                .andExpect(jsonPath("$.items[0].farmId").doesNotExist())
                .andExpect(jsonPath("$.items[0].permissions").doesNotExist());
    }

    @Test
    void organizationDatabaseFailureReturnsASafeResponse() throws Exception {
        doThrow(new CannotGetJdbcConnectionException("database-host.example.test:5432"))
                .when(accessibleOrganizationRepository).findActiveForCurrentUser(any());
        String token = sign(validClaims(UUID.randomUUID()), SECRET);

        mockMvc.perform(get("/api/v1/me/organizations")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("X-Request-ID", "organization-database-failure"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("IDENTITY_PERSISTENCE_UNAVAILABLE"))
                .andExpect(jsonPath("$.requestId").value("organization-database-failure"))
                .andExpect(jsonPath("$.message").value(not(containsString("database-host"))));
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

    private static void insertActiveUser(UUID userId) throws SQLException {
        try (Connection connection = PostgresTestEnvironment.adminConnection();
             var statement = connection.prepareStatement("insert into app.users (id, status) values (?, 'ACTIVE')")) {
            statement.setObject(1, userId);
            statement.executeUpdate();
        }
    }

    private static UUID insertOrganizationMembership(
            UUID userId,
            String organizationName,
            String organizationStatus,
            String membershipStatus,
            String role,
            String farmScopeMode
    ) throws SQLException {
        UUID organizationId = UUID.randomUUID();
        UUID membershipId = UUID.randomUUID();
        try (Connection connection = PostgresTestEnvironment.adminConnection();
             var organization = connection.prepareStatement(
                     "insert into app.organizations (id, name, status) values (?, ?, ?)");
             var membership = connection.prepareStatement("""
                     insert into app.organization_memberships
                         (id, tenant_id, user_id, role_key, status, farm_scope_mode)
                     values (?, ?, ?, ?, ?, ?)
                     """)) {
            organization.setObject(1, organizationId);
            organization.setString(2, organizationName);
            organization.setString(3, organizationStatus);
            organization.executeUpdate();
            membership.setObject(1, membershipId);
            membership.setObject(2, organizationId);
            membership.setObject(3, userId);
            membership.setString(4, role);
            membership.setString(5, membershipStatus);
            membership.setString(6, farmScopeMode);
            membership.executeUpdate();
        }
        return membershipId;
    }

    private static TenantContextData insertTenantContextData(
            UUID userId,
            String organizationName,
            String farmName,
            String farmScopeMode
    ) throws SQLException {
        UUID organizationId = UUID.randomUUID();
        UUID farmId = UUID.randomUUID();
        UUID membershipId = UUID.randomUUID();
        try (Connection connection = PostgresTestEnvironment.adminConnection();
             var user = connection.prepareStatement(
                     "insert into app.users (id, status) values (?, 'ACTIVE')");
             var organization = connection.prepareStatement(
                     "insert into app.organizations (id, name, status) values (?, ?, 'ACTIVE')");
             var farm = connection.prepareStatement(
                     "insert into app.farms (id, tenant_id, name, status) values (?, ?, ?, 'ACTIVE')");
             var membership = connection.prepareStatement("""
                     insert into app.organization_memberships
                         (id, tenant_id, user_id, role_key, status, farm_scope_mode)
                     values (?, ?, ?, 'VIEWER', 'ACTIVE', ?)
                     """);
             var scope = connection.prepareStatement("""
                     insert into app.membership_farm_scopes (tenant_id, membership_id, farm_id)
                     values (?, ?, ?)
                     """)) {
            user.setObject(1, userId);
            user.executeUpdate();
            organization.setObject(1, organizationId);
            organization.setString(2, organizationName);
            organization.executeUpdate();
            farm.setObject(1, farmId);
            farm.setObject(2, organizationId);
            farm.setString(3, farmName);
            farm.executeUpdate();
            membership.setObject(1, membershipId);
            membership.setObject(2, organizationId);
            membership.setObject(3, userId);
            membership.setString(4, farmScopeMode);
            membership.executeUpdate();
            if ("SELECTED_FARMS".equals(farmScopeMode)) {
                scope.setObject(1, organizationId);
                scope.setObject(2, membershipId);
                scope.setObject(3, farmId);
                scope.executeUpdate();
            }
        }
        return new TenantContextData(organizationId, farmId, membershipId);
    }

    private record TenantContextData(UUID organizationId, UUID farmId, UUID membershipId) {
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
