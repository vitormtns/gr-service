package com.gerenciadorrural.modules.herd.api;

import com.gerenciadorrural.infrastructure.database.PostgresTestEnvironment;
import com.gerenciadorrural.infrastructure.database.SpringPostgresTestSupport;
import com.gerenciadorrural.modules.herd.domain.HerdAnimalProfileRepository;
import com.gerenciadorrural.modules.herd.domain.HerdAnimalSex;
import com.gerenciadorrural.modules.herd.domain.HerdAnimalSummary;
import com.gerenciadorrural.modules.herd.infrastructure.JdbcHerdAnimalProfileRepository;
import com.gerenciadorrural.shared.tenancy.TenantId;
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
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.sql.Connection;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;

@ActiveProfiles("test")
@SpringBootTest(properties = {"app.security.supabase.mode=HMAC", "app.security.supabase.algorithm=HS256", "app.security.supabase.issuer=https://auth.example.test/auth/v1", "app.security.supabase.hmac-secret=test-only-hmac-key-with-at-least-32-bytes", "app.security.supabase.audiences=authenticated", "app.security.supabase.accepted-token-roles=authenticated"})
@AutoConfigureMockMvc
@Import(HerdAnimalCorrectionConcurrentVerticalIntegrationTest.DeterministicLockConfiguration.class)
class HerdAnimalCorrectionConcurrentVerticalIntegrationTest extends SpringPostgresTestSupport {
    private static final String SECRET = "test-only-hmac-key-with-at-least-32-bytes";
    @Autowired MockMvc mvc;
    @Autowired UpdateLockGate updateLockGate;
    private UUID userId, tenantId, farmId;

    @BeforeEach void seed() throws Exception {
        updateLockGate.reset();
        PostgresTestEnvironment.clearUsers(); userId = UUID.randomUUID(); tenantId = UUID.randomUUID(); farmId = UUID.randomUUID(); UUID membership = UUID.randomUUID();
        try (Connection c = PostgresTestEnvironment.adminConnection(); var s = c.prepareStatement("""
                insert into app.users(id,status) values(?,'ACTIVE'); insert into app.organizations(id,name,status) values(?,'Tenant','ACTIVE');
                insert into app.organization_memberships(id,tenant_id,user_id,role_key,status,farm_scope_mode) values(?,?,?,'OWNER','ACTIVE','SELECTED_FARMS');
                insert into app.farms(id,tenant_id,name,status) values(?,?,'Fazenda','ACTIVE'); insert into app.membership_farm_scopes(tenant_id,membership_id,farm_id) values(?,?,?)
                """)) { s.setObject(1,userId);s.setObject(2,tenantId);s.setObject(3,membership);s.setObject(4,tenantId);s.setObject(5,userId);s.setObject(6,farmId);s.setObject(7,tenantId);s.setObject(8,tenantId);s.setObject(9,membership);s.setObject(10,farmId);s.executeUpdate(); }
    }

    @Test void concurrentRealCorrectionsHaveOneWinnerAndOneStaleConflictInEveryIteration() throws Exception {
        for (int i = 0; i < 5; i++) { UUID id = animal("CC-" + i, "Brisa"); List<Response> responses = simultaneous(id, "Brisa A", "Brisa B");
            assertThat(responses).extracting(Response::status).containsExactlyInAnyOrder(200, 409);
            assertThat(responses.stream().filter(response -> response.status() == 409).map(Response::body)).allSatisfy(body -> assertThat(body).contains("HERD_VERSION_CONFLICT"));
            assertState(id, Set.of("Brisa A", "Brisa B"), 1); }
    }

    @Test void concurrentUpdateAndNoOpNeverAcceptsAStaleNoOp() throws Exception {
        for (int i = 0; i < 5; i++) { UUID id = animal("NO-" + i, "Brisa"); List<Response> responses = simultaneous(id, "Aurora", "Brisa");
            assertThat(responses).extracting(Response::status).isSubsetOf(200, 409).contains(200); assertState(id, Set.of("Aurora"), 1); }
    }

    @Test void updateFirstMakesTheStaleNoOpReturnVersionConflict() throws Exception {
        UUID id = animal("NO-DETERMINISTIC", "Brisa"); updateLockGate.arm(); ExecutorService executor = Executors.newFixedThreadPool(2);
        try { Future<Response> update = executor.submit(() -> correct(id, "Aurora")); updateLockGate.awaitUpdateLock(); Future<Response> staleNoOp = executor.submit(() -> correct(id, "Brisa")); updateLockGate.releaseUpdate();
            assertThat(update.get(30, TimeUnit.SECONDS).status()).isEqualTo(200); Response staleResponse = staleNoOp.get(30, TimeUnit.SECONDS);
            assertThat(staleResponse.status()).isEqualTo(409); assertThat(staleResponse.body()).contains("HERD_VERSION_CONFLICT"); assertState(id, Set.of("Aurora"), 1);
        } finally { updateLockGate.releaseUpdate(); executor.shutdownNow(); assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue(); }
    }

    private List<Response> simultaneous(UUID id, String first, String second) throws Exception { ExecutorService executor=Executors.newFixedThreadPool(2); CyclicBarrier barrier=new CyclicBarrier(2); try { Future<Response> a=executor.submit(()->correct(id,first,barrier)); Future<Response> b=executor.submit(()->correct(id,second,barrier)); return List.of(a.get(30,TimeUnit.SECONDS),b.get(30,TimeUnit.SECONDS)); } finally { executor.shutdownNow(); assertThat(executor.awaitTermination(10,TimeUnit.SECONDS)).isTrue(); } }
    private Response correct(UUID id,String name) throws Exception { return response(id,name); }
    private Response correct(UUID id,String name,CyclicBarrier barrier) throws Exception { barrier.await(10,TimeUnit.SECONDS); return response(id,name); }
    private Response response(UUID id,String name) throws Exception { var result=mvc.perform(patch("/api/v1/herd/animals/"+id).contentType("application/json").content("{\"expectedVersion\":0,\"name\":\""+name+"\"}").header(HttpHeaders.AUTHORIZATION,"Bearer "+token()).header("X-Organization-Id",tenantId).header("X-Farm-Id",farmId)).andReturn(); return new Response(result.getResponse().getStatus(),result.getResponse().getContentAsString()); }
    private UUID animal(String identification,String name) throws Exception { UUID id=UUID.randomUUID();try(Connection c=PostgresTestEnvironment.adminConnection();var s=c.prepareStatement("insert into app.animals(id,tenant_id,farm_id,identification,name,sex) values(?,?,?,?,?,'FEMALE')")){s.setObject(1,id);s.setObject(2,tenantId);s.setObject(3,farmId);s.setString(4,identification);s.setString(5,name);s.executeUpdate();}return id; }
    private void assertState(UUID id,Set<String> names,long version) throws Exception {try(Connection c=PostgresTestEnvironment.adminConnection();var s=c.prepareStatement("select name,version from app.animals where id=?")){s.setObject(1,id);var r=s.executeQuery();assertThat(r.next()).isTrue();assertThat(r.getString(1)).isIn(names);assertThat(r.getLong(2)).isEqualTo(version);}}
    private String token() throws Exception { JWTClaimsSet claims=new JWTClaimsSet.Builder().issuer("https://auth.example.test/auth/v1").audience("authenticated").subject(userId.toString()).claim("role","authenticated").issueTime(new Date()).expirationTime(Date.from(Instant.now().plusSeconds(300))).build(); SignedJWT jwt=new SignedJWT(new JWSHeader(JWSAlgorithm.HS256),claims);jwt.sign(new MACSigner(SECRET.getBytes()));return jwt.serialize(); }
    private record Response(int status,String body) { }

    @TestConfiguration static class DeterministicLockConfiguration {
        @Bean UpdateLockGate updateLockGate() { return new UpdateLockGate(); }
        @Bean @Primary HerdAnimalProfileRepository lockGatedHerdAnimalProfileRepository(JdbcHerdAnimalProfileRepository delegate,UpdateLockGate gate) { return new LockGatedHerdAnimalProfileRepository(delegate,gate); }
    }
    static final class UpdateLockGate {
        private volatile CountDownLatch lockAcquired=new CountDownLatch(1), releaseUpdate=new CountDownLatch(1); private final AtomicBoolean armed=new AtomicBoolean(), intercepted=new AtomicBoolean();
        void reset(){armed.set(false);intercepted.set(false);lockAcquired=new CountDownLatch(1);releaseUpdate=new CountDownLatch(1);} void arm(){armed.set(true);} void awaitUpdateLock()throws InterruptedException{assertThat(lockAcquired.await(30,TimeUnit.SECONDS)).as("o UPDATE deve adquirir o SELECT FOR UPDATE").isTrue();} void releaseUpdate(){releaseUpdate.countDown();}
        void afterSelectForUpdate(){if(armed.get()&&intercepted.compareAndSet(false,true)){lockAcquired.countDown();try{if(!releaseUpdate.await(30,TimeUnit.SECONDS))throw new AssertionError("o teste não liberou o UPDATE");}catch(InterruptedException exception){Thread.currentThread().interrupt();throw new AssertionError("UPDATE interrompido",exception);}}}
    }
    static final class LockGatedHerdAnimalProfileRepository implements HerdAnimalProfileRepository {
        private final JdbcHerdAnimalProfileRepository delegate; private final UpdateLockGate gate; LockGatedHerdAnimalProfileRepository(JdbcHerdAnimalProfileRepository delegate,UpdateLockGate gate){this.delegate=delegate;this.gate=gate;}
        @Override public Optional<HerdAnimalSummary> findById(TenantId tenantId,UUID farmId,UUID id){return delegate.findById(tenantId,farmId,id);}
        @Override public Optional<HerdAnimalSummary> findByIdForCorrection(TenantId tenantId,UUID farmId,UUID id){Optional<HerdAnimalSummary> animal=delegate.findByIdForCorrection(tenantId,farmId,id);gate.afterSelectForUpdate();return animal;}
        @Override public Optional<HerdAnimalSummary> update(TenantId tenantId,UUID farmId,UUID id,long expectedVersion,String identification,String name,HerdAnimalSex sex,LocalDate birthDate){return delegate.update(tenantId,farmId,id,expectedVersion,identification,name,sex,birthDate);}
    }
}
