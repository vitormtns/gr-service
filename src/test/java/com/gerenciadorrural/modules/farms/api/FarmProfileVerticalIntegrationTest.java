package com.gerenciadorrural.modules.farms.api;

import com.gerenciadorrural.infrastructure.database.PostgresTestEnvironment;
import com.gerenciadorrural.infrastructure.database.SpringPostgresTestSupport;
import com.nimbusds.jose.JOSEObjectType;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.sql.Connection;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@SpringBootTest(properties = {
        "app.security.supabase.mode=HMAC",
        "app.security.supabase.algorithm=HS256",
        "app.security.supabase.issuer=https://auth.example.test/auth/v1",
        "app.security.supabase.hmac-secret=test-only-hmac-key-with-at-least-32-bytes",
        "app.security.supabase.audiences=authenticated",
        "app.security.supabase.accepted-token-roles=authenticated"
})
@AutoConfigureMockMvc
class FarmProfileVerticalIntegrationTest extends SpringPostgresTestSupport {

    private static final String SECRET = "test-only-hmac-key-with-at-least-32-bytes";

    @Autowired
    private MockMvc mvc;

    private UUID userA;
    private UUID userB;
    private UUID organizationA;
    private UUID organizationB;
    private UUID membershipA;
    private UUID activeFarmA;
    private UUID inactiveFarmA;
    private UUID archivedFarmA;
    private UUID activeFarmB;

    @BeforeEach
    void seed() throws Exception {
        PostgresTestEnvironment.clearUsers();
        userA = UUID.randomUUID();
        userB = UUID.randomUUID();
        organizationA = UUID.randomUUID();
        organizationB = UUID.randomUUID();
        membershipA = UUID.randomUUID();
        activeFarmA = UUID.randomUUID();
        inactiveFarmA = UUID.randomUUID();
        archivedFarmA = UUID.randomUUID();
        activeFarmB = UUID.randomUUID();
        UUID membershipB = UUID.randomUUID();

        try (Connection connection = PostgresTestEnvironment.adminConnection();
             var statement = connection.prepareStatement("""
                     insert into app.users(id, status) values (?, 'ACTIVE'), (?, 'ACTIVE');
                     insert into app.organizations(id, name, status) values
                         (?, 'Organização A', 'ACTIVE'), (?, 'Organização B', 'ACTIVE');
                     insert into app.organization_memberships
                         (id, tenant_id, user_id, role_key, status, farm_scope_mode)
                     values
                         (?, ?, ?, 'OWNER', 'ACTIVE', 'ALL_FARMS'),
                         (?, ?, ?, 'OWNER', 'ACTIVE', 'ALL_FARMS');
                     insert into app.farms(id, tenant_id, name, status) values
                         (?, ?, 'Fazenda A', 'ACTIVE'),
                         (?, ?, 'Fazenda inativa', 'INACTIVE'),
                         (?, ?, 'Fazenda arquivada', 'ARCHIVED'),
                         (?, ?, 'Fazenda B', 'ACTIVE')
                     """)) {
            int index = 1;
            statement.setObject(index++, userA);
            statement.setObject(index++, userB);
            statement.setObject(index++, organizationA);
            statement.setObject(index++, organizationB);
            statement.setObject(index++, membershipA);
            statement.setObject(index++, organizationA);
            statement.setObject(index++, userA);
            statement.setObject(index++, membershipB);
            statement.setObject(index++, organizationB);
            statement.setObject(index++, userB);
            statement.setObject(index++, activeFarmA);
            statement.setObject(index++, organizationA);
            statement.setObject(index++, inactiveFarmA);
            statement.setObject(index++, organizationA);
            statement.setObject(index++, archivedFarmA);
            statement.setObject(index++, organizationA);
            statement.setObject(index++, activeFarmB);
            statement.setObject(index, organizationB);
            statement.executeUpdate();
        }
    }

    @Test
    void realJwtResolverExecutorRepositoryAndRlsReturnOnlyAuthorizedActiveFarm() throws Exception {
        mvc.perform(request(userA, organizationA, activeFarmA))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
                .andExpect(jsonPath("$.*", hasSize(4)))
                .andExpect(jsonPath("$.id").value(activeFarmA.toString()))
                .andExpect(jsonPath("$.organizationId").value(organizationA.toString()))
                .andExpect(jsonPath("$.name").value("Fazenda A"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.userId").doesNotExist())
                .andExpect(jsonPath("$.tenantId").doesNotExist())
                .andExpect(jsonPath("$.membershipId").doesNotExist())
                .andExpect(jsonPath("$.role").doesNotExist())
                .andExpect(jsonPath("$.farmScopeMode").doesNotExist())
                .andExpect(jsonPath("$.version").doesNotExist())
                .andExpect(jsonPath("$.createdAt").doesNotExist())
                .andExpect(jsonPath("$.updatedAt").doesNotExist())
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.claims").doesNotExist());

        assertGenericNotFound(request(userA, organizationB, activeFarmB));
        assertGenericNotFound(request(userA, organizationA, activeFarmB));
        assertGenericNotFound(request(userA, organizationA, UUID.randomUUID()));
    }

    @Test
    void alternativePathQueryBodyAndJwtClaimsCannotSelectAnotherFarm() throws Exception {
        String token = token(userA, Map.of(
                "farmId", activeFarmB.toString(),
                "organizationId", organizationB.toString()
        ));

        mvc.perform(get("/api/v1/farms/current")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("X-Organization-Id", organizationA)
                        .header("X-Farm-Id", activeFarmA)
                        .queryParam("farmId", activeFarmB.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"farmId\":\"" + activeFarmB + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.*", hasSize(4)))
                .andExpect(jsonPath("$.id").value(activeFarmA.toString()))
                .andExpect(jsonPath("$.organizationId").value(organizationA.toString()));

        mvc.perform(get("/api/v1/farms/current/{farmId}", activeFarmB)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("X-Organization-Id", organizationA)
                        .header("X-Farm-Id", activeFarmA))
                .andExpect(status().isNotFound());
    }

    @Test
    void authenticationHeadersAndBlockedStatesFailWithStableSafeResponses() throws Exception {
        mvc.perform(get("/api/v1/farms/current").header("X-Request-ID", "farm-without-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.requestId").value("farm-without-token"));

        mvc.perform(get("/api/v1/farms/current")
                        .header("X-Request-ID", "farm-header-missing")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(userA)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TENANT_CONTEXT_HEADER_MISSING"))
                .andExpect(jsonPath("$.requestId").value("farm-header-missing"));

        mvc.perform(get("/api/v1/farms/current")
                        .header("X-Request-ID", "farm-header-invalid")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(userA))
                        .header("X-Organization-Id", "inválido")
                        .header("X-Farm-Id", activeFarmA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TENANT_CONTEXT_HEADER_INVALID"))
                .andExpect(jsonPath("$.requestId").value("farm-header-invalid"));

        assertGenericNotFound(request(userA, organizationA, inactiveFarmA));
        assertGenericNotFound(request(userA, organizationA, archivedFarmA));

        executeAsAdmin(
                "update app.organization_memberships set status = 'REVOKED' where id = ?",
                membershipA
        );
        assertGenericNotFound(request(userA, organizationA, activeFarmA));

        executeAsAdmin("update app.users set status = 'SUSPENDED' where id = ?", userA);
        mvc.perform(request(userA, organizationA, activeFarmA))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("INTERNAL_USER_SUSPENDED"));
    }

    private void assertGenericNotFound(MockHttpServletRequestBuilder request) throws Exception {
        mvc.perform(request.header("X-Request-ID", "farm-not-found"))
                .andExpect(status().isNotFound())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
                .andExpect(jsonPath("$.code").value("FARM_PROFILE_NOT_AVAILABLE"))
                .andExpect(jsonPath("$.requestId").value("farm-not-found"))
                .andExpect(jsonPath("$.message").value("O perfil da fazenda solicitada não está disponível"))
                .andExpect(content().string(not(containsString("INACTIVE"))))
                .andExpect(content().string(not(containsString("ARCHIVED"))))
                .andExpect(content().string(not(containsString("Fazenda A"))))
                .andExpect(content().string(not(containsString("Fazenda B"))));
    }

    private static void executeAsAdmin(String sql, UUID value) throws Exception {
        try (Connection connection = PostgresTestEnvironment.adminConnection();
             var statement = connection.prepareStatement(sql)) {
            statement.setObject(1, value);
            statement.executeUpdate();
        }
    }

    private static MockHttpServletRequestBuilder request(UUID user, UUID organization, UUID farm)
            throws Exception {
        return get("/api/v1/farms/current")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(user))
                .header("X-Organization-Id", organization)
                .header("X-Farm-Id", farm);
    }

    private static String token(UUID user) throws Exception {
        return token(user, Map.of());
    }

    private static String token(UUID user, Map<String, Object> extraClaims) throws Exception {
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                .issuer("https://auth.example.test/auth/v1")
                .audience(List.of("authenticated"))
                .subject(user.toString())
                .claim("role", "authenticated")
                .issueTime(Date.from(Instant.now().minusSeconds(5)))
                .expirationTime(Date.from(Instant.now().plusSeconds(300)));
        extraClaims.forEach(claims::claim);
        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.HS256).type(JOSEObjectType.JWT).build(),
                claims.build()
        );
        jwt.sign(new MACSigner(SECRET.getBytes()));
        return jwt.serialize();
    }
}
