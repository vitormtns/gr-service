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
import java.time.LocalDate;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@ActiveProfiles("test")
@SpringBootTest(properties = {
        "app.security.supabase.mode=HMAC", "app.security.supabase.algorithm=HS256",
        "app.security.supabase.issuer=https://auth.example.test/auth/v1",
        "app.security.supabase.hmac-secret=test-only-hmac-key-with-at-least-32-bytes",
        "app.security.supabase.audiences=authenticated", "app.security.supabase.accepted-token-roles=authenticated"
})
@AutoConfigureMockMvc
class HerdReproductionConcurrencyIntegrationTest extends SpringPostgresTestSupport {
    private static final String ISSUER = "https://auth.example.test/auth/v1";
    private static final String SECRET = "test-only-hmac-key-with-at-least-32-bytes";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    UUID user, tenant, farm, membership;
    String token;

    @BeforeEach void seed() throws Exception {
        PostgresTestEnvironment.clearUsers();
        user=UUID.randomUUID(); tenant=UUID.randomUUID(); farm=UUID.randomUUID(); membership=UUID.randomUUID();
        try (Connection c=PostgresTestEnvironment.adminConnection(); var s=c.prepareStatement("""
                insert into app.users(id,status) values(?,'ACTIVE');
                insert into app.organizations(id,name,status) values(?,'Organização','ACTIVE');
                insert into app.farms(id,tenant_id,name,status) values(?,?,'Fazenda','ACTIVE');
                insert into app.organization_memberships(id,tenant_id,user_id,role_key,status,farm_scope_mode) values(?,?,?,'OWNER','ACTIVE','SELECTED_FARMS');
                insert into app.membership_farm_scopes(tenant_id,membership_id,farm_id) values(?,?,?)
                """)) { int i=1; s.setObject(i++,user);s.setObject(i++,tenant);s.setObject(i++,farm);s.setObject(i++,tenant);s.setObject(i++,membership);s.setObject(i++,tenant);s.setObject(i++,user);s.setObject(i++,tenant);s.setObject(i++,membership);s.setObject(i,farm);s.executeUpdate(); }
        token=token();
    }

    @Test void duplicateBreedingIsSerializedAcrossFiveRealConcurrentRuns() throws Exception {
        for(int run=0;run<5;run++) {
            UUID mother=createMother(); CyclicBarrier barrier=new CyclicBarrier(2); ExecutorService executor=Executors.newFixedThreadPool(2);
            try {
                Future<Integer> a=executor.submit(()->breed(barrier,mother,UUID.randomUUID()));
                Future<Integer> b=executor.submit(()->breed(barrier,mother,UUID.randomUUID()));
                int sa=a.get(20,TimeUnit.SECONDS), sb=b.get(20,TimeUnit.SECONDS);
                assertThat(java.util.Set.of(sa,sb)).containsExactlyInAnyOrder(201,409);
                assertThat(count("select count(*) from app.animal_pregnancies where mother_animal_id=? and status in ('POSSIBLE','CONFIRMED')",mother)).isOne();
                assertThat(count("select count(*) from app.animal_events where animal_id=? and event_type='BREEDING_RECORDED'",mother)).isOne();
                assertThat(version(mother)).isEqualTo(1);
            } finally { shutdown(executor); }
        }
    }

    @Test void confirmationAndTerminationHaveExactlyOneWinner() throws Exception {
        UUID mother=createMother(), pregnancy=breed(mother); CyclicBarrier barrier=new CyclicBarrier(2); ExecutorService executor=Executors.newFixedThreadPool(2);
        try {
            Future<Integer> confirm=executor.submit(()->transition(barrier,pregnancy,"confirmation",UUID.randomUUID(),null));
            Future<Integer> terminate=executor.submit(()->transition(barrier,pregnancy,"termination",UUID.randomUUID(),"ABORTION"));
            assertThat(java.util.Set.of(confirm.get(20,TimeUnit.SECONDS),terminate.get(20,TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,409);
            assertThat(count("select count(*) from app.animal_events where animal_id=? and event_type in ('PREGNANCY_CONFIRMED','PREGNANCY_ENDED')",mother)).isOne();
            assertThat(pregnancyStatus(pregnancy)).isIn("CONFIRMED","TERMINATED");
        } finally { shutdown(executor); }
    }

    @Test void doubleCalvingReplaysOneOperationAndRejectsDifferentOperations() throws Exception {
        UUID mother=createMother(), pregnancy=breed(mother), operation=UUID.randomUUID(), calf=UUID.randomUUID(); CyclicBarrier sameBarrier=new CyclicBarrier(2); ExecutorService executor=Executors.newFixedThreadPool(2);
        try {
            Future<Integer> first=executor.submit(()->calve(sameBarrier,mother,pregnancy,operation,calf));
            Future<Integer> replay=executor.submit(()->calve(sameBarrier,mother,pregnancy,operation,calf));
            assertThat(first.get(20,TimeUnit.SECONDS)).isEqualTo(201); assertThat(replay.get(20,TimeUnit.SECONDS)).isEqualTo(201);
            assertThat(count("select count(*) from app.animals where id=?",calf)).isOne();
            assertThat(count("select count(*) from app.animal_maternal_relations where calf_animal_id=?",calf)).isOne();
            assertThat(count("select count(*) from app.animal_events where animal_id=? and event_type='CALVED'",mother)).isOne();
        } finally { shutdown(executor); }
    }

    @Test void calvingAndTerminationHaveExactlyOneWinnerWithoutPartialTimeline() throws Exception {
        for (int run = 0; run < 5; run++) {
            UUID mother = createMother();
            UUID pregnancy = breed(mother);
            UUID calf = UUID.randomUUID();
            UUID calvingOperation = UUID.randomUUID();
            UUID terminationOperation = UUID.randomUUID();
            CyclicBarrier barrier = new CyclicBarrier(2);
            ExecutorService executor = Executors.newFixedThreadPool(2);
            try {
                Future<Integer> calving = executor.submit(
                        () -> calve(barrier, mother, pregnancy, calvingOperation, calf));
                Future<Integer> termination = executor.submit(
                        () -> transition(barrier, pregnancy, "termination", terminationOperation, "ABORTION"));
                int calvingStatus = calving.get(20, TimeUnit.SECONDS);
                int terminationStatus = termination.get(20, TimeUnit.SECONDS);
                assertThat(java.util.Set.of(calvingStatus, terminationStatus))
                        .containsExactlyInAnyOrder(409, calvingStatus == 201 ? 201 : 200);

                if (calvingStatus == 201) {
                    assertThat(pregnancyStatus(pregnancy)).isEqualTo("CALVED");
                    assertThat(count("select count(*) from app.animals where id=?", calf)).isOne();
                    assertThat(count("select count(*) from app.animal_maternal_relations where calf_animal_id=?", calf)).isOne();
                    assertThat(count("select count(*) from app.animal_events where operation_id=?", calvingOperation)).isEqualTo(2);
                    assertThat(count("select count(*) from app.animal_events where operation_id=?", terminationOperation)).isZero();
                } else {
                    assertThat(terminationStatus).isEqualTo(200);
                    assertThat(pregnancyStatus(pregnancy)).isEqualTo("TERMINATED");
                    assertThat(count("select count(*) from app.animals where id=?", calf)).isZero();
                    assertThat(count("select count(*) from app.animal_maternal_relations where calf_animal_id=?", calf)).isZero();
                    assertThat(count("select count(*) from app.animal_events where operation_id=?", calvingOperation)).isZero();
                    assertThat(count("select count(*) from app.animal_events where operation_id=?", terminationOperation)).isOne();
                }
                assertThat(count("""
                        select count(*) from app.animal_events
                        where animal_id=? and event_type in ('CALVED','PREGNANCY_ENDED')
                        """, mother)).isOne();
            } finally {
                shutdown(executor);
            }
        }
    }

    @Test void doubleCalvingWithDifferentOperationsCreatesOnlyOneCalfRelationAndTimeline() throws Exception {
        for (int run = 0; run < 5; run++) {
            UUID mother = createMother();
            UUID pregnancy = breed(mother);
            UUID firstOperation = UUID.randomUUID();
            UUID secondOperation = UUID.randomUUID();
            UUID firstCalf = UUID.randomUUID();
            UUID secondCalf = UUID.randomUUID();
            CyclicBarrier barrier = new CyclicBarrier(2);
            ExecutorService executor = Executors.newFixedThreadPool(2);
            try {
                Future<Integer> first = executor.submit(
                        () -> calve(barrier, mother, pregnancy, firstOperation, firstCalf));
                Future<Integer> second = executor.submit(
                        () -> calve(barrier, mother, pregnancy, secondOperation, secondCalf));
                assertThat(java.util.Set.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
                        .containsExactlyInAnyOrder(201, 409);
                assertThat(countEither("select count(*) from app.animals where id in (?,?)", firstCalf, secondCalf)).isOne();
                assertThat(countEither("select count(*) from app.animal_maternal_relations where calf_animal_id in (?,?)", firstCalf, secondCalf)).isOne();
                assertThat(count("select count(*) from app.animal_events where animal_id=? and event_type='CALVED'", mother)).isOne();
                assertThat(countEither("select count(*) from app.animal_events where animal_id in (?,?) and event_type='BORN'", firstCalf, secondCalf)).isOne();
                assertThat(countEither("select count(*) from app.animal_events where operation_id in (?,?) and event_type='CALVED'", firstOperation, secondOperation)).isOne();
                assertThat(pregnancyStatus(pregnancy)).isEqualTo("CALVED");
            } finally {
                shutdown(executor);
            }
        }
    }

    @Test void breedingAndTransitionsReplayWithoutExtraVersionsOrEvents() throws Exception {
        UUID mother=createMother(), breedingOperation=UUID.randomUUID(); String breedingBody="{\"operationId\":\""+breedingOperation+"\",\"expectedVersion\":0,\"serviceType\":\"INSEMINATION\",\"serviceOn\":\""+LocalDate.now().minusDays(2)+"\"}";
        MvcResult initial=request(post("/api/v1/herd/animals/"+mother+"/breedings"),breedingBody); MvcResult replay=request(post("/api/v1/herd/animals/"+mother+"/breedings"),breedingBody);
        assertThat(initial.getResponse().getStatus()).isEqualTo(201); assertThat(replay.getResponse().getStatus()).isEqualTo(201); UUID pregnancy=UUID.fromString(json.readTree(initial.getResponse().getContentAsString()).path("id").asText());
        assertThat(version(mother)).isEqualTo(1); assertThat(count("select count(*) from app.animal_events where animal_id=? and event_type='BREEDING_RECORDED'",mother)).isOne();
        UUID confirmation=UUID.randomUUID(); String confirmationBody="{\"operationId\":\""+confirmation+"\",\"expectedVersion\":0,\"occurredOn\":\""+LocalDate.now().minusDays(1)+"\"}";
        assertThat(request(post("/api/v1/herd/pregnancies/"+pregnancy+"/confirmation"),confirmationBody).getResponse().getStatus()).isEqualTo(200); assertThat(request(post("/api/v1/herd/pregnancies/"+pregnancy+"/confirmation"),confirmationBody).getResponse().getStatus()).isEqualTo(200);
        assertThat(count("select count(*) from app.animal_events where animal_id=? and event_type='PREGNANCY_CONFIRMED'",mother)).isOne();
    }

    @Test void viewerAndOperatorCannotPerformRestrictedMutations() throws Exception {
        UUID mother=createMother(), pregnancy=breed(mother); setRole("VIEWER");
        assertThat(request(post("/api/v1/herd/animals/"+mother+"/breedings"),"{\"operationId\":\""+UUID.randomUUID()+"\",\"expectedVersion\":1,\"serviceType\":\"INSEMINATION\",\"serviceOn\":\""+LocalDate.now().minusDays(1)+"\"}").getResponse().getStatus()).isEqualTo(403);
        setRole("OPERATOR");
        assertThat(request(post("/api/v1/herd/pregnancies/"+pregnancy+"/termination"),"{\"operationId\":\""+UUID.randomUUID()+"\",\"expectedVersion\":0,\"endedOn\":\""+LocalDate.now().minusDays(1)+"\",\"reason\":\"ABORTION\"}").getResponse().getStatus()).isEqualTo(403);
    }

    private int breed(CyclicBarrier barrier, UUID mother, UUID operation) throws Exception { barrier.await(10,TimeUnit.SECONDS); return request(post("/api/v1/herd/animals/"+mother+"/breedings"),"{\"operationId\":\""+operation+"\",\"expectedVersion\":0,\"serviceType\":\"INSEMINATION\",\"serviceOn\":\""+LocalDate.now().minusDays(2)+"\"}").getResponse().getStatus(); }
    private UUID breed(UUID mother) throws Exception { MvcResult r=request(post("/api/v1/herd/animals/"+mother+"/breedings"),"{\"operationId\":\""+UUID.randomUUID()+"\",\"expectedVersion\":0,\"serviceType\":\"INSEMINATION\",\"serviceOn\":\""+LocalDate.now().minusDays(2)+"\"}");assertThat(r.getResponse().getStatus()).isEqualTo(201);return UUID.fromString(json.readTree(r.getResponse().getContentAsString()).path("id").asText()); }
    private int transition(CyclicBarrier barrier, UUID pregnancy, String action, UUID operation, String reason) throws Exception {barrier.await(10,TimeUnit.SECONDS);String extra=reason==null?"":",\"reason\":\""+reason+"\"";return request(post("/api/v1/herd/pregnancies/"+pregnancy+"/"+action),"{\"operationId\":\""+operation+"\",\"expectedVersion\":0,\""+(action.equals("confirmation")?"occurredOn":"endedOn")+"\":\""+LocalDate.now().minusDays(1)+"\""+extra+"}").getResponse().getStatus();}
    private int calve(CyclicBarrier barrier,UUID mother,UUID pregnancy,UUID operation,UUID calf)throws Exception{barrier.await(10,TimeUnit.SECONDS);return calve(mother,pregnancy,operation,calf).getResponse().getStatus();}
    private MvcResult calve(UUID mother,UUID pregnancy,UUID operation,UUID calf)throws Exception{return request(post("/api/v1/herd/animals/"+mother+"/calvings"),"{\"operationId\":\""+operation+"\",\"expectedVersion\":1,\"pregnancyId\":\""+pregnancy+"\",\"expectedPregnancyVersion\":0,\"calvedOn\":\""+LocalDate.now().minusDays(1)+"\",\"calfId\":\""+calf+"\",\"identification\":\"C-"+calf+"\",\"sex\":\"FEMALE\",\"birthDate\":\""+LocalDate.now().minusDays(1)+"\"}");}
    private UUID createMother() throws Exception {UUID id=UUID.randomUUID();assertThat(request(post("/api/v1/herd/animals"),"{\"id\":\""+id+"\",\"identification\":\"M-"+UUID.randomUUID()+"\",\"sex\":\"FEMALE\"}").getResponse().getStatus()).isEqualTo(201);return id;}
    private MvcResult request(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,String body)throws Exception{return mvc.perform(request.contentType(MediaType.APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION,"Bearer "+token).header("X-Organization-Id",tenant).header("X-Farm-Id",farm).content(body)).andReturn();}
    private long count(String sql,UUID id)throws Exception{try(Connection c=PostgresTestEnvironment.adminConnection();var s=c.prepareStatement(sql)){s.setObject(1,id);ResultSet r=s.executeQuery();r.next();return r.getLong(1);}}
    private long countEither(String sql,UUID first,UUID second)throws Exception{try(Connection c=PostgresTestEnvironment.adminConnection();var s=c.prepareStatement(sql)){s.setObject(1,first);s.setObject(2,second);ResultSet r=s.executeQuery();r.next();return r.getLong(1);}}
    private long version(UUID id)throws Exception{try(Connection c=PostgresTestEnvironment.adminConnection();var s=c.prepareStatement("select version from app.animals where id=?")){s.setObject(1,id);ResultSet r=s.executeQuery();r.next();return r.getLong(1);}}
    private String pregnancyStatus(UUID id)throws Exception{try(Connection c=PostgresTestEnvironment.adminConnection();var s=c.prepareStatement("select status from app.animal_pregnancies where id=?")){s.setObject(1,id);ResultSet r=s.executeQuery();r.next();return r.getString(1);}}
    private void setRole(String role)throws Exception{try(Connection c=PostgresTestEnvironment.adminConnection();var s=c.prepareStatement("update app.organization_memberships set role_key=? where id=?")){s.setString(1,role);s.setObject(2,membership);s.executeUpdate();}}
    private static void shutdown(ExecutorService executor)throws Exception{executor.shutdown();if(!executor.awaitTermination(10,TimeUnit.SECONDS)){executor.shutdownNow();assertThat(executor.awaitTermination(10,TimeUnit.SECONDS)).isTrue();}assertThat(executor.isTerminated()).isTrue();}
    private String token()throws Exception{JWTClaimsSet claims=new JWTClaimsSet.Builder().issuer(ISSUER).audience("authenticated").subject(user.toString()).claim("role","authenticated").issueTime(new Date()).expirationTime(Date.from(Instant.now().plusSeconds(300))).build();SignedJWT jwt=new SignedJWT(new JWSHeader(JWSAlgorithm.HS256),claims);jwt.sign(new MACSigner(SECRET.getBytes()));return jwt.serialize();}
}
