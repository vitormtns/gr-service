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
import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
class HerdReproductionSecurityTransferIntegrationTest extends SpringPostgresTestSupport {

    private static final String ISSUER = "https://auth.example.test/auth/v1";
    private static final String SECRET = "test-only-hmac-key-with-at-least-32-bytes";
    private static final LocalDate SERVICE_ON = LocalDate.of(2026, 9, 1);
    private static final LocalDate OCCURRED_ON = LocalDate.of(2026, 9, 2);

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    UUID tenant;
    UUID farmA;
    UUID farmB;
    UUID farmC;
    UUID caller;
    UUID callerMembership;
    UUID administrator;
    String callerToken;
    String administratorToken;

    @BeforeEach
    void seed() throws Exception {
        PostgresTestEnvironment.clearUsers();
        tenant = UUID.randomUUID();
        farmA = UUID.randomUUID();
        farmB = UUID.randomUUID();
        farmC = UUID.randomUUID();
        caller = UUID.randomUUID();
        callerMembership = UUID.randomUUID();
        administrator = UUID.randomUUID();
        UUID administratorMembership = UUID.randomUUID();
        try (Connection connection = PostgresTestEnvironment.adminConnection();
             var statement = connection.prepareStatement("""
                     insert into app.users(id,status) values(?,'ACTIVE'),(?,'ACTIVE');
                     insert into app.organizations(id,name,status) values(?,'Organização','ACTIVE');
                     insert into app.farms(id,tenant_id,name,status) values
                       (?,?,'Fazenda A','ACTIVE'),(?,?,'Fazenda B','ACTIVE'),(?,?,'Fazenda C','ACTIVE');
                     insert into app.organization_memberships
                       (id,tenant_id,user_id,role_key,status,farm_scope_mode) values
                       (?,?,?,'OWNER','ACTIVE','SELECTED_FARMS'),
                       (?,?,?,'OWNER','ACTIVE','SELECTED_FARMS');
                     insert into app.membership_farm_scopes(tenant_id,membership_id,farm_id) values
                       (?,?,?),(?,?,?),(?,?,?),(?,?,?)
                     """)) {
            int index = 1;
            statement.setObject(index++, caller);
            statement.setObject(index++, administrator);
            statement.setObject(index++, tenant);
            statement.setObject(index++, farmA);
            statement.setObject(index++, tenant);
            statement.setObject(index++, farmB);
            statement.setObject(index++, tenant);
            statement.setObject(index++, farmC);
            statement.setObject(index++, tenant);
            statement.setObject(index++, callerMembership);
            statement.setObject(index++, tenant);
            statement.setObject(index++, caller);
            statement.setObject(index++, administratorMembership);
            statement.setObject(index++, tenant);
            statement.setObject(index++, administrator);
            statement.setObject(index++, tenant);
            statement.setObject(index++, callerMembership);
            statement.setObject(index++, farmA);
            for (UUID farm : List.of(farmA, farmB, farmC)) {
                statement.setObject(index++, tenant);
                statement.setObject(index++, administratorMembership);
                statement.setObject(index++, farm);
            }
            statement.executeUpdate();
        }
        callerToken = token(caller);
        administratorToken = token(administrator);
    }

    @Test
    void realExternalPregnancyAndMutationsRemainNonEnumerable() throws Exception {
        UUID mother = createMother(administratorToken, farmB);
        UUID pregnancy = breed(administratorToken, farmB, mother, UUID.randomUUID(), 0);
        UUID externalConfirmation = UUID.randomUUID();
        UUID externalTermination = UUID.randomUUID();
        UUID externalBreeding = UUID.randomUUID();
        UUID externalCalving = UUID.randomUUID();
        UUID calf = UUID.randomUUID();

        assertNotFound(request(callerToken, farmA, get("/api/v1/herd/pregnancies/{id}", pregnancy), null),
                mother, pregnancy);
        assertNotFound(request(callerToken, farmA,
                get("/api/v1/herd/animals/{id}/pregnancies", mother), null), mother, pregnancy);
        assertNotFound(request(callerToken, farmA,
                post("/api/v1/herd/animals/{id}/breedings", mother), breedingBody(externalBreeding, 1)),
                mother, pregnancy);
        assertNotFound(request(callerToken, farmA,
                post("/api/v1/herd/pregnancies/{id}/confirmation", pregnancy),
                transitionBody(externalConfirmation, 0, "occurredOn", null)), mother, pregnancy);
        assertNotFound(request(callerToken, farmA,
                post("/api/v1/herd/pregnancies/{id}/termination", pregnancy),
                transitionBody(externalTermination, 0, "endedOn", "ABORTION")), mother, pregnancy);
        assertNotFound(request(callerToken, farmA,
                post("/api/v1/herd/animals/{id}/calvings", mother),
                calvingBody(externalCalving, 1, pregnancy, 0, calf)), mother, pregnancy);

        assertThat(pregnancyStatus(pregnancy)).isEqualTo("POSSIBLE");
        assertThat(pregnancyVersion(pregnancy)).isZero();
        assertThat(animalVersion(mother)).isOne();
        assertThat(count("select count(*) from app.animals where id=?", calf)).isZero();
        for (UUID operation : List.of(externalConfirmation, externalTermination, externalBreeding, externalCalving)) {
            assertThat(count("select count(*) from app.animal_events where operation_id=?", operation)).isZero();
        }
    }

    @Test
    void optimisticLockingSeparatesReplayFromNewStaleCommandsWithoutPartialWrites() throws Exception {
        UUID breedingMother = createMother(callerToken, farmA);
        UUID breedingOperation = UUID.randomUUID();
        UUID breedingPregnancy = breed(callerToken, farmA, breedingMother, breedingOperation, 0);
        assertConflict(request(callerToken, farmA, post("/api/v1/herd/animals/{id}/breedings", breedingMother),
                breedingBody(UUID.randomUUID(), 0)));

        UUID staleMotherCalf = UUID.randomUUID();
        assertConflict(request(callerToken, farmA, post("/api/v1/herd/animals/{id}/calvings", breedingMother),
                calvingBody(UUID.randomUUID(), 0, breedingPregnancy, 0, staleMotherCalf)));
        assertThat(count("select count(*) from app.animals where id=?", staleMotherCalf)).isZero();

        UUID confirmationMother = createMother(callerToken, farmA);
        UUID confirmationPregnancy = breed(callerToken, farmA, confirmationMother, UUID.randomUUID(), 0);
        UUID confirmationOperation = UUID.randomUUID();
        String confirmation = transitionBody(confirmationOperation, 0, "occurredOn", null);
        assertStatus(request(callerToken, farmA,
                post("/api/v1/herd/pregnancies/{id}/confirmation", confirmationPregnancy), confirmation), 200);
        assertStatus(request(callerToken, farmA,
                post("/api/v1/herd/pregnancies/{id}/confirmation", confirmationPregnancy), confirmation), 200);
        assertConflict(request(callerToken, farmA,
                post("/api/v1/herd/pregnancies/{id}/confirmation", confirmationPregnancy),
                transitionBody(UUID.randomUUID(), 0, "occurredOn", null)));
        assertThat(eventTypeCount(confirmationMother, "PREGNANCY_CONFIRMED")).isOne();
        assertThat(pregnancyVersion(confirmationPregnancy)).isOne();

        UUID terminationMother = createMother(callerToken, farmA);
        UUID terminationPregnancy = breed(callerToken, farmA, terminationMother, UUID.randomUUID(), 0);
        UUID terminationOperation = UUID.randomUUID();
        String termination = transitionBody(terminationOperation, 0, "endedOn", "ABORTION");
        assertStatus(request(callerToken, farmA,
                post("/api/v1/herd/pregnancies/{id}/termination", terminationPregnancy), termination), 200);
        assertStatus(request(callerToken, farmA,
                post("/api/v1/herd/pregnancies/{id}/termination", terminationPregnancy), termination), 200);
        assertConflict(request(callerToken, farmA,
                post("/api/v1/herd/pregnancies/{id}/termination", terminationPregnancy),
                transitionBody(UUID.randomUUID(), 0, "endedOn", "ABORTION")));
        assertThat(eventTypeCount(terminationMother, "PREGNANCY_ENDED")).isOne();
        assertThat(pregnancyVersion(terminationPregnancy)).isOne();

        UUID pregnancyMother = createMother(callerToken, farmA);
        UUID stalePregnancy = breed(callerToken, farmA, pregnancyMother, UUID.randomUUID(), 0);
        assertStatus(request(callerToken, farmA,
                post("/api/v1/herd/pregnancies/{id}/confirmation", stalePregnancy),
                transitionBody(UUID.randomUUID(), 0, "occurredOn", null)), 200);
        UUID stalePregnancyCalf = UUID.randomUUID();
        assertConflict(request(callerToken, farmA,
                post("/api/v1/herd/animals/{id}/calvings", pregnancyMother),
                calvingBody(UUID.randomUUID(), 1, stalePregnancy, 0, stalePregnancyCalf)));
        assertThat(count("select count(*) from app.animals where id=?", stalePregnancyCalf)).isZero();
        assertThat(count("select count(*) from app.animal_maternal_relations where calf_animal_id=?",
                stalePregnancyCalf)).isZero();
        assertThat(eventTypeCount(pregnancyMother, "CALVED")).isZero();
        assertThat(pregnancyStatus(stalePregnancy)).isEqualTo("CONFIRMED");
        assertThat(pregnancyVersion(stalePregnancy)).isOne();
    }

    @Test
    void breedingReplayIsBoundToMotherAndUsesNormalizedSemanticPayload() throws Exception {
        UUID mother = createMother(callerToken, farmA);
        UUID otherMother = createMother(callerToken, farmA);
        UUID operation = UUID.randomUUID();
        LocalDate expectedCalving = SERVICE_ON.plusDays(283);
        String original = """
                {"operationId":"%s","expectedVersion":0,"serviceType":"INSEMINATION",\
                "serviceOn":"%s","sireReference":"  Touro 1  ","notes":"  Observação clínica  "}
                """.formatted(operation, SERVICE_ON);
        String normalizedReplay = """
                {"operationId":"%s","expectedVersion":0,"serviceType":"INSEMINATION",\
                "serviceOn":"%s","sireReference":"Touro 1","expectedCalvingOn":"%s",\
                "notes":"Observação clínica"}
                """.formatted(operation, SERVICE_ON, expectedCalving);

        MvcResult first = request(callerToken, farmA,
                post("/api/v1/herd/animals/{id}/breedings", mother), original);
        assertStatus(first, 201);
        UUID pregnancy = UUID.fromString(json.readTree(first.getResponse().getContentAsString()).path("id").asText());
        MvcResult replay = request(callerToken, farmA,
                post("/api/v1/herd/animals/{id}/breedings", mother), normalizedReplay);
        assertStatus(replay, 201);
        assertThat(json.readTree(replay.getResponse().getContentAsString()).path("id").asText())
                .isEqualTo(pregnancy.toString());
        assertThat(eventTypeCount(mother, "BREEDING_RECORDED")).isOne();
        assertThat(animalVersion(mother)).isOne();

        MvcResult wrongMother = request(callerToken, farmA,
                post("/api/v1/herd/animals/{id}/breedings", otherMother), normalizedReplay);
        assertThat(wrongMother.getResponse().getStatus()).isEqualTo(409);
        assertThat(json.readTree(wrongMother.getResponse().getContentAsString()).path("code").asText())
                .isEqualTo("HERD_OPERATION_IDEMPOTENCY_CONFLICT");
        assertThat(animalVersion(otherMother)).isZero();
        assertThat(count("select count(*) from app.animal_pregnancies where mother_animal_id=?", otherMother)).isZero();
    }

    @Test
    void authorizationMatrixAllowsDocumentedRolesAndRejectsViewerAndOperatorGaps() throws Exception {
        UUID readableMother = createMother(callerToken, farmA);
        UUID readablePregnancy = breed(callerToken, farmA, readableMother, UUID.randomUUID(), 0);
        for (String role : List.of("OWNER", "ADMIN", "MANAGER", "OPERATOR", "VIEWER")) {
            setCallerRole(role);
            assertStatus(request(callerToken, farmA, get("/api/v1/herd/pregnancies/{id}", readablePregnancy), null), 200);
        }

        for (String role : List.of("OWNER", "ADMIN", "MANAGER", "OPERATOR")) {
            setCallerRole(role);
            UUID mother = createMother(callerToken, farmA);
            UUID pregnancy = breed(callerToken, farmA, mother, UUID.randomUUID(), 0);
            assertStatus(request(callerToken, farmA,
                    post("/api/v1/herd/pregnancies/{id}/confirmation", pregnancy),
                    transitionBody(UUID.randomUUID(), 0, "occurredOn", null)), 200);
        }

        for (String role : List.of("OWNER", "ADMIN", "MANAGER")) {
            setCallerRole(role);
            UUID terminationMother = createMother(callerToken, farmA);
            UUID terminationPregnancy = breed(callerToken, farmA, terminationMother, UUID.randomUUID(), 0);
            assertStatus(request(callerToken, farmA,
                    post("/api/v1/herd/pregnancies/{id}/termination", terminationPregnancy),
                    transitionBody(UUID.randomUUID(), 0, "endedOn", "ABORTION")), 200);

            UUID calvingMother = createMother(callerToken, farmA);
            UUID calvingPregnancy = breed(callerToken, farmA, calvingMother, UUID.randomUUID(), 0);
            assertStatus(request(callerToken, farmA,
                    post("/api/v1/herd/animals/{id}/calvings", calvingMother),
                    calvingBody(UUID.randomUUID(), 1, calvingPregnancy, 0, UUID.randomUUID())), 201);
        }

        UUID forbiddenMother = createMother(administratorToken, farmA);
        UUID forbiddenPregnancy = breed(administratorToken, farmA, forbiddenMother, UUID.randomUUID(), 0);
        setCallerRole("VIEWER");
        assertForbidden(request(callerToken, farmA,
                post("/api/v1/herd/pregnancies/{id}/confirmation", forbiddenPregnancy),
                transitionBody(UUID.randomUUID(), 0, "occurredOn", null)));
        assertForbidden(request(callerToken, farmA,
                post("/api/v1/herd/pregnancies/{id}/termination", forbiddenPregnancy),
                transitionBody(UUID.randomUUID(), 0, "endedOn", "ABORTION")));
        assertForbidden(request(callerToken, farmA,
                post("/api/v1/herd/animals/{id}/calvings", forbiddenMother),
                calvingBody(UUID.randomUUID(), 1, forbiddenPregnancy, 0, UUID.randomUUID())));
        setCallerRole("OPERATOR");
        assertForbidden(request(callerToken, farmA,
                post("/api/v1/herd/pregnancies/{id}/termination", forbiddenPregnancy),
                transitionBody(UUID.randomUUID(), 0, "endedOn", "ABORTION")));
        assertForbidden(request(callerToken, farmA,
                post("/api/v1/herd/animals/{id}/calvings", forbiddenMother),
                calvingBody(UUID.randomUUID(), 1, forbiddenPregnancy, 0, UUID.randomUUID())));
        assertThat(pregnancyStatus(forbiddenPregnancy)).isEqualTo("POSSIBLE");
        assertThat(pregnancyVersion(forbiddenPregnancy)).isZero();
        assertThat(animalVersion(forbiddenMother)).isOne();
    }

    @Test
    void transfersAfterCalvingPreserveMaternalHistoryRlsAndCurrentCustodyAuthorization() throws Exception {
        UUID mother = createMother(administratorToken, farmA);
        UUID pregnancy = breed(administratorToken, farmA, mother, UUID.randomUUID(), 0);
        UUID calf = UUID.randomUUID();
        assertStatus(request(administratorToken, farmA,
                post("/api/v1/herd/animals/{id}/calvings", mother),
                calvingBody(UUID.randomUUID(), 1, pregnancy, 0, calf)), 201);
        long reproductiveEvents = reproductiveEventCount();

        transfer(administratorToken, farmA, mother, 2, farmB);
        transfer(administratorToken, farmA, calf, 0, farmC);

        assertThat(reproductiveEventCount()).isEqualTo(reproductiveEvents);
        assertThat(count("select count(*) from app.animal_maternal_relations where mother_animal_id=?", mother)).isOne();
        assertThat(count("select count(*) from app.animal_maternal_relations where calf_animal_id=?", calf)).isOne();
        assertThat(pairCount(mother, calf, pregnancy)).isOne();
        assertThat(animalFarm(mother)).isEqualTo(farmB);
        assertThat(animalFarm(calf)).isEqualTo(farmC);
        assertThat(pregnancyFarm(pregnancy)).isEqualTo(farmA);
        assertThat(pregnancyStatus(pregnancy)).isEqualTo("CALVED");
        assertThat(pregnancyCalf(pregnancy)).isEqualTo(calf);
        assertThat(runtimeRelationCount(tenant)).isOne();
        assertThat(runtimeRelationCount(UUID.randomUUID())).isZero();

        assertNotFound(request(callerToken, farmA, get("/api/v1/herd/pregnancies/{id}", pregnancy), null),
                mother, pregnancy);
        assertStatus(request(administratorToken, farmB,
                get("/api/v1/herd/pregnancies/{id}", pregnancy), null), 200);
        assertNotFound(request(administratorToken, farmC,
                get("/api/v1/herd/animals/{id}/mother", calf), null), mother, calf);
        MvcResult calves = request(administratorToken, farmB,
                get("/api/v1/herd/animals/{id}/calves", mother), null);
        assertStatus(calves, 200);
        assertThat(json.readTree(calves.getResponse().getContentAsString()).size()).isZero();

        UUID currentOperation = UUID.randomUUID();
        assertStatus(request(administratorToken, farmB,
                post("/api/v1/herd/animals/{id}/breedings", mother), breedingBody(currentOperation, 3)), 201);
        assertNotFound(request(callerToken, farmA,
                post("/api/v1/herd/animals/{id}/breedings", mother), breedingBody(UUID.randomUUID(), 3)),
                mother, pregnancy);
        assertThat(pairCount(mother, calf, pregnancy)).isOne();
    }

    private UUID createMother(String bearer, UUID farm) throws Exception {
        UUID mother = UUID.randomUUID();
        MvcResult result = request(bearer, farm, post("/api/v1/herd/animals"), """
                {"id":"%s","identification":"M-%s","sex":"FEMALE"}
                """.formatted(mother, UUID.randomUUID()));
        assertStatus(result, 201);
        return mother;
    }

    private UUID breed(String bearer, UUID farm, UUID mother, UUID operation, long version) throws Exception {
        MvcResult result = request(bearer, farm, post("/api/v1/herd/animals/{id}/breedings", mother),
                breedingBody(operation, version));
        assertStatus(result, 201);
        assertPublicPregnancyResponse(result);
        return UUID.fromString(json.readTree(result.getResponse().getContentAsString()).path("id").asText());
    }

    private void assertPublicPregnancyResponse(MvcResult result) throws Exception {
        assertThat(result.getResponse().getContentAsString()).doesNotContain(
                "\"tenantId\"", "\"farmId\"", "\"operationId\"", "\"commandPayload\"", "\"createdBy\"");
    }

    private void transfer(String bearer, UUID source, UUID animal, long version, UUID destination) throws Exception {
        assertStatus(request(bearer, source, post("/api/v1/herd/animals/{id}/transfers", animal), """
                {"operationId":"%s","expectedVersion":%d,"destinationFarmId":"%s","occurredOn":"2026-09-03"}
                """.formatted(UUID.randomUUID(), version, destination)), 200);
    }

    private String breedingBody(UUID operation, long version) {
        return """
                {"operationId":"%s","expectedVersion":%d,"serviceType":"INSEMINATION","serviceOn":"%s"}
                """.formatted(operation, version, SERVICE_ON);
    }

    private String transitionBody(UUID operation, long version, String dateField, String reason) {
        String reasonJson = reason == null ? "" : ",\"reason\":\"" + reason + "\"";
        return """
                {"operationId":"%s","expectedVersion":%d,"%s":"%s"%s}
                """.formatted(operation, version, dateField, OCCURRED_ON, reasonJson);
    }

    private String calvingBody(UUID operation, long motherVersion, UUID pregnancy,
                               long pregnancyVersion, UUID calf) {
        return """
                {"operationId":"%s","expectedVersion":%d,"pregnancyId":"%s",\
                "expectedPregnancyVersion":%d,"calvedOn":"%s","calfId":"%s",\
                "identification":"C-%s","sex":"FEMALE","birthDate":"%s"}
                """.formatted(operation, motherVersion, pregnancy, pregnancyVersion,
                OCCURRED_ON, calf, calf, OCCURRED_ON);
    }

    private MvcResult request(String bearer, UUID farm, MockHttpServletRequestBuilder request,
                              String body) throws Exception {
        request.header(HttpHeaders.AUTHORIZATION, "Bearer " + bearer)
                .header("X-Organization-Id", tenant)
                .header("X-Farm-Id", farm);
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mvc.perform(request).andReturn();
    }

    private void assertNotFound(MvcResult result, UUID... protectedIds) throws Exception {
        assertThat(result.getResponse().getStatus()).isEqualTo(404);
        String response = result.getResponse().getContentAsString();
        assertThat(json.readTree(response).path("code").asText()).isEqualTo("HERD_ANIMAL_NOT_FOUND");
        for (UUID id : protectedIds) {
            assertThat(response).doesNotContain(id.toString());
        }
    }

    private void assertForbidden(MvcResult result) throws Exception {
        assertThat(result.getResponse().getStatus()).isEqualTo(403);
        assertThat(json.readTree(result.getResponse().getContentAsString()).path("code").asText())
                .isEqualTo("HERD_MOVEMENT_FORBIDDEN");
    }

    private void assertConflict(MvcResult result) throws Exception {
        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        String response = result.getResponse().getContentAsString();
        assertThat(response).doesNotContain("currentVersion", "actualVersion", "SQL", "Exception",
                tenant.toString(), farmA.toString());
    }

    private static void assertStatus(MvcResult result, int expected) {
        assertThat(result.getResponse().getStatus()).isEqualTo(expected);
    }

    private void setCallerRole(String role) throws Exception {
        try (Connection connection = PostgresTestEnvironment.adminConnection();
             var statement = connection.prepareStatement(
                     "update app.organization_memberships set role_key=? where id=?")) {
            statement.setString(1, role);
            statement.setObject(2, callerMembership);
            statement.executeUpdate();
        }
    }

    private long eventTypeCount(UUID animal, String type) throws Exception {
        try (Connection connection = PostgresTestEnvironment.adminConnection();
             var statement = connection.prepareStatement("""
                     select count(*) from app.animal_events where animal_id=? and event_type=?
                     """)) {
            statement.setObject(1, animal);
            statement.setString(2, type);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getLong(1);
            }
        }
    }

    private long reproductiveEventCount() throws Exception {
        try (Connection connection = PostgresTestEnvironment.adminConnection();
             var statement = connection.createStatement();
             ResultSet result = statement.executeQuery("""
                     select count(*) from app.animal_events where event_type in
                       ('BREEDING_RECORDED','PREGNANCY_CONFIRMED','PREGNANCY_ENDED','CALVED','BORN')
                     """)) {
            result.next();
            return result.getLong(1);
        }
    }

    private long runtimeRelationCount(UUID currentTenant) throws Exception {
        try (Connection connection = PostgresTestEnvironment.runtimeConnection()) {
            connection.setAutoCommit(false);
            try (var statement = connection.createStatement()) {
                statement.execute("set local role app_api");
            }
            try (var statement = connection.prepareStatement(
                    "select set_config('app.current_tenant_id', ?, true)")) {
                statement.setString(1, currentTenant.toString());
                statement.execute();
            }
            try (var statement = connection.createStatement();
                 ResultSet result = statement.executeQuery(
                         "select count(*) from app.animal_maternal_relations")) {
                result.next();
                return result.getLong(1);
            }
        }
    }

    private long pairCount(UUID mother, UUID calf, UUID pregnancy) throws Exception {
        try (Connection connection = PostgresTestEnvironment.adminConnection();
             var statement = connection.prepareStatement("""
                     select count(*) from app.animal_maternal_relations
                     where tenant_id=? and mother_animal_id=? and calf_animal_id=? and pregnancy_id=?
                     """)) {
            statement.setObject(1, tenant);
            statement.setObject(2, mother);
            statement.setObject(3, calf);
            statement.setObject(4, pregnancy);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getLong(1);
            }
        }
    }

    private long count(String sql, UUID value) throws Exception {
        try (Connection connection = PostgresTestEnvironment.adminConnection();
             var statement = connection.prepareStatement(sql)) {
            statement.setObject(1, value);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getLong(1);
            }
        }
    }

    private long animalVersion(UUID animal) throws Exception {
        return count("select version from app.animals where id=?", animal);
    }

    private UUID animalFarm(UUID animal) throws Exception {
        return uuid("select farm_id from app.animals where id=?", animal);
    }

    private long pregnancyVersion(UUID pregnancy) throws Exception {
        return count("select version from app.animal_pregnancies where id=?", pregnancy);
    }

    private String pregnancyStatus(UUID pregnancy) throws Exception {
        return string("select status from app.animal_pregnancies where id=?", pregnancy);
    }

    private UUID pregnancyFarm(UUID pregnancy) throws Exception {
        return uuid("select farm_id from app.animal_pregnancies where id=?", pregnancy);
    }

    private UUID pregnancyCalf(UUID pregnancy) throws Exception {
        return uuid("select calf_animal_id from app.animal_pregnancies where id=?", pregnancy);
    }

    private String string(String sql, UUID value) throws Exception {
        try (Connection connection = PostgresTestEnvironment.adminConnection();
             var statement = connection.prepareStatement(sql)) {
            statement.setObject(1, value);
            try (ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                return result.getString(1);
            }
        }
    }

    private UUID uuid(String sql, UUID value) throws Exception {
        try (Connection connection = PostgresTestEnvironment.adminConnection();
             var statement = connection.prepareStatement(sql)) {
            statement.setObject(1, value);
            try (ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                return result.getObject(1, UUID.class);
            }
        }
    }

    private String token(UUID user) throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder().issuer(ISSUER).audience("authenticated")
                .subject(user.toString()).claim("role", "authenticated").issueTime(new Date())
                .expirationTime(Date.from(Instant.now().plusSeconds(300))).build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        jwt.sign(new MACSigner(SECRET.getBytes()));
        return jwt.serialize();
    }
}
