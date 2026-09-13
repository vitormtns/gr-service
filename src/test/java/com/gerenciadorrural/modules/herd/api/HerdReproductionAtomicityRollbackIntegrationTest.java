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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
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
class HerdReproductionAtomicityRollbackIntegrationTest extends SpringPostgresTestSupport {

    private static final String ISSUER = "https://auth.example.test/auth/v1";
    private static final String SECRET = "test-only-hmac-key-with-at-least-32-bytes";
    private static final LocalDate SERVICE_ON = LocalDate.of(2026, 9, 1);
    private static final LocalDate OCCURRED_ON = LocalDate.of(2026, 9, 2);

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    UUID user;
    UUID tenant;
    UUID farm;
    String token;

    @BeforeEach
    void seed() throws Exception {
        PostgresTestEnvironment.clearUsers();
        user = UUID.randomUUID();
        tenant = UUID.randomUUID();
        farm = UUID.randomUUID();
        UUID membership = UUID.randomUUID();
        try (Connection connection = PostgresTestEnvironment.adminConnection();
             var statement = connection.prepareStatement("""
                     insert into app.users(id,status) values(?,'ACTIVE');
                     insert into app.organizations(id,name,status) values(?,'Organização','ACTIVE');
                     insert into app.farms(id,tenant_id,name,status) values(?,?,'Fazenda','ACTIVE');
                     insert into app.organization_memberships(id,tenant_id,user_id,role_key,status,farm_scope_mode)
                     values(?,?,?,'OWNER','ACTIVE','SELECTED_FARMS');
                     insert into app.membership_farm_scopes(tenant_id,membership_id,farm_id) values(?,?,?)
                     """)) {
            int index = 1;
            statement.setObject(index++, user);
            statement.setObject(index++, tenant);
            statement.setObject(index++, farm);
            statement.setObject(index++, tenant);
            statement.setObject(index++, membership);
            statement.setObject(index++, tenant);
            statement.setObject(index++, user);
            statement.setObject(index++, tenant);
            statement.setObject(index++, membership);
            statement.setObject(index, farm);
            statement.executeUpdate();
        }
        token = token();
    }

    @Test
    void breedingRollbackRemovesPregnancyVersionEventAndOperationThenAllowsRetry() throws Exception {
        UUID mother = createMother();
        UUID operation = UUID.randomUUID();
        String body = breedingBody(operation, 0);

        failOnEvent(operation, "BREEDING_RECORDED", () -> {
            assertUnavailable(request(post("/api/v1/herd/animals/{id}/breedings", mother), body), operation);
            assertThat(count("select count(*) from app.animal_pregnancies where mother_animal_id=?", mother)).isZero();
            assertThat(version(mother)).isZero();
            assertThat(eventCount(operation)).isZero();
        });

        MvcResult retry = request(post("/api/v1/herd/animals/{id}/breedings", mother), body);
        assertThat(retry.getResponse().getStatus()).isEqualTo(201);
        assertThat(count("select count(*) from app.animal_pregnancies where mother_animal_id=?", mother)).isOne();
        assertThat(version(mother)).isOne();
        assertThat(eventCount(operation)).isOne();
        assertHarnessClean();
    }

    @Test
    void confirmationRollbackRestoresPregnancyAndAllowsRetry() throws Exception {
        UUID mother = createMother();
        UUID pregnancy = breed(mother);
        UUID operation = UUID.randomUUID();
        String body = transitionBody(operation, 0, "occurredOn", null);

        failOnEvent(operation, "PREGNANCY_CONFIRMED", () -> {
            assertUnavailable(request(post("/api/v1/herd/pregnancies/{id}/confirmation", pregnancy), body), operation);
            PregnancyState state = pregnancy(pregnancy);
            assertThat(state.status()).isEqualTo("POSSIBLE");
            assertThat(state.version()).isZero();
            assertThat(state.confirmedOn()).isNull();
            assertThat(eventCount(operation)).isZero();
        });

        assertThat(request(post("/api/v1/herd/pregnancies/{id}/confirmation", pregnancy), body)
                .getResponse().getStatus()).isEqualTo(200);
        PregnancyState result = pregnancy(pregnancy);
        assertThat(result.status()).isEqualTo("CONFIRMED");
        assertThat(result.version()).isOne();
        assertThat(result.confirmedOn()).isEqualTo(OCCURRED_ON);
        assertThat(eventCount(operation)).isOne();
        assertHarnessClean();
    }

    @Test
    void terminationRollbackRestoresConfirmedStateAndAllowsRetry() throws Exception {
        UUID mother = createMother();
        UUID pregnancy = breed(mother);
        UUID confirmation = UUID.randomUUID();
        assertThat(request(post("/api/v1/herd/pregnancies/{id}/confirmation", pregnancy),
                transitionBody(confirmation, 0, "occurredOn", null)).getResponse().getStatus()).isEqualTo(200);
        PregnancyState before = pregnancy(pregnancy);
        UUID operation = UUID.randomUUID();
        String body = transitionBody(operation, 1, "endedOn", "ABORTION");

        failOnEvent(operation, "PREGNANCY_ENDED", () -> {
            assertUnavailable(request(post("/api/v1/herd/pregnancies/{id}/termination", pregnancy), body), operation);
            PregnancyState state = pregnancy(pregnancy);
            assertThat(state.status()).isEqualTo("CONFIRMED");
            assertThat(state.version()).isEqualTo(1);
            assertThat(state.confirmedOn()).isEqualTo(before.confirmedOn());
            assertThat(state.endedOn()).isNull();
            assertThat(state.terminationReason()).isNull();
            assertThat(eventCount(operation)).isZero();
        });

        assertThat(request(post("/api/v1/herd/pregnancies/{id}/termination", pregnancy), body)
                .getResponse().getStatus()).isEqualTo(200);
        PregnancyState result = pregnancy(pregnancy);
        assertThat(result.status()).isEqualTo("TERMINATED");
        assertThat(result.version()).isEqualTo(2);
        assertThat(result.endedOn()).isEqualTo(OCCURRED_ON);
        assertThat(result.terminationReason()).isEqualTo("ABORTION");
        assertThat(eventCount(operation)).isOne();
        assertHarnessClean();
    }

    @Test
    void calvingWithPregnancyRollbackRemovesEveryPartialWriteAndAllowsRetry() throws Exception {
        UUID mother = createMother();
        UUID pregnancy = breed(mother);
        UUID calf = UUID.randomUUID();
        UUID operation = UUID.randomUUID();
        String body = calvingBody(operation, 1, pregnancy, 0L, calf);

        failOnEvent(operation, "CALVED", () -> {
            assertUnavailable(request(post("/api/v1/herd/animals/{id}/calvings", mother), body), operation);
            assertCalvingRolledBack(mother, pregnancy, calf, operation, 1);
        });

        assertThat(request(post("/api/v1/herd/animals/{id}/calvings", mother), body)
                .getResponse().getStatus()).isEqualTo(201);
        assertSuccessfulCalving(mother, pregnancy, calf, operation, 2);
        assertHarnessClean();
    }

    @Test
    void calvingWithoutPregnancyRollbackRemovesEveryPartialWriteAndAllowsRetry() throws Exception {
        UUID mother = createMother();
        UUID calf = UUID.randomUUID();
        UUID operation = UUID.randomUUID();
        String body = calvingBody(operation, 0, null, null, calf);

        failOnEvent(operation, "CALVED", () -> {
            assertUnavailable(request(post("/api/v1/herd/animals/{id}/calvings", mother), body), operation);
            assertThat(count("select count(*) from app.animals where id=?", calf)).isZero();
            assertThat(count("select count(*) from app.animal_maternal_relations where calf_animal_id=?", calf)).isZero();
            assertThat(version(mother)).isZero();
            assertThat(eventCount(operation)).isZero();
            assertThat(calfEventCount(calf)).isZero();
        });

        assertThat(request(post("/api/v1/herd/animals/{id}/calvings", mother), body)
                .getResponse().getStatus()).isEqualTo(201);
        assertThat(count("select count(*) from app.animals where id=?", calf)).isOne();
        assertThat(count("select count(*) from app.animal_maternal_relations where calf_animal_id=?", calf)).isOne();
        assertThat(version(mother)).isOne();
        assertThat(eventCount(operation)).isEqualTo(2);
        assertThat(calfEventCount(calf)).isEqualTo(2);
        assertHarnessClean();
    }

    private void assertCalvingRolledBack(UUID mother, UUID pregnancy, UUID calf, UUID operation,
                                         long motherVersion) throws Exception {
        assertThat(count("select count(*) from app.animals where id=?", calf)).isZero();
        assertThat(count("select count(*) from app.animal_maternal_relations where calf_animal_id=?", calf)).isZero();
        PregnancyState state = pregnancy(pregnancy);
        assertThat(state.status()).isEqualTo("POSSIBLE");
        assertThat(state.version()).isZero();
        assertThat(state.calfAnimalId()).isNull();
        assertThat(version(mother)).isEqualTo(motherVersion);
        assertThat(eventCount(operation)).isZero();
        assertThat(calfEventCount(calf)).isZero();
    }

    private void assertSuccessfulCalving(UUID mother, UUID pregnancy, UUID calf, UUID operation,
                                         long motherVersion) throws Exception {
        assertThat(count("select count(*) from app.animals where id=?", calf)).isOne();
        assertThat(count("select count(*) from app.animal_maternal_relations where calf_animal_id=?", calf)).isOne();
        PregnancyState state = pregnancy(pregnancy);
        assertThat(state.status()).isEqualTo("CALVED");
        assertThat(state.version()).isOne();
        assertThat(state.calfAnimalId()).isEqualTo(calf);
        assertThat(version(mother)).isEqualTo(motherVersion);
        assertThat(eventCount(operation)).isEqualTo(2);
        assertThat(calfEventCount(calf)).isEqualTo(2);
    }

    private void failOnEvent(UUID operation, String eventType, CheckedRunnable assertion) throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String trigger = "repro_fault_trigger_" + suffix;
        String function = "app.repro_fault_function_" + suffix;
        Throwable failure = null;
        try {
            installFault(trigger, function, operation, eventType);
            assertion.run();
        } catch (Throwable throwable) {
            failure = throwable;
            throw throwable;
        } finally {
            try {
                removeFault(trigger, function);
            } catch (Exception cleanupFailure) {
                if (failure == null) {
                    throw cleanupFailure;
                }
                failure.addSuppressed(cleanupFailure);
            }
        }
    }

    private void installFault(String trigger, String function, UUID operation, String eventType) throws Exception {
        try (Connection connection = PostgresTestEnvironment.adminConnection();
             var statement = connection.createStatement()) {
            statement.execute("""
                    create function %s() returns trigger language plpgsql as $$
                    begin
                        if new.operation_id = '%s'::uuid and new.event_type = '%s' then
                            raise exception 'injected reproduction persistence failure';
                        end if;
                        return new;
                    end;
                    $$
                    """.formatted(function, operation, eventType));
            statement.execute("create trigger " + trigger
                    + " before insert on app.animal_events for each row execute function " + function + "()");
        }
    }

    private void removeFault(String trigger, String function) throws Exception {
        Exception failure = null;
        try (Connection connection = PostgresTestEnvironment.adminConnection();
             var statement = connection.createStatement()) {
            try {
                statement.execute("drop trigger if exists " + trigger + " on app.animal_events");
            } catch (Exception exception) {
                failure = exception;
            }
            try {
                statement.execute("drop function if exists " + function + "()");
            } catch (Exception exception) {
                if (failure == null) {
                    failure = exception;
                } else {
                    failure.addSuppressed(exception);
                }
            }
            try (ResultSet result = statement.executeQuery("""
                    select count(*) from pg_trigger
                    where tgrelid='app.animal_events'::regclass and tgname='%s'
                    """.formatted(trigger))) {
                result.next();
                assertThat(result.getLong(1)).isZero();
            }
            try (ResultSet result = statement.executeQuery(
                    "select to_regprocedure('" + function + "()') is null")) {
                result.next();
                assertThat(result.getBoolean(1)).isTrue();
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    private void assertHarnessClean() throws Exception {
        try (Connection connection = PostgresTestEnvironment.adminConnection();
             var statement = connection.createStatement();
             ResultSet result = statement.executeQuery("""
                     select
                       (select count(*) from pg_trigger where tgname like 'repro_fault_trigger_%'),
                       (select count(*) from pg_proc join pg_namespace on pg_namespace.oid=pg_proc.pronamespace
                        where nspname='app' and proname like 'repro_fault_function_%'),
                       (select count(*) from pg_locks where locktype='advisory')
                     """)) {
            result.next();
            assertThat(result.getLong(1)).isZero();
            assertThat(result.getLong(2)).isZero();
            assertThat(result.getLong(3)).isZero();
        }
    }

    private void assertUnavailable(MvcResult result, UUID operation) throws Exception {
        String response = result.getResponse().getContentAsString();
        JsonNode body = json.readTree(response);
        assertThat(result.getResponse().getStatus()).isEqualTo(503);
        assertThat(body.path("code").asText()).isEqualTo("HERD_PERSISTENCE_UNAVAILABLE");
        assertThat(response).doesNotContain("SQL", "SQLState", "injected", "app.animal_events",
                "constraint", "Exception", tenant.toString(), farm.toString(), operation.toString());
    }

    private UUID createMother() throws Exception {
        UUID mother = UUID.randomUUID();
        MvcResult result = request(post("/api/v1/herd/animals"), """
                {"id":"%s","identification":"M-%s","sex":"FEMALE"}
                """.formatted(mother, UUID.randomUUID()));
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        return mother;
    }

    private UUID breed(UUID mother) throws Exception {
        MvcResult result = request(post("/api/v1/herd/animals/{id}/breedings", mother),
                breedingBody(UUID.randomUUID(), 0));
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        return UUID.fromString(json.readTree(result.getResponse().getContentAsString()).path("id").asText());
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
                               Long pregnancyVersion, UUID calf) {
        String pregnancyJson = pregnancy == null ? "" : """
                ,"pregnancyId":"%s","expectedPregnancyVersion":%d
                """.formatted(pregnancy, pregnancyVersion).strip();
        return """
                {"operationId":"%s","expectedVersion":%d%s,"calvedOn":"%s","calfId":"%s",\
                "identification":"C-%s","sex":"FEMALE","birthDate":"%s"}
                """.formatted(operation, motherVersion, pregnancyJson, OCCURRED_ON, calf, calf, OCCURRED_ON);
    }

    private MvcResult request(MockHttpServletRequestBuilder request, String body) throws Exception {
        return mvc.perform(request.contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("X-Organization-Id", tenant)
                        .header("X-Farm-Id", farm)
                        .content(body))
                .andReturn();
    }

    private PregnancyState pregnancy(UUID id) throws Exception {
        try (Connection connection = PostgresTestEnvironment.adminConnection();
             var statement = connection.prepareStatement("""
                     select status,version,confirmed_on,ended_on,termination_reason,calf_animal_id
                     from app.animal_pregnancies where id=?
                     """)) {
            statement.setObject(1, id);
            try (ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                return new PregnancyState(result.getString(1), result.getLong(2),
                        result.getObject(3, LocalDate.class), result.getObject(4, LocalDate.class),
                        result.getString(5), result.getObject(6, UUID.class));
            }
        }
    }

    private long version(UUID animal) throws Exception {
        return scalar("select version from app.animals where id=?", animal);
    }

    private long eventCount(UUID operation) throws Exception {
        return count("select count(*) from app.animal_events where operation_id=?", operation);
    }

    private long calfEventCount(UUID calf) throws Exception {
        return count("""
                select count(*) from app.animal_events
                where animal_id=? and event_type in ('CREATED','BORN')
                """, calf);
    }

    private long count(String sql, UUID value) throws Exception {
        return scalar(sql, value);
    }

    private long scalar(String sql, UUID value) throws Exception {
        try (Connection connection = PostgresTestEnvironment.adminConnection();
             var statement = connection.prepareStatement(sql)) {
            statement.setObject(1, value);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getLong(1);
            }
        }
    }

    private String token() throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder().issuer(ISSUER).audience("authenticated")
                .subject(user.toString()).claim("role", "authenticated").issueTime(new Date())
                .expirationTime(Date.from(Instant.now().plusSeconds(300))).build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        jwt.sign(new MACSigner(SECRET.getBytes()));
        return jwt.serialize();
    }

    @FunctionalInterface
    private interface CheckedRunnable {
        void run() throws Exception;
    }

    private record PregnancyState(String status, long version, LocalDate confirmedOn, LocalDate endedOn,
                                  String terminationReason, UUID calfAnimalId) { }
}
