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
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Date;
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
class HerdPlannerVerticalIntegrationTest extends SpringPostgresTestSupport {

    private static final String ISSUER = "https://auth.example.test/auth/v1";
    private static final String SECRET = "test-only-hmac-key-with-at-least-32-bytes";

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    UUID tenant;
    UUID farmA;
    UUID farmB;
    UUID user;
    UUID membership;
    String bearer;

    @BeforeEach
    void seed() throws Exception {
        PostgresTestEnvironment.clearUsers();
        tenant = UUID.randomUUID();
        farmA = UUID.randomUUID();
        farmB = UUID.randomUUID();
        user = UUID.randomUUID();
        membership = UUID.randomUUID();
        try (Connection connection = PostgresTestEnvironment.adminConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     insert into app.users(id,status) values(?,'ACTIVE');
                     insert into app.organizations(id,name,status) values(?,'Organização','ACTIVE');
                     insert into app.farms(id,tenant_id,name,status) values
                       (?,?,'Fazenda A','ACTIVE'),(?,?,'Fazenda B','ACTIVE');
                     insert into app.organization_memberships
                       (id,tenant_id,user_id,role_key,status,farm_scope_mode)
                       values(?,?,?,'OWNER','ACTIVE','SELECTED_FARMS');
                     insert into app.membership_farm_scopes(tenant_id,membership_id,farm_id)
                       values(?,?,?),(?,?,?)
                     """)) {
            int index = 1;
            statement.setObject(index++, user);
            statement.setObject(index++, tenant);
            statement.setObject(index++, farmA);
            statement.setObject(index++, tenant);
            statement.setObject(index++, farmB);
            statement.setObject(index++, tenant);
            statement.setObject(index++, membership);
            statement.setObject(index++, tenant);
            statement.setObject(index++, user);
            statement.setObject(index++, tenant);
            statement.setObject(index++, membership);
            statement.setObject(index++, farmA);
            statement.setObject(index++, tenant);
            statement.setObject(index++, membership);
            statement.setObject(index, farmB);
            statement.executeUpdate();
        }
        bearer = token(user);
    }

    @Test
    void lifecycleHistoricalReplayCanonicalizationLockingAndAuthorizationWorkOverHttp() throws Exception {
        UUID createOperation = UUID.randomUUID();
        String create = plannerBody(createOperation, null, "GENERAL", "  Inspecionar curral  ",
                "  Observação operacional  ", LocalDate.of(2020, 1, 2), null);
        MvcResult creation = request(farmA, post("/api/v1/herd/planner-items"), create);
        assertStatus(creation, 201);
        JsonNode created = body(creation);
        UUID item = UUID.fromString(created.path("id").asText());
        assertThat(created.path("title").asText()).isEqualTo("Inspecionar curral");
        assertThat(created.path("notes").asText()).isEqualTo("Observação operacional");
        assertThat(created.path("version").asLong()).isZero();

        MvcResult replay = request(farmA, post("/api/v1/herd/planner-items"), plannerBody(
                createOperation, null, "GENERAL", "Inspecionar curral", "Observação operacional",
                LocalDate.of(2020, 1, 2), null));
        assertStatus(replay, 200);
        assertThat(body(replay).path("replay").asBoolean()).isTrue();
        assertError(request(farmA, post("/api/v1/herd/planner-items"), plannerBody(
                createOperation, null, "GENERAL", "Outro título", "Observação operacional",
                LocalDate.of(2020, 1, 2), null)), 409, "HERD_PLANNER_ITEM_CONFLICT");

        UUID correctionOperation = UUID.randomUUID();
        String correction = plannerBody(correctionOperation, 0L, "MOVEMENT", "Mover lote", null,
                LocalDate.of(2020, 2, 1), null);
        assertStatus(request(farmA, patch("/api/v1/herd/planner-items/{id}", item), correction), 200);
        assertError(request(farmA, patch("/api/v1/herd/planner-items/{id}", item), plannerBody(
                UUID.randomUUID(), 0L, "CALVING", "Versão antiga", null,
                LocalDate.of(2020, 2, 2), null)), 409, "HERD_PLANNER_ITEM_CONFLICT");

        UUID completionOperation = UUID.randomUUID();
        String completion = transitionBody(completionOperation, 1);
        assertStatus(request(farmA, post("/api/v1/herd/planner-items/{id}/completion", item), completion), 200);
        assertThat(body(request(farmA, post("/api/v1/herd/planner-items/{id}/completion", item), completion))
                .path("replay").asBoolean()).isTrue();
        assertThat(body(request(farmA, patch("/api/v1/herd/planner-items/{id}", item), correction))
                .path("replay").asBoolean()).isTrue();
        assertError(request(farmA, post("/api/v1/herd/planner-items/{id}/cancellation", item),
                transitionBody(UUID.randomUUID(), 2)), 409, "HERD_PLANNER_ITEM_CONFLICT");

        UUID cancellable = createPlanner(farmA, "Cancelar", "GENERAL", null);
        assertStatus(request(farmA, post("/api/v1/herd/planner-items/{id}/cancellation", cancellable),
                transitionBody(UUID.randomUUID(), 0)), 200);
        assertThat(body(request(farmA, get("/api/v1/herd/planner-items?status=CANCELLED&type=GENERAL"), null))
                .path("totalElements").asInt()).isOne();

        long factsBeforeReports = reportFactCount();
        for (String role : List.of("OWNER", "ADMIN", "MANAGER", "OPERATOR", "VIEWER")) {
            setRole(role);
            assertStatus(request(farmA, get("/api/v1/herd/planner-items/{id}", item), null), 200);
            assertStatus(request(farmA, get("/api/v1/herd/planner-items"), null), 200);
            assertStatus(request(farmA, get("/api/v1/herd/agenda"), null), 200);
            for (String report : List.of(
                    "herd-position", "lifecycle", "movements", "transfers", "weights",
                    "health", "reproduction", "planner")) {
                assertStatus(request(farmA, get("/api/v1/herd/reports/" + report), null), 200);
            }
        }
        assertThat(reportFactCount()).isEqualTo(factsBeforeReports);
        setRole("VIEWER");
        assertError(request(farmA, post("/api/v1/herd/planner-items"), plannerBody(
                UUID.randomUUID(), null, "GENERAL", "Sem permissão", null, LocalDate.now(), null)),
                403, "HERD_PLANNER_FORBIDDEN");
        for (String role : List.of("OWNER", "ADMIN", "MANAGER", "OPERATOR")) {
            setRole(role);
            assertStatus(request(farmA, post("/api/v1/herd/planner-items"), plannerBody(
                    UUID.randomUUID(), null, "GENERAL", "Permitido para " + role, null,
                    LocalDate.now(), null)), 201);
        }
    }

    @Test
    void strictJsonAndEveryQueryValidationFailureAreSanitized() throws Exception {
        UUID operation = UUID.randomUUID();
        List<String> invalidCreates = List.of(
                "{",
                plannerBody(operation, 0L, "GENERAL", "Versão do cliente", null, LocalDate.now(), null),
                "{\"operationId\":\"bad\",\"type\":\"GENERAL\",\"title\":\"A\",\"scheduledFor\":\"2026-01-01\"}",
                "{\"operationId\":\"" + UUID.randomUUID() + "\",\"type\":\"general\",\"title\":\"A\",\"scheduledFor\":\"2026-01-01\"}",
                "{\"operationId\":\"" + UUID.randomUUID() + "\",\"type\":\"GENERAL\",\"title\":\"A\",\"scheduledFor\":\"2026-01-01\",\"tenantId\":\"" + tenant + "\"}",
                "{\"operationId\":\"" + UUID.randomUUID() + "\",\"type\":\"GENERAL\",\"title\":\"A\",\"title\":\"B\",\"scheduledFor\":\"2026-01-01\"}",
                "{\"operationId\":\"" + UUID.randomUUID() + "\",\"type\":\"GENERAL\",\"title\":\"   \",\"scheduledFor\":\"2026-01-01\"}"
        );
        for (String invalid : invalidCreates) {
            assertError(request(farmA, post("/api/v1/herd/planner-items"), invalid),
                    400, "HERD_COMMAND_INVALID");
        }
        for (String serverOwned : List.of(
                "tenantId", "farmId", "createdBy", "createdAt", "updatedAt",
                "completedAt", "cancelledAt", "version", "result", "Type")) {
            String invalid = "{\"operationId\":\"" + UUID.randomUUID()
                    + "\",\"type\":\"GENERAL\",\"title\":\"A\","
                    + "\"scheduledFor\":\"2026-01-01\",\"" + serverOwned + "\":null}";
            assertError(request(farmA, post("/api/v1/herd/planner-items"), invalid),
                    400, "HERD_COMMAND_INVALID");
        }
        assertThat(count("app.herd_planner_items")).isZero();

        UUID item = createPlanner(farmA, "Válido", "GENERAL", null);
        for (String invalid : List.of(
                "{\"operationId\":\"" + UUID.randomUUID() + "\",\"expectedVersion\":0,\"extra\":true}",
                "{\"operationId\":\"" + UUID.randomUUID() + "\",\"expectedVersion\":null}",
                "{\"operationId\":\"" + UUID.randomUUID() + "\",\"expectedVersion\":-1}"
        )) {
            assertError(request(farmA, post("/api/v1/herd/planner-items/{id}/completion", item), invalid),
                    400, "HERD_COMMAND_INVALID");
        }
        String duplicateTransition = "{\"operationId\":\"" + UUID.randomUUID()
                + "\",\"operationId\":\"" + UUID.randomUUID() + "\",\"expectedVersion\":0}";
        assertError(request(farmA, post("/api/v1/herd/planner-items/{id}/cancellation", item),
                duplicateTransition), 400, "HERD_COMMAND_INVALID");
        assertError(request(farmA, patch("/api/v1/herd/planner-items/{id}", item),
                "{\"operationId\":\"" + UUID.randomUUID()
                        + "\",\"expectedVersion\":0,\"type\":\"GENERAL\","
                        + "\"title\":\"A\",\"scheduledFor\":\"2026-01-01\",\"farmId\":null}"),
                400, "HERD_COMMAND_INVALID");

        for (String path : List.of(
                "/api/v1/herd/planner-items?page=-1",
                "/api/v1/herd/planner-items?size=0",
                "/api/v1/herd/planner-items?size=-1",
                "/api/v1/herd/planner-items?size=101",
                "/api/v1/herd/planner-items?status=open",
                "/api/v1/herd/planner-items?type=general",
                "/api/v1/herd/planner-items?animalId=bad",
                "/api/v1/herd/planner-items?from=bad",
                "/api/v1/herd/planner-items?from=2026-02-01&to=2026-01-01",
                "/api/v1/herd/planner-items?unknown=true",
                "/api/v1/herd/agenda?page=-1",
                "/api/v1/herd/agenda?size=0",
                "/api/v1/herd/agenda?size=-1",
                "/api/v1/herd/agenda?size=101",
                "/api/v1/herd/agenda?source=manual",
                "/api/v1/herd/agenda?type=OTHER",
                "/api/v1/herd/agenda?animalId=bad",
                "/api/v1/herd/agenda?to=bad",
                "/api/v1/herd/agenda?from=2026-02-01&to=2026-01-01",
                "/api/v1/herd/agenda?unknown=true",
                "/api/v1/herd/reports/herd-position?page=-1",
                "/api/v1/herd/reports/herd-position?size=101",
                "/api/v1/herd/reports/herd-position?sex=female",
                "/api/v1/herd/reports/lifecycle?event=born",
                "/api/v1/herd/reports/lifecycle?from=bad",
                "/api/v1/herd/reports/lifecycle?from=2026-02-01&to=2026-01-01",
                "/api/v1/herd/reports/movements?sourcePaddockId=bad",
                "/api/v1/herd/reports/transfers?direction=in",
                "/api/v1/herd/reports/weights?animalId=bad",
                "/api/v1/herd/reports/health?treatmentType=vaccination",
                "/api/v1/herd/reports/reproduction?pregnancyStatus=possible",
                "/api/v1/herd/reports/planner?status=open",
                "/api/v1/herd/reports/planner?unknown=true"
        )) {
            assertError(request(farmA, get(path), null), 400, "HERD_QUERY_INVALID");
        }
    }

    @Test
    void nonEnumerationAndTransferKeepManualOriginWhileDerivedWorkFollowsCustody() throws Exception {
        UUID animal = UUID.randomUUID();
        assertStatus(request(farmA, post("/api/v1/herd/animals"), """
                {"id":"%s","identification":"BR-10","name":"Estrela","sex":"FEMALE"}
                """.formatted(animal)), 201);
        seedCurrentVaccinationAndWeight(animal);
        UUID originPlanner = createPlanner(farmA, "Vacinação planejada", "VACCINATION", animal);

        JsonNode before = body(request(farmA, get("/api/v1/herd/agenda?type=VACCINATION&animalId=" + animal), null));
        assertThat(before.path("totalElements").asInt()).isEqualTo(2);
        assertThat(before.path("items").get(0).path("source").asText()).isEqualTo("DERIVED");
        assertThat(before.path("items").get(1).path("source").asText()).isEqualTo("MANUAL");

        assertError(request(farmB, get("/api/v1/herd/planner-items/{id}", originPlanner), null),
                404, "HERD_PLANNER_ITEM_NOT_FOUND");
        assertError(request(farmB, patch("/api/v1/herd/planner-items/{id}", originPlanner),
                plannerBody(UUID.randomUUID(), 0L, "VACCINATION", "Externo", null,
                        LocalDate.now(), animal)), 404, "HERD_PLANNER_ITEM_NOT_FOUND");
        assertError(request(farmB, post("/api/v1/herd/planner-items/{id}/completion", originPlanner),
                transitionBody(UUID.randomUUID(), 0)), 404, "HERD_PLANNER_ITEM_NOT_FOUND");
        assertError(request(farmB, post("/api/v1/herd/planner-items/{id}/cancellation", originPlanner),
                transitionBody(UUID.randomUUID(), 0)), 404, "HERD_PLANNER_ITEM_NOT_FOUND");

        assertStatus(request(farmA, post("/api/v1/herd/animals/{id}/transfers", animal), """
                {"operationId":"%s","expectedVersion":0,"destinationFarmId":"%s","occurredOn":"%s"}
                """.formatted(UUID.randomUUID(), farmB, LocalDate.now())), 200);

        JsonNode origin = body(request(farmA, get("/api/v1/herd/agenda?animalId=" + animal), null));
        assertThat(origin.path("totalElements").asInt()).isOne();
        assertThat(origin.path("items").get(0).path("source").asText()).isEqualTo("MANUAL");
        assertThat(origin.toString()).doesNotContain(farmB.toString(), "Fazenda B", "Estrela", "BR-10");
        JsonNode destination = body(request(farmB,
                get("/api/v1/herd/agenda?type=VACCINATION&animalId=" + animal), null));
        assertThat(destination.path("totalElements").asInt()).isOne();
        assertThat(destination.path("items").get(0).path("source").asText()).isEqualTo("DERIVED");

        UUID destinationPlanner = createPlanner(farmB, "Plano da nova fazenda", "VACCINATION", animal);
        assertThat(destinationPlanner).isNotEqualTo(originPlanner);
        assertThat(body(request(farmB, get("/api/v1/herd/agenda?type=VACCINATION&animalId=" + animal), null))
                .path("totalElements").asInt()).isEqualTo(2);
        assertThat(count("app.herd_planner_items")).isEqualTo(2);
    }

    private UUID createPlanner(UUID farm, String title, String type, UUID animal) throws Exception {
        MvcResult result = request(farm, post("/api/v1/herd/planner-items"), plannerBody(
                UUID.randomUUID(), null, type, title, null, LocalDate.now(), animal));
        assertStatus(result, 201);
        return UUID.fromString(body(result).path("id").asText());
    }

    private void seedCurrentVaccinationAndWeight(UUID animal) throws Exception {
        try (Connection connection = PostgresTestEnvironment.adminConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     insert into app.animal_health_treatments
                       (id,tenant_id,farm_id,animal_id,operation_id,treatment_type,occurred_on,next_due_on,actor_user_id)
                       values(?,?,?,?,?,'VACCINATION',?,?,?);
                     insert into app.animal_weight_measurements
                       (id,tenant_id,farm_id,animal_id,operation_id,measured_on,weight_kg,actor_user_id)
                       values(?,?,?,?,?,?,?,?)
                     """)) {
            int index = 1;
            statement.setObject(index++, UUID.randomUUID());
            statement.setObject(index++, tenant);
            statement.setObject(index++, farmA);
            statement.setObject(index++, animal);
            statement.setObject(index++, UUID.randomUUID());
            statement.setObject(index++, LocalDate.now().minusDays(30));
            statement.setObject(index++, LocalDate.now().minusDays(1));
            statement.setObject(index++, user);
            statement.setObject(index++, UUID.randomUUID());
            statement.setObject(index++, tenant);
            statement.setObject(index++, farmA);
            statement.setObject(index++, animal);
            statement.setObject(index++, UUID.randomUUID());
            statement.setObject(index++, LocalDate.now());
            statement.setBigDecimal(index++, new java.math.BigDecimal("450"));
            statement.setObject(index, user);
            statement.executeUpdate();
        }
    }

    private void setRole(String role) throws Exception {
        try (Connection connection = PostgresTestEnvironment.adminConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "update app.organization_memberships set role_key=? where id=?")) {
            statement.setString(1, role);
            statement.setObject(2, membership);
            statement.executeUpdate();
        }
    }

    private MvcResult request(UUID farm, MockHttpServletRequestBuilder request, String content) throws Exception {
        request.header(HttpHeaders.AUTHORIZATION, "Bearer " + bearer)
                .header("X-Organization-Id", tenant)
                .header("X-Farm-Id", farm);
        if (content != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(content);
        }
        return mvc.perform(request).andReturn();
    }

    private JsonNode body(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString());
    }

    private void assertStatus(MvcResult result, int expected) {
        assertThat(result.getResponse().getStatus()).isEqualTo(expected);
        assertThat(result.getResponse().getHeader(HttpHeaders.CACHE_CONTROL)).contains("no-store");
    }

    private void assertError(MvcResult result, int status, String code) throws Exception {
        assertStatus(result, status);
        JsonNode error = body(result);
        assertThat(error.path("code").asText()).isEqualTo(code);
        assertThat(error.toString()).doesNotContain(
                "org.springframework", "org.postgresql", "command_payload", "tenant_id", "farm_id");
    }

    private long count(String table) throws Exception {
        try (Connection connection = PostgresTestEnvironment.adminConnection();
             PreparedStatement statement = connection.prepareStatement("select count(*) from " + table);
             ResultSet resultSet = statement.executeQuery()) {
            resultSet.next();
            return resultSet.getLong(1);
        }
    }

    private long reportFactCount() throws Exception {
        return count("app.animals")
                + count("app.animal_events")
                + count("app.herd_movements")
                + count("app.animal_transfers")
                + count("app.animal_weight_measurements")
                + count("app.animal_health_treatments")
                + count("app.animal_pregnancies")
                + count("app.herd_planner_items");
    }

    private static String plannerBody(
            UUID operation,
            Long version,
            String type,
            String title,
            String notes,
            LocalDate date,
            UUID animal
    ) {
        return "{" +
                "\"operationId\":\"" + operation + "\"," +
                (version == null ? "" : "\"expectedVersion\":" + version + ",") +
                "\"type\":\"" + type + "\"," +
                "\"title\":\"" + title + "\"," +
                (notes == null ? "" : "\"notes\":\"" + notes + "\",") +
                "\"scheduledFor\":\"" + date + "\"" +
                (animal == null ? "" : ",\"animalId\":\"" + animal + "\"") +
                "}";
    }

    private static String transitionBody(UUID operation, long version) {
        return "{\"operationId\":\"" + operation + "\",\"expectedVersion\":" + version + "}";
    }

    private static String token(UUID subject) throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .audience("authenticated")
                .subject(subject.toString())
                .claim("role", "authenticated")
                .issueTime(new Date())
                .expirationTime(Date.from(Instant.now().plusSeconds(300)))
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        jwt.sign(new MACSigner(SECRET.getBytes()));
        return jwt.serialize();
    }
}
