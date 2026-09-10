package com.gerenciadorrural.modules.herd.api;

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
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.sql.Connection;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

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
class HerdLifecycleVerticalIntegrationTest extends SpringPostgresTestSupport {

    private static final String ISSUER = "https://auth.example.test/auth/v1";
    private static final String SECRET = "test-only-hmac-key-with-at-least-32-bytes";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    UUID owner;
    UUID tenantA;
    UUID farmA;
    UUID farmA2;
    UUID tenantB;
    UUID farmB;
    String ownerToken;

    @BeforeEach
    void seed() throws Exception {
        PostgresTestEnvironment.clearUsers();
        owner = UUID.randomUUID();
        tenantA = UUID.randomUUID();
        farmA = UUID.randomUUID();
        farmA2 = UUID.randomUUID();
        tenantB = UUID.randomUUID();
        farmB = UUID.randomUUID();
        try (Connection connection = PostgresTestEnvironment.adminConnection(); var statement = connection.prepareStatement("insert into app.users(id,status) values(?,'ACTIVE');insert into app.organizations(id,name,status) values(?,'A','ACTIVE'),(?,'B','ACTIVE');insert into app.farms(id,tenant_id,name,status) values(?,?,'A1','ACTIVE'),(?,?,'A2','ACTIVE'),(?,?,'B1','ACTIVE')")) {
            statement.setObject(1, owner);
            statement.setObject(2, tenantA);
            statement.setObject(3, tenantB);
            statement.setObject(4, farmA);
            statement.setObject(5, tenantA);
            statement.setObject(6, farmA2);
            statement.setObject(7, tenantA);
            statement.setObject(8, farmB);
            statement.setObject(9, tenantB);
            statement.executeUpdate();
        }
        addMembership(owner, tenantA, farmA, "OWNER");
        addScope(owner, tenantA, farmA2);
        ownerToken = token(owner);
    }

    @Test
    void createsCorrectsSellsKillsAndServesStructuredPagedHistory() throws Exception {
        UUID corrected = create("CORR");
        UUID sold = create("SOLD");
        UUID deceased = create("DEAD");

        JsonNode createdHistory = history(corrected, ownerToken, tenantA, farmA, "?size=10");
        assertThat(createdHistory.path("page").asInt()).isZero();
        assertThat(createdHistory.path("size").asInt()).isEqualTo(10);
        assertThat(createdHistory.path("totalElements").asLong()).isOne();
        assertThat(createdHistory.path("items")).hasSize(1);
        assertEvent(createdHistory.path("items").get(0), "CREATED");

        assertThat(call(patch("/api/v1/herd/animals/" + corrected), ownerToken, tenantA, farmA, "{\"expectedVersion\":0,\"name\":\"Brisa corrigida\"}").getResponse().getStatus()).isEqualTo(200);
        JsonNode correctedHistory = history(corrected, ownerToken, tenantA, farmA, "?size=1&page=0");
        assertThat(correctedHistory.path("totalElements").asLong()).isEqualTo(2);
        assertThat(correctedHistory.path("items")).hasSize(1);
        assertEvent(correctedHistory.path("items").get(0), "CORRECTED");
        assertThat(correctedHistory.path("items").get(0).path("details").path("changes").path("name").path("before").asText()).isEqualTo("Brisa");
        assertThat(correctedHistory.path("items").get(0).path("details").path("changes").path("name").path("after").asText()).isEqualTo("Brisa corrigida");
        JsonNode correctionPageOne = history(corrected, ownerToken, tenantA, farmA, "?size=1&page=1");
        assertThat(correctionPageOne.path("items")).hasSize(1);
        assertEvent(correctionPageOne.path("items").get(0), "CREATED");

        assertThat(call(patch("/api/v1/herd/animals/" + corrected), ownerToken, tenantA, farmA, "{\"expectedVersion\":1,\"name\":\"Brisa corrigida\"}").getResponse().getStatus()).isEqualTo(200);
        assertThat(profile(corrected, ownerToken, tenantA, farmA).path("version").asLong()).isEqualTo(1);
        assertThat(history(corrected, ownerToken, tenantA, farmA, "?size=10").path("totalElements").asLong()).isEqualTo(2);

        UUID saleOperation = UUID.randomUUID();
        UUID deathOperation = UUID.randomUUID();
        assertThat(lifecycle(sold, "sale", ownerToken, tenantA, farmA, saleOperation, 0, "Venda").getResponse().getStatus()).isEqualTo(200);
        assertThat(lifecycle(deceased, "death", ownerToken, tenantA, farmA, deathOperation, 0, "Morte").getResponse().getStatus()).isEqualTo(200);
        JsonNode soldProfile = profile(sold, ownerToken, tenantA, farmA);
        JsonNode deceasedProfile = profile(deceased, ownerToken, tenantA, farmA);
        assertThat(soldProfile.path("status").asText()).isEqualTo("SOLD");
        assertThat(soldProfile.path("version").asLong()).isEqualTo(1);
        assertThat(deceasedProfile.path("status").asText()).isEqualTo("DECEASED");
        assertThat(deceasedProfile.path("version").asLong()).isEqualTo(1);

        JsonNode soldHistory = history(sold, ownerToken, tenantA, farmA, "?type=SOLD&size=10");
        assertThat(soldHistory.path("totalElements").asLong()).isOne();
        assertThat(soldHistory.path("items")).hasSize(1);
        assertEvent(soldHistory.path("items").get(0), "SOLD");
        assertThat(soldHistory.path("items").get(0).path("details").path("notes").asText()).isEqualTo("Venda");

        JsonNode deceasedHistory = history(deceased, ownerToken, tenantA, farmA, "?type=DECEASED&size=10");
        assertThat(deceasedHistory.path("totalElements").asLong()).isOne();
        assertThat(deceasedHistory.path("items")).hasSize(1);
        assertEvent(deceasedHistory.path("items").get(0), "DECEASED");
        assertThat(deceasedHistory.path("items").get(0).path("details").path("notes").asText()).isEqualTo("Morte");
    }

    @Test
    void enforcesRolesContextsLegacyStatesAndCompleteHistoryQueryValidation() throws Exception {
        UUID admin = member(tenantA, farmA, "ADMIN");
        UUID manager = member(tenantA, farmA, "MANAGER");
        UUID operator = member(tenantA, farmA, "OPERATOR");
        UUID viewer = member(tenantA, farmA, "VIEWER");
        assertThat(lifecycle(create("ADMIN-SALE"), "sale", token(admin), tenantA, farmA, UUID.randomUUID(), 0, "x").getResponse().getStatus()).isEqualTo(200);
        assertThat(lifecycle(create("MANAGER-SALE"), "sale", token(manager), tenantA, farmA, UUID.randomUUID(), 0, "x").getResponse().getStatus()).isEqualTo(200);
        assertThat(lifecycle(create("ADMIN-DEATH"), "death", token(admin), tenantA, farmA, UUID.randomUUID(), 0, "x").getResponse().getStatus()).isEqualTo(200);
        assertThat(lifecycle(create("MANAGER-DEATH"), "death", token(manager), tenantA, farmA, UUID.randomUUID(), 0, "x").getResponse().getStatus()).isEqualTo(200);
        assertForbidden(lifecycle(create("OP-SALE"), "sale", token(operator), tenantA, farmA, UUID.randomUUID(), 0, "x"));
        assertForbidden(lifecycle(create("VIEW-SALE"), "sale", token(viewer), tenantA, farmA, UUID.randomUUID(), 0, "x"));
        assertThat(lifecycle(create("OP-DEATH"), "death", token(operator), tenantA, farmA, UUID.randomUUID(), 0, "x").getResponse().getStatus()).isEqualTo(200);
        assertForbidden(lifecycle(create("VIEW-DEATH"), "death", token(viewer), tenantA, farmA, UUID.randomUUID(), 0, "x"));

        UUID readable = create("VIEW");
        assertThat(history(readable, token(viewer), tenantA, farmA, "?size=10").path("items")).hasSize(1);
        assertError(get("/api/v1/herd/animals/" + readable + "/history?page=-1"), ownerToken, tenantA, farmA, 400, "HERD_QUERY_INVALID");
        assertError(get("/api/v1/herd/animals/" + readable + "/history?size=0"), ownerToken, tenantA, farmA, 400, "HERD_QUERY_INVALID");
        assertError(get("/api/v1/herd/animals/" + readable + "/history?size=101"), ownerToken, tenantA, farmA, 400, "HERD_QUERY_INVALID");
        JsonNode maximumSize = history(readable, ownerToken, tenantA, farmA, "?size=100");
        assertThat(maximumSize.path("size").asInt()).isEqualTo(100);
        assertThat(maximumSize.path("items")).hasSize(1);
        JsonNode largePage = history(readable, ownerToken, tenantA, farmA, "?page=2147483647&size=100");
        assertThat(largePage.path("page").asInt()).isEqualTo(Integer.MAX_VALUE);
        assertThat(largePage.path("items")).isEmpty();
        assertError(get("/api/v1/herd/animals/" + UUID.randomUUID() + "/history"), ownerToken, tenantA, farmA, 404, "HERD_ANIMAL_NOT_FOUND");

        UUID first = create("IDA");
        UUID second = create("IDB");
        UUID operation = UUID.randomUUID();
        assertThat(lifecycle(first, "sale", ownerToken, tenantA, farmA, operation, 0, "mesma").getResponse().getStatus()).isEqualTo(200);
        MvcResult conflict = lifecycle(second, "sale", ownerToken, tenantA, farmA, operation, 0, "mesma");
        assertError(conflict, 409, "HERD_OPERATION_IDEMPOTENCY_CONFLICT");
        assertAnimal(second, "ACTIVE", 0, 0);
        assertError(get("/api/v1/herd/animals/" + first + "/history"), ownerToken, tenantA, farmA2, 404, "HERD_ANIMAL_NOT_FOUND");
        UUID foreignUser = member(tenantB, farmB, "OWNER");
        assertError(get("/api/v1/herd/animals/" + first + "/history"), token(foreignUser), tenantB, farmB, 404, "HERD_ANIMAL_NOT_FOUND");

        for (String status : List.of("TRANSFERRED", "ARCHIVED")) {
            UUID saleLegacy = legacy(status, 7);
            UUID deathLegacy = legacy(status, 7);
            assertError(lifecycle(saleLegacy, "sale", ownerToken, tenantA, farmA, UUID.randomUUID(), 7, "legado"), 409, "HERD_LIFECYCLE_CONFLICT");
            assertError(lifecycle(deathLegacy, "death", ownerToken, tenantA, farmA, UUID.randomUUID(), 7, "legado"), 409, "HERD_LIFECYCLE_CONFLICT");
            assertAnimal(saleLegacy, status, 7, 0);
            assertAnimal(deathLegacy, status, 7, 0);
        }
    }

    @Test
    void rejectsStrictLifecycleJsonAndPreservesSpecialCharactersAcrossReplayAndHistory() throws Exception {
        UUID strict = create("STRICT");
        for (String body : List.of(
                "{\"operationId\":\"" + UUID.randomUUID() + "\",\"expectedVersion\":0,\"occurredOn\":\"2026-09-08\",\"unknown\":1}",
                "{\"operationId\":\"" + UUID.randomUUID() + "\",\"operationId\":\"" + UUID.randomUUID() + "\",\"expectedVersion\":0,\"occurredOn\":\"2026-09-08\"}",
                "{\"operationId\":\"" + UUID.randomUUID() + "\",\"expectedVersion\":0.5,\"occurredOn\":\"2026-09-08\"}",
                "{\"operationId\":\"" + UUID.randomUUID() + "\",\"expectedVersion\":1.0,\"occurredOn\":\"2026-09-08\"}",
                "{\"operationId\":\"" + UUID.randomUUID() + "\",\"expectedVersion\":1e0,\"occurredOn\":\"2026-09-08\"}",
                "{\"operationId\":\"" + UUID.randomUUID() + "\",\"expectedVersion\":9223372036854775808,\"occurredOn\":\"2026-09-08\"}",
                "{\"operationId\":\"" + UUID.randomUUID() + "\",\"expectedVersion\":\"0\",\"occurredOn\":\"2026-09-08\"}",
                "{\"operationId\":\"" + UUID.randomUUID() + "\",\"expectedVersion\":null,\"occurredOn\":\"2026-09-08\"}",
                "{\"operationId\":\"" + UUID.randomUUID() + "\",\"expectedVersion\":[],\"occurredOn\":\"2026-09-08\"}",
                "{\"operationId\":\"" + UUID.randomUUID() + "\",\"expectedVersion\":{},\"occurredOn\":\"2026-09-08\"}",
                "{\"id\":\"x\",\"operationId\":\"" + UUID.randomUUID() + "\",\"expectedVersion\":0,\"occurredOn\":\"2026-09-08\"}",
                "{\"OperationId\":\"" + UUID.randomUUID() + "\",\"expectedVersion\":0,\"occurredOn\":\"2026-09-08\"}",
                "{\"tenantId\":\"x\",\"operationId\":\"" + UUID.randomUUID() + "\",\"expectedVersion\":0,\"occurredOn\":\"2026-09-08\"}",
                "{\"status\":\"SOLD\",\"operationId\":\"" + UUID.randomUUID() + "\",\"expectedVersion\":0,\"occurredOn\":\"2026-09-08\"}",
                "{\"actorUserId\":\"x\",\"operationId\":\"" + UUID.randomUUID() + "\",\"expectedVersion\":0,\"occurredOn\":\"2026-09-08\"}"
        )) {
            assertError(call(post("/api/v1/herd/animals/" + strict + "/sale"), ownerToken, tenantA, farmA, body), 400, "HERD_COMMAND_INVALID");
        }
        for (String notes : List.of("Animal \"Brisa\"", "Barra \\ interna", "Linha um\nLinha dois", "Tab\t interno", "Ação com acentuação")) {
            UUID id = create("SPECIAL");
            UUID operation = UUID.randomUUID();
            assertThat(lifecycle(id, "sale", ownerToken, tenantA, farmA, operation, 0, notes).getResponse().getStatus()).isEqualTo(200);
            JsonNode event = history(id, ownerToken, tenantA, farmA, "?type=SOLD").path("items").get(0);
            assertEvent(event, "SOLD");
            assertThat(event.path("details").path("notes").asText()).isEqualTo(notes);
            assertThat(lifecycle(id, "sale", ownerToken, tenantA, farmA, operation, 0, notes).getResponse().getStatus()).isEqualTo(200);
            assertAnimal(id, "SOLD", 1, 1);
        }
    }

    private UUID create(String prefix) throws Exception {
        UUID id = UUID.randomUUID();
        LinkedHashMap<String, Object> body = new LinkedHashMap<>();
        body.put("id", id);
        body.put("identification", prefix + "-" + id);
        body.put("name", prefix.equals("CORR") ? "Brisa" : null);
        body.put("sex", "FEMALE");
        MvcResult result = call(post("/api/v1/herd/animals"), ownerToken, tenantA, farmA, json.writeValueAsString(body));
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        return id;
    }

    private MvcResult lifecycle(UUID id, String action, String bearer, UUID tenant, UUID farm, UUID operation, long version, String notes) throws Exception {
        LinkedHashMap<String, Object> body = new LinkedHashMap<>();
        body.put("operationId", operation);
        body.put("expectedVersion", version);
        body.put("occurredOn", "2026-09-08");
        body.put("notes", notes);
        return call(post("/api/v1/herd/animals/" + id + "/" + action), bearer, tenant, farm, json.writeValueAsString(body));
    }

    private JsonNode history(UUID id, String bearer, UUID tenant, UUID farm, String query) throws Exception {
        MvcResult result = call(get("/api/v1/herd/animals/" + id + "/history" + query), bearer, tenant, farm, null);
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        return json.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode profile(UUID id, String bearer, UUID tenant, UUID farm) throws Exception {
        MvcResult result = call(get("/api/v1/herd/animals/" + id), bearer, tenant, farm, null);
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        return json.readTree(result.getResponse().getContentAsString());
    }

    private MvcResult call(MockHttpServletRequestBuilder request, String bearer, UUID tenant, UUID farm, String body) throws Exception {
        request.header(HttpHeaders.AUTHORIZATION, "Bearer " + bearer).header("X-Organization-Id", tenant).header("X-Farm-Id", farm);
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(body);
        return mvc.perform(request).andReturn();
    }

    private void assertError(MockHttpServletRequestBuilder request, String bearer, UUID tenant, UUID farm, int status, String code) throws Exception {
        assertError(call(request, bearer, tenant, farm, null), status, code);
    }

    private void assertError(MvcResult result, int status, String code) throws Exception {
        String body = result.getResponse().getContentAsString();
        assertThat(result.getResponse().getStatus()).isEqualTo(status);
        assertThat(json.readTree(body).path("code").asText()).isEqualTo(code);
        assertThat(body).doesNotContain("SQL", "SQLState", "constraint", "currentVersion", "tenantId", "farmId", "stack", "Exception", "Jdbc");
    }

    private void assertForbidden(MvcResult result) throws Exception {
        assertError(result, 403, "HERD_LIFECYCLE_FORBIDDEN");
    }

    private void assertEvent(JsonNode event, String type) {
        assertThat(event.path("type").asText()).isEqualTo(type);
        assertThat(event.path("details").isObject()).isTrue();
        assertThat(event.has("tenantId")).isFalse();
        assertThat(event.has("farmId")).isFalse();
    }

    private UUID member(UUID tenant, UUID farm, String role) throws Exception {
        UUID user = UUID.randomUUID();
        addMembership(user, tenant, farm, role);
        return user;
    }

    private void addMembership(UUID user, UUID tenant, UUID farm, String role) throws Exception {
        UUID membership = UUID.randomUUID();
        try (Connection connection = PostgresTestEnvironment.adminConnection(); var statement = connection.prepareStatement("insert into app.users(id,status) values(?,'ACTIVE') on conflict(id) do nothing;insert into app.organization_memberships(id,tenant_id,user_id,role_key,status,farm_scope_mode) values(?,?,?,?,'ACTIVE','SELECTED_FARMS');insert into app.membership_farm_scopes(tenant_id,membership_id,farm_id) values(?,?,?)")) {
            statement.setObject(1, user);
            statement.setObject(2, membership);
            statement.setObject(3, tenant);
            statement.setObject(4, user);
            statement.setString(5, role);
            statement.setObject(6, tenant);
            statement.setObject(7, membership);
            statement.setObject(8, farm);
            statement.executeUpdate();
        }
    }

    private void addScope(UUID user, UUID tenant, UUID farm) throws Exception {
        try (Connection connection = PostgresTestEnvironment.adminConnection(); var statement = connection.prepareStatement("select id from app.organization_memberships where tenant_id=? and user_id=?")) {
            statement.setObject(1, tenant);
            statement.setObject(2, user);
            ResultSet result = statement.executeQuery();
            result.next();
            UUID membership = result.getObject(1, UUID.class);
            try (var insert = connection.prepareStatement("insert into app.membership_farm_scopes(tenant_id,membership_id,farm_id) values(?,?,?)")) {
                insert.setObject(1, tenant);
                insert.setObject(2, membership);
                insert.setObject(3, farm);
                insert.executeUpdate();
            }
        }
    }

    private UUID legacy(String status, long version) throws Exception {
        UUID id = UUID.randomUUID();
        try (Connection connection = PostgresTestEnvironment.adminConnection(); var statement = connection.prepareStatement("insert into app.animals(id,tenant_id,farm_id,identification,sex,status,version) values(?,?,?,?,?,?,?)")) {
            statement.setObject(1, id);
            statement.setObject(2, tenantA);
            statement.setObject(3, farmA);
            statement.setString(4, "LEG-" + id);
            statement.setString(5, "FEMALE");
            statement.setString(6, status);
            statement.setLong(7, version);
            statement.executeUpdate();
        }
        return id;
    }

    private void assertAnimal(UUID id, String status, long version, long terminalEvents) throws Exception {
        try (Connection connection = PostgresTestEnvironment.adminConnection(); var statement = connection.prepareStatement("select status,version,(select count(*) from app.animal_events where animal_id=animals.id and event_type in ('SOLD','DECEASED')) from app.animals where id=?")) {
            statement.setObject(1, id);
            ResultSet result = statement.executeQuery();
            assertThat(result.next()).isTrue();
            assertThat(result.getString(1)).isEqualTo(status);
            assertThat(result.getLong(2)).isEqualTo(version);
            assertThat(result.getLong(3)).isEqualTo(terminalEvents);
        }
    }

    private String token(UUID user) throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder().issuer(ISSUER).audience("authenticated").subject(user.toString()).claim("role", "authenticated").issueTime(new Date()).expirationTime(Date.from(Instant.now().plusSeconds(300))).build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        jwt.sign(new MACSigner(SECRET.getBytes()));
        return jwt.serialize();
    }
}
