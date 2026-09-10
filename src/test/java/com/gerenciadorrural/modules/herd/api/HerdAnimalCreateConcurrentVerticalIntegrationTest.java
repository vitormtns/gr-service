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
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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
class HerdAnimalCreateConcurrentVerticalIntegrationTest extends SpringPostgresTestSupport {

    private static final String ISSUER = "https://auth.example.test/auth/v1";
    private static final String AUDIENCE = "authenticated";
    private static final String HMAC_SECRET = "test-only-hmac-key-with-at-least-32-bytes";
    private static final int ITERATIONS = 5;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    private UUID userA;
    private UUID userB;
    private UUID tenantA;
    private UUID tenantB;
    private UUID farmA1;
    private UUID farmA2;
    private UUID farmB;
    private String tokenA;
    private String tokenB;

    @BeforeEach
    void seed() throws Exception {
        PostgresTestEnvironment.clearUsers();
        userA = UUID.randomUUID();
        userB = UUID.randomUUID();
        tenantA = UUID.randomUUID();
        tenantB = UUID.randomUUID();
        farmA1 = UUID.randomUUID();
        farmA2 = UUID.randomUUID();
        farmB = UUID.randomUUID();
        UUID membershipA = UUID.randomUUID();
        UUID membershipB = UUID.randomUUID();

        String sql = """
                insert into app.users (id, status) values (?, 'ACTIVE'), (?, 'ACTIVE');
                insert into app.organizations (id, name, status) values
                    (?, 'Tenant A', 'ACTIVE'), (?, 'Tenant B', 'ACTIVE');
                insert into app.organization_memberships (
                    id, tenant_id, user_id, role_key, status, farm_scope_mode
                ) values
                    (?, ?, ?, 'OWNER', 'ACTIVE', 'SELECTED_FARMS'),
                    (?, ?, ?, 'OWNER', 'ACTIVE', 'SELECTED_FARMS');
                insert into app.farms (id, tenant_id, name, status) values
                    (?, ?, 'Fazenda A1', 'ACTIVE'),
                    (?, ?, 'Fazenda A2', 'ACTIVE'),
                    (?, ?, 'Fazenda B', 'ACTIVE');
                insert into app.membership_farm_scopes (tenant_id, membership_id, farm_id) values
                    (?, ?, ?), (?, ?, ?), (?, ?, ?);
                """;
        try (Connection connection = PostgresTestEnvironment.adminConnection();
             var statement = connection.prepareStatement(sql)) {
            int index = 1;
            statement.setObject(index++, userA);
            statement.setObject(index++, userB);
            statement.setObject(index++, tenantA);
            statement.setObject(index++, tenantB);
            statement.setObject(index++, membershipA);
            statement.setObject(index++, tenantA);
            statement.setObject(index++, userA);
            statement.setObject(index++, membershipB);
            statement.setObject(index++, tenantB);
            statement.setObject(index++, userB);
            statement.setObject(index++, farmA1);
            statement.setObject(index++, tenantA);
            statement.setObject(index++, farmA2);
            statement.setObject(index++, tenantA);
            statement.setObject(index++, farmB);
            statement.setObject(index++, tenantB);
            statement.setObject(index++, tenantA);
            statement.setObject(index++, membershipA);
            statement.setObject(index++, farmA1);
            statement.setObject(index++, tenantA);
            statement.setObject(index++, membershipA);
            statement.setObject(index++, farmA2);
            statement.setObject(index++, tenantB);
            statement.setObject(index++, membershipB);
            statement.setObject(index++, farmB);
            statement.executeUpdate();
        }
        tokenA = token(userA);
        tokenB = token(userB);
    }

    @Test
    void sameIdAndSamePayloadProduceOneCreatedAndOneReplayInEveryIteration() throws Exception {
        withTwoThreads(executor -> {
            for (int iteration = 0; iteration < ITERATIONS; iteration++) {
                UUID id = UUID.randomUUID();
                Payload payload = new Payload(id, "A-" + iteration, "Brisa " + iteration,
                        "FEMALE", LocalDate.of(2024, 3, 15));

                List<Attempt> attempts = runPair(executor,
                        request(tokenA, tenantA, farmA1, payload),
                        request(tokenA, tenantA, farmA1, payload));

                assertStatuses(attempts, 201, 200);
                for (Attempt attempt : attempts) {
                    assertAnimalResponse(attempt, payload);
                }
                StoredAnimal stored = singleStored(id);
                assertStored(stored, tenantA, farmA1, payload);
                assertThat(countById(id)).isEqualTo(1);
            }
        });
    }

    @Test
    void sameIdAndDivergentPayloadProduceOneCreatedAndOneIdempotencyConflict() throws Exception {
        withTwoThreads(executor -> {
            for (int iteration = 0; iteration < ITERATIONS; iteration++) {
                UUID id = UUID.randomUUID();
                Payload first = new Payload(id, "B-" + iteration + "-A", "Brisa " + iteration,
                        "FEMALE", LocalDate.of(2023, 1, 2));
                Payload second = new Payload(id, "B-" + iteration + "-B", "Trovão " + iteration,
                        "MALE", LocalDate.of(2022, 2, 3));

                List<Attempt> attempts = runPair(executor,
                        request(tokenA, tenantA, farmA1, first),
                        request(tokenA, tenantA, farmA1, second));

                assertStatuses(attempts, 201, 409);
                Attempt loser = withStatus(attempts, 409);
                assertThat(code(loser)).isEqualTo("HERD_IDEMPOTENCY_CONFLICT");
                Attempt winner = withStatus(attempts, 201);
                assertAnimalResponse(winner, winner.request().payload());
                StoredAnimal stored = singleStored(id);
                assertThat(stored).satisfiesAnyOf(
                        animal -> assertStored(animal, tenantA, farmA1, first),
                        animal -> assertStored(animal, tenantA, farmA1, second));
                assertThat(countById(id)).isEqualTo(1);
            }
        });
    }

    @Test
    void differentIdsAndNormalizedEqualIdentificationPreserveIdentificationConflict() throws Exception {
        withTwoThreads(executor -> {
            for (int iteration = 0; iteration < ITERATIONS; iteration++) {
                String identification = "A-001";
                Payload first = new Payload(UUID.randomUUID(), identification, "Primeiro",
                        "FEMALE", null);
                Payload second = new Payload(UUID.randomUUID(), " \t" + identification + "\r\n", "Segundo",
                        "MALE", null);

                List<Attempt> attempts = runPair(executor,
                        request(tokenA, tenantA, farmA1, first),
                        request(tokenA, tenantA, farmA1, second));

                assertStatuses(attempts, 201, 409);
                assertThat(code(withStatus(attempts, 409))).isEqualTo("HERD_IDENTIFICATION_CONFLICT");
                StoredAnimal stored = singleStored(identification, farmA1);
                assertThat(stored.id()).isIn(first.id(), second.id());
                assertThat(stored.identification()).isEqualTo(identification);
                assertThat(stored.version()).isZero();
                assertThat(countByNormalizedIdentification(tenantA, farmA1, identification)).isEqualTo(1);
                if (iteration < ITERATIONS - 1) {
                    deleteAnimals(tenantA, farmA1);
                }
            }
        });
    }

    @Test
    void sameUuidAcrossTenantsReturnsGenericConflictWithoutExposingTheWinner() throws Exception {
        withTwoThreads(executor -> {
            for (int iteration = 0; iteration < ITERATIONS; iteration++) {
                UUID id = UUID.randomUUID();
                Payload payloadA = new Payload(id, "D-" + iteration + "-A", "Animal sigiloso A",
                        "FEMALE", null);
                Payload payloadB = new Payload(id, "D-" + iteration + "-B", "Animal sigiloso B",
                        "MALE", null);

                List<Attempt> attempts = runPair(executor,
                        request(tokenA, tenantA, farmA1, payloadA),
                        request(tokenB, tenantB, farmB, payloadB));

                assertStatuses(attempts, 201, 409);
                Attempt winner = withStatus(attempts, 201);
                Attempt loser = withStatus(attempts, 409);
                assertThat(code(loser)).isEqualTo("HERD_IDEMPOTENCY_CONFLICT");
                assertThat(loser.body())
                        .doesNotContain(id.toString())
                        .doesNotContain(winner.request().payload().identification())
                        .doesNotContain(winner.request().payload().name())
                        .doesNotContain(winner.request().tenantId().toString())
                        .doesNotContain(winner.request().farmId().toString());

                StoredAnimal stored = singleStored(id);
                assertStored(stored, winner.request().tenantId(), winner.request().farmId(),
                        winner.request().payload());
                assertThat(countById(id)).isEqualTo(1);
                assertVisibleOnlyToWinner(winner.request(), loser.request(), id);
            }
        });
    }

    @Test
    void sameIdentificationInDifferentFarmsCreatesBothAnimals() throws Exception {
        withTwoThreads(executor -> {
            for (int iteration = 0; iteration < ITERATIONS; iteration++) {
                String identification = "E-" + iteration;
                Payload first = new Payload(UUID.randomUUID(), identification, "Animal A1",
                        "FEMALE", null);
                Payload second = new Payload(UUID.randomUUID(), identification, "Animal A2",
                        "MALE", null);

                List<Attempt> attempts = runPair(executor,
                        request(tokenA, tenantA, farmA1, first),
                        request(tokenA, tenantA, farmA2, second));

                assertStatuses(attempts, 201, 201);
                assertStored(singleStored(first.id()), tenantA, farmA1, first);
                assertStored(singleStored(second.id()), tenantA, farmA2, second);
                assertThat(countByNormalizedIdentification(tenantA, farmA1, identification)).isEqualTo(1);
                assertThat(countByNormalizedIdentification(tenantA, farmA2, identification)).isEqualTo(1);
                assertFarmContainsOnly(request(tokenA, tenantA, farmA1, first), first.id());
                assertFarmContainsOnly(request(tokenA, tenantA, farmA2, second), second.id());
            }
        });
    }

    private List<Attempt> runPair(ExecutorService executor, Request first, Request second) throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(2);
        Future<Attempt> firstFuture = executor.submit(() -> perform(first, barrier));
        Future<Attempt> secondFuture = executor.submit(() -> perform(second, barrier));
        return List.of(firstFuture.get(30, TimeUnit.SECONDS), secondFuture.get(30, TimeUnit.SECONDS));
    }

    private Attempt perform(Request request, CyclicBarrier barrier) throws Exception {
        barrier.await(10, TimeUnit.SECONDS);
        String body = objectMapper.writeValueAsString(request.payload());
        MvcResult result = mvc.perform(post("/api/v1/herd/animals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + request.token())
                        .header("X-Organization-Id", request.tenantId())
                        .header("X-Farm-Id", request.farmId())
                        .content(body))
                .andReturn();
        return new Attempt(request, result.getResponse().getStatus(), result.getResponse().getContentAsString());
    }

    private void withTwoThreads(ConcurrentScenario scenario) throws Exception {
        Set<Long> threadIds = ConcurrentHashMap.newKeySet();
        ExecutorService delegate = Executors.newFixedThreadPool(2, task -> new Thread(() -> {
            threadIds.add(Thread.currentThread().threadId());
            task.run();
        }));
        try {
            scenario.execute(delegate);
            assertThat(threadIds).hasSize(2);
        } finally {
            delegate.shutdownNow();
            assertThat(delegate.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private void assertStatuses(List<Attempt> attempts, int... expected) {
        assertThat(attempts).extracting(Attempt::status).containsExactlyInAnyOrder(expected[0], expected[1]);
        assertThat(attempts).allSatisfy(attempt -> assertThat(attempt.status()).isNotIn(500, 503));
    }

    private Attempt withStatus(List<Attempt> attempts, int status) {
        return attempts.stream().filter(attempt -> attempt.status() == status).findFirst().orElseThrow();
    }

    private String code(Attempt attempt) throws Exception {
        return objectMapper.readTree(attempt.body()).path("code").asText();
    }

    private void assertAnimalResponse(Attempt attempt, Payload expected) throws Exception {
        JsonNode body = objectMapper.readTree(attempt.body());
        assertThat(body.path("id").asText()).isEqualTo(expected.id().toString());
        assertThat(body.path("identification").asText()).isEqualTo(normalized(expected.identification()));
        assertThat(body.path("name").isNull() ? null : body.path("name").asText()).isEqualTo(expected.name());
        assertThat(body.path("sex").asText()).isEqualTo(expected.sex());
        assertThat(body.path("birthDate").isNull() ? null : body.path("birthDate").asText())
                .isEqualTo(expected.birthDate() == null ? null : expected.birthDate().toString());
        assertThat(body.path("status").asText()).isEqualTo("ACTIVE");
        assertThat(body.path("version").asLong()).isZero();
    }

    private void assertStored(StoredAnimal actual, UUID tenantId, UUID farmId, Payload expected) {
        assertThat(actual.id()).isEqualTo(expected.id());
        assertThat(actual.tenantId()).isEqualTo(tenantId);
        assertThat(actual.farmId()).isEqualTo(farmId);
        assertThat(actual.identification()).isEqualTo(normalized(expected.identification()));
        assertThat(actual.name()).isEqualTo(expected.name());
        assertThat(actual.sex()).isEqualTo(expected.sex());
        assertThat(actual.birthDate()).isEqualTo(expected.birthDate());
        assertThat(actual.status()).isEqualTo("ACTIVE");
        assertThat(actual.version()).isZero();
        assertThat(actual.updatedAt()).isEqualTo(actual.createdAt());
    }

    private void assertVisibleOnlyToWinner(Request winner, Request loser, UUID expectedId) throws Exception {
        assertFarmContainsOnly(winner, expectedId);
        MvcResult hidden = list(loser);
        assertThat(hidden.getResponse().getStatus()).isEqualTo(200);
        JsonNode body = objectMapper.readTree(hidden.getResponse().getContentAsString());
        assertThat(body.path("totalElements").asLong()).isZero();
        assertThat(body.path("items").isEmpty()).isTrue();
        assertThat(hidden.getResponse().getContentAsString())
                .doesNotContain(expectedId.toString())
                .doesNotContain(winner.payload().identification())
                .doesNotContain(winner.payload().name());
    }

    private void assertFarmContainsOnly(Request request, UUID expectedId) throws Exception {
        MvcResult visible = list(request);
        assertThat(visible.getResponse().getStatus()).isEqualTo(200);
        JsonNode body = objectMapper.readTree(visible.getResponse().getContentAsString());
        assertThat(body.path("totalElements").asLong()).isEqualTo(1);
        assertThat(body.path("items")).hasSize(1);
        assertThat(body.path("items").get(0).path("id").asText()).isEqualTo(expectedId.toString());
    }

    private MvcResult list(Request request) throws Exception {
        return mvc.perform(get("/api/v1/herd/animals")
                        .queryParam("search", normalized(request.payload().identification()))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + request.token())
                        .header("X-Organization-Id", request.tenantId())
                        .header("X-Farm-Id", request.farmId()))
                .andReturn();
    }

    private StoredAnimal singleStored(UUID id) throws SQLException {
        return querySingleStored("select * from app.animals where id = ?", id);
    }

    private StoredAnimal singleStored(String identification, UUID farmId) throws SQLException {
        return querySingleStored("""
                select * from app.animals
                where farm_id = ? and lower(regexp_replace(identification, '(^[[:space:]]+|[[:space:]]+$)', '', 'g')) = lower(?)
                """, farmId, identification);
    }

    private StoredAnimal querySingleStored(String sql, Object... parameters) throws SQLException {
        try (Connection connection = PostgresTestEnvironment.adminConnection();
             var statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < parameters.length; index++) {
                statement.setObject(index + 1, parameters[index]);
            }
            ResultSet result = statement.executeQuery();
            assertThat(result.next()).isTrue();
            StoredAnimal animal = new StoredAnimal(
                    result.getObject("id", UUID.class), result.getObject("tenant_id", UUID.class),
                    result.getObject("farm_id", UUID.class), result.getString("identification"),
                    result.getString("name"), result.getString("sex"),
                    result.getObject("birth_date", LocalDate.class), result.getString("status"),
                    result.getLong("version"), result.getObject("created_at", OffsetDateTime.class),
                    result.getObject("updated_at", OffsetDateTime.class));
            assertThat(result.next()).isFalse();
            return animal;
        }
    }

    private long countById(UUID id) throws SQLException {
        return count("select count(*) from app.animals where id = ?", id);
    }

    private long countByNormalizedIdentification(UUID tenantId, UUID farmId, String identification)
            throws SQLException {
        return count("""
                select count(*) from app.animals
                where tenant_id = ? and farm_id = ?
                  and lower(regexp_replace(identification, '(^[[:space:]]+|[[:space:]]+$)', '', 'g')) = lower(?)
                """, tenantId, farmId, identification);
    }

    private long count(String sql, Object... parameters) throws SQLException {
        try (Connection connection = PostgresTestEnvironment.adminConnection();
             var statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < parameters.length; index++) {
                statement.setObject(index + 1, parameters[index]);
            }
            ResultSet result = statement.executeQuery();
            assertThat(result.next()).isTrue();
            return result.getLong(1);
        }
    }

    private void deleteAnimals(UUID tenantId, UUID farmId) throws SQLException {
        try (Connection connection = PostgresTestEnvironment.adminConnection();
             var eventStatement = connection.prepareStatement("delete from app.animal_events where tenant_id = ? and farm_id = ?");
             var statement = connection.prepareStatement("delete from app.animals where tenant_id = ? and farm_id = ?")) {
            eventStatement.setObject(1, tenantId);
            eventStatement.setObject(2, farmId);
            eventStatement.executeUpdate();
            statement.setObject(1, tenantId);
            statement.setObject(2, farmId);
            assertThat(statement.executeUpdate()).isEqualTo(1);
        }
    }

    private Request request(String token, UUID tenantId, UUID farmId, Payload payload) {
        return new Request(token, tenantId, farmId, payload);
    }

    private static String normalized(String identification) {
        return identification.replaceAll("^[\\x00-\\x20]+|[\\x00-\\x20]+$", "");
    }

    private static String token(UUID userId) throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .audience(AUDIENCE)
                .subject(userId.toString())
                .claim("role", "authenticated")
                .issueTime(new Date())
                .expirationTime(Date.from(Instant.now().plusSeconds(300)))
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        jwt.sign(new MACSigner(HMAC_SECRET.getBytes()));
        return jwt.serialize();
    }

    @FunctionalInterface
    private interface ConcurrentScenario {
        void execute(ExecutorService executor) throws Exception;
    }

    private record Payload(UUID id, String identification, String name, String sex, LocalDate birthDate) {
    }

    private record Request(String token, UUID tenantId, UUID farmId, Payload payload) {
    }

    private record Attempt(Request request, int status, String body) {
    }

    private record StoredAnimal(
            UUID id,
            UUID tenantId,
            UUID farmId,
            String identification,
            String name,
            String sex,
            LocalDate birthDate,
            String status,
            long version,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
    }
}
