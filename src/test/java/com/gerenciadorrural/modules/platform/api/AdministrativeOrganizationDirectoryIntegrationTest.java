package com.gerenciadorrural.modules.platform.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ActiveProfiles("test")
@SpringBootTest(properties={"app.security.supabase.mode=HMAC","app.security.supabase.algorithm=HS256","app.security.supabase.issuer=https://auth.example.test/auth/v1","app.security.supabase.hmac-secret=test-only-hmac-key-with-at-least-32-bytes","app.security.supabase.audiences=authenticated","app.security.supabase.accepted-token-roles=authenticated"})
@AutoConfigureMockMvc
class AdministrativeOrganizationDirectoryIntegrationTest extends SpringPostgresTestSupport {
    private static final String PATH="/api/v1/me/administrative-organizations";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    private UUID user;

    @BeforeEach void seedUser() throws Exception {
        PostgresTestEnvironment.clearUsers();
        user=UUID.randomUUID();
        try(Connection c=PostgresTestEnvironment.adminConnection();var p=c.prepareStatement("insert into app.users(id,status) values(?,'ACTIVE')")) {p.setObject(1,user);p.executeUpdate();}
    }
    @Test void directoryIncludesOwnInactiveOrganizationsButOperationalBootstrapRemainsActiveOnly() throws Exception {
        UUID active=organization(user,"A","ACTIVE","OWNER","ACTIVE");
        UUID suspended=organization(user,"B","SUSPENDED","OWNER","ACTIVE");
        UUID archived=organization(user,"C","ARCHIVED","VIEWER","ACTIVE");
        organization(user,"Revogada","ACTIVE","OWNER","REVOKED");
        UUID other=UUID.randomUUID();
        try(Connection c=PostgresTestEnvironment.adminConnection();var p=c.prepareStatement("insert into app.users(id,status) values(?,'ACTIVE')")){p.setObject(1,other);p.executeUpdate();}
        UUID privateOrg=organization(other,"Privada","ACTIVE","OWNER","ACTIVE");
        var response=mvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION,bearer(user))).andExpect(status().isOk()).andExpect(header().string(HttpHeaders.CACHE_CONTROL,"no-store")).andReturn().getResponse();
        JsonNode rows=json.readTree(response.getContentAsString());
        assertThat(rows.size()).isEqualTo(3);
        assertThat(rows.get(0).path("id").asText()).isEqualTo(active.toString());
        assertThat(rows.get(1).path("id").asText()).isEqualTo(suspended.toString());
        assertThat(rows.get(2).path("id").asText()).isEqualTo(archived.toString());
        assertThat(rows.get(2).path("role").asText()).isEqualTo("VIEWER");
        assertThat(rows.get(2).path("farmScopeMode").asText()).isEqualTo("ALL_FARMS");
        assertThat(response.getContentAsString()).doesNotContain(privateOrg.toString(),"Privada","Revogada","userId","membershipId");
        mvc.perform(get("/api/v1/me/organizations").header(HttpHeaders.AUTHORIZATION,bearer(user))).andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1)).andExpect(jsonPath("$.items[0].organizationId").value(active.toString()));
    }
    @Test void directoryNeverGrantsOwnerWritePermissionToViewer() throws Exception {
        UUID org=organization(user,"Arquivada","ARCHIVED","VIEWER","ACTIVE");
        mvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION,bearer(user))).andExpect(status().isOk()).andExpect(jsonPath("$[0].role").value("VIEWER"));
        mvc.perform(patch("/api/v1/organizations/"+org).header(HttpHeaders.AUTHORIZATION,bearer(user)).contentType("application/json").content("{\"status\":\"ACTIVE\",\"expectedVersion\":0}")).andExpect(status().isForbidden());
    }
    @Test void ownerCanDiscoverAndReactivateAnOrganizationWithoutOperationalTenantHeaders() throws Exception {
        UUID org=organization(user,"Suspensa","SUSPENDED","OWNER","ACTIVE");
        mvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION,bearer(user))).andExpect(status().isOk()).andExpect(jsonPath("$[0].version").value(0));
        mvc.perform(patch("/api/v1/organizations/"+org).header(HttpHeaders.AUTHORIZATION,bearer(user)).contentType("application/json").content("{\"status\":\"ACTIVE\",\"expectedVersion\":0}")).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
        mvc.perform(get("/api/v1/me/organizations").header(HttpHeaders.AUTHORIZATION,bearer(user))).andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1));
    }
    @Test void authenticationIsRequiredAndAnUnassociatedUserGetsAnEmptyDirectory() throws Exception {
        mvc.perform(get(PATH)).andExpect(status().isUnauthorized());
        mvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION,bearer(user))).andExpect(status().isOk()).andExpect(content().json("[]"));
    }
    private UUID organization(UUID actor,String name,String status,String role,String memberStatus) throws Exception {
        UUID org=UUID.randomUUID();
        try(Connection c=PostgresTestEnvironment.adminConnection();var o=c.prepareStatement("insert into app.organizations(id,name,status) values(?,?,?)");var m=c.prepareStatement("insert into app.organization_memberships(id,tenant_id,user_id,role_key,status,farm_scope_mode) values(?,?,?,?,?,'ALL_FARMS')")){
            o.setObject(1,org);o.setString(2,name);o.setString(3,status);o.executeUpdate();
            m.setObject(1,UUID.randomUUID());m.setObject(2,org);m.setObject(3,actor);m.setString(4,role);m.setString(5,memberStatus);m.executeUpdate();
        }return org;
    }
    private String bearer(UUID actor) throws Exception {
        var claims=new JWTClaimsSet.Builder().issuer("https://auth.example.test/auth/v1").audience("authenticated").subject(actor.toString()).claim("role","authenticated").issueTime(new Date()).expirationTime(Date.from(Instant.now().plusSeconds(300))).build();
        var jwt=new SignedJWT(new JWSHeader(JWSAlgorithm.HS256),claims);jwt.sign(new MACSigner("test-only-hmac-key-with-at-least-32-bytes"));return "Bearer "+jwt.serialize();
    }
}
