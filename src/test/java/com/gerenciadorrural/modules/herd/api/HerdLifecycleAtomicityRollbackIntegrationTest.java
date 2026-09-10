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

import java.sql.Connection;
import java.sql.ResultSet;
import java.time.Instant;
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
class HerdLifecycleAtomicityRollbackIntegrationTest extends SpringPostgresTestSupport {

    private static final String ISSUER = "https://auth.example.test/auth/v1";
    private static final String HMAC_SECRET = "test-only-hmac-key-with-at-least-32-bytes";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    private UUID userId;
    private UUID tenantId;
    private UUID farmId;
    private String token;

    @BeforeEach
    void seed() throws Exception {
        PostgresTestEnvironment.clearUsers();
        userId = UUID.randomUUID();
        tenantId = UUID.randomUUID();
        farmId = UUID.randomUUID();
        UUID membershipId = UUID.randomUUID();
        try (Connection connection = PostgresTestEnvironment.adminConnection();
             var statement = connection.prepareStatement("""
                 insert into app.users(id, status) values (?, 'ACTIVE');
                 insert into app.organizations(id, name, status) values (?, 'Organização', 'ACTIVE');
                 insert into app.organization_memberships(id, tenant_id, user_id, role_key, status, farm_scope_mode)
                 values (?, ?, ?, 'OWNER', 'ACTIVE', 'SELECTED_FARMS');
                 insert into app.farms(id, tenant_id, name, status) values (?, ?, 'Fazenda', 'ACTIVE');
                 insert into app.membership_farm_scopes(tenant_id, membership_id, farm_id) values (?, ?, ?)
                 """)) {
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
            statement.setObject(index, farmId);
            statement.executeUpdate();
        }
        token = token();
    }

    @Test
    void rollsBackAnimalStateWhenTerminalEventInsertFails() throws Exception {
        UUID animalId = createAnimal();
        Snapshot before = snapshot(animalId);
        UUID operationId = UUID.randomUUID();
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String triggerName = "atomicity_event_trigger_" + suffix;
        String functionName = "app.atomicity_event_function_" + suffix;

        Throwable testFailure = null;
        try {
            installFaultInjection(triggerName, functionName, operationId);
            MvcResult result = mvc.perform(post("/api/v1/herd/animals/{id}/sale", animalId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .header("X-Organization-Id", tenantId)
                    .header("X-Farm-Id", farmId)
                    .content("""
                        {"operationId":"%s","expectedVersion":0,"occurredOn":"2026-09-08","notes":"Venda de teste"}
                        """.formatted(operationId)))
                .andReturn();

            String response = result.getResponse().getContentAsString();
            JsonNode body = objectMapper.readTree(response);
            assertThat(result.getResponse().getStatus()).isEqualTo(503);
            assertThat(body.path("code").asText()).isEqualTo("HERD_PERSISTENCE_UNAVAILABLE");
            assertSanitized(response, operationId, triggerName, functionName);

            Snapshot after = snapshot(animalId);
            assertThat(after.status()).isEqualTo(before.status()).isEqualTo("ACTIVE");
            assertThat(after.version()).isEqualTo(before.version()).isZero();
            assertThat(after.updatedAt()).isEqualTo(before.updatedAt());
            assertThat(after.eventCount()).isEqualTo(before.eventCount());
            assertThat(after.events()).isEqualTo(before.events()).contains("CREATED");
            assertThat(terminalEventCount(animalId)).isZero();
            assertThat(operationEventCount(operationId)).isZero();
        } catch (Throwable failure) {
            testFailure = failure;
            throw failure;
        } finally {
            try {
                removeFaultInjection(triggerName, functionName);
            } catch (Exception cleanupFailure) {
                if (testFailure != null) {
                    testFailure.addSuppressed(cleanupFailure);
                } else {
                    throw cleanupFailure;
                }
            }
        }
    }

    private UUID createAnimal() throws Exception {
        UUID animalId = UUID.randomUUID();
        int status = mvc.perform(post("/api/v1/herd/animals")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .header("X-Organization-Id", tenantId)
                .header("X-Farm-Id", farmId)
                .content("""
                    {"id":"%s","identification":"AT-%s","sex":"FEMALE"}
                    """.formatted(animalId, UUID.randomUUID())))
            .andReturn().getResponse().getStatus();
        assertThat(status).isEqualTo(201);
        return animalId;
    }

    private void installFaultInjection(String triggerName, String functionName, UUID operationId) throws Exception {
        try (Connection connection = PostgresTestEnvironment.adminConnection();
             var statement = connection.createStatement()) {
            statement.execute("""
                create function %s() returns trigger language plpgsql as $$
                begin
                    if new.operation_id = '%s'::uuid then
                        raise exception 'injected event persistence failure';
                    end if;
                    return new;
                end;
                $$
                """.formatted(functionName, operationId));
            statement.execute("create trigger " + triggerName
                + " before insert on app.animal_events for each row execute function " + functionName + "()");
        }
    }

    private void removeFaultInjection(String triggerName, String functionName) throws Exception {
        Exception failure = null;
        try (Connection connection = PostgresTestEnvironment.adminConnection(); var statement = connection.createStatement()) {
            try { statement.execute("drop trigger if exists " + triggerName + " on app.animal_events"); } catch (Exception exception) { failure = exception; }
            try { statement.execute("drop function if exists " + functionName + "()"); } catch (Exception exception) { if (failure == null) failure = exception; else failure.addSuppressed(exception); }
            try (ResultSet result = statement.executeQuery("""
                select count(*) from pg_trigger where tgrelid = 'app.animal_events'::regclass and tgname = '%s'
                """.formatted(triggerName))) {
                result.next();
                assertThat(result.getLong(1)).isZero();
            }
            try (ResultSet result = statement.executeQuery("select to_regprocedure('" + functionName + "()') is null")) {
                result.next();
                assertThat(result.getBoolean(1)).isTrue();
            }
        }
        if (failure != null) throw failure;
    }

    private Snapshot snapshot(UUID animalId) throws Exception {
        try (Connection connection = PostgresTestEnvironment.adminConnection();
             var statement = connection.prepareStatement("""
                 select status, version, updated_at,
                    (select count(*) from app.animal_events where animal_id = animals.id) event_count,
                    (select coalesce(string_agg(event_type, ',' order by recorded_at, id), '')
                     from app.animal_events where animal_id = animals.id) events
                 from app.animals where id = ?
                 """)) {
            statement.setObject(1, animalId);
            try (ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                return new Snapshot(result.getString("status"), result.getLong("version"),
                    result.getObject("updated_at", java.time.OffsetDateTime.class), result.getLong("event_count"),
                    result.getString("events"));
            }
        }
    }

    private long terminalEventCount(UUID animalId) throws Exception { return count("select count(*) from app.animal_events where animal_id = ? and event_type in ('SOLD', 'DECEASED')", animalId); }
    private long operationEventCount(UUID operationId) throws Exception { return count("select count(*) from app.animal_events where operation_id = ?", operationId); }
    private long count(String sql, UUID value) throws Exception { try (Connection connection = PostgresTestEnvironment.adminConnection(); var statement = connection.prepareStatement(sql)) { statement.setObject(1, value); try (ResultSet result = statement.executeQuery()) { result.next(); return result.getLong(1); } } }

    private void assertSanitized(String response, UUID operationId, String triggerName, String functionName) {
        assertThat(response).doesNotContain("SQL", "SQLState", triggerName, functionName, "app.animal_events", "constraint", "stack", "Exception", tenantId.toString(), farmId.toString(), operationId.toString());
    }

    private String token() throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder().issuer(ISSUER).audience("authenticated")
            .subject(userId.toString()).claim("role", "authenticated").issueTime(new Date())
            .expirationTime(Date.from(Instant.now().plusSeconds(300))).build();
        SignedJWT signedJwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        signedJwt.sign(new MACSigner(HMAC_SECRET.getBytes()));
        return signedJwt.serialize();
    }

    private record Snapshot(String status, long version, java.time.OffsetDateTime updatedAt, long eventCount, String events) { }
}
