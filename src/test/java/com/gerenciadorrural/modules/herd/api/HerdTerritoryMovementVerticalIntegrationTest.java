package com.gerenciadorrural.modules.herd.api;

import com.fasterxml.jackson.databind.*;
import com.gerenciadorrural.infrastructure.database.*;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

@ActiveProfiles("test") @SpringBootTest(properties={"app.security.supabase.mode=HMAC","app.security.supabase.algorithm=HS256","app.security.supabase.issuer=https://auth.example.test/auth/v1","app.security.supabase.hmac-secret=test-only-hmac-key-with-at-least-32-bytes","app.security.supabase.audiences=authenticated","app.security.supabase.accepted-token-roles=authenticated"}) @AutoConfigureMockMvc
class HerdTerritoryMovementVerticalIntegrationTest extends SpringPostgresTestSupport {
 private static final String ISSUER="https://auth.example.test/auth/v1", SECRET="test-only-hmac-key-with-at-least-32-bytes";
 @Autowired MockMvc mvc; @Autowired ObjectMapper json; UUID user,tenant,farm; String token;
 @BeforeEach void seed() throws Exception {PostgresTestEnvironment.clearUsers();user=UUID.randomUUID();tenant=UUID.randomUUID();farm=UUID.randomUUID();try(Connection c=PostgresTestEnvironment.adminConnection();PreparedStatement s=c.prepareStatement("insert into app.users(id,status) values(?,'ACTIVE');insert into app.organizations(id,name,status) values(?,'T','ACTIVE');insert into app.farms(id,tenant_id,name,status) values(?,?,'F','ACTIVE')")){s.setObject(1,user);s.setObject(2,tenant);s.setObject(3,farm);s.setObject(4,tenant);s.executeUpdate();}membership("OWNER");token=token(user);}
 @Test void managesPaddocksMovesAnimalsInBatchAndPreservesMovementSnapshots() throws Exception {UUID a=paddock("A","A"),b=paddock("B","B"),one=animal("ONE"),two=animal("TWO");UUID op=UUID.randomUUID();assertStatus(post("/api/v1/herd/animals/"+one+"/movements"),"{\"operationId\":\""+op+"\",\"expectedVersion\":0,\"destinationPaddockId\":\""+a+"\",\"occurredOn\":\"2026-09-09\"}",200);assertStatus(post("/api/v1/herd/animals/"+one+"/movements"),"{\"operationId\":\""+op+"\",\"expectedVersion\":0,\"destinationPaddockId\":\""+a+"\",\"occurredOn\":\"2026-09-09\"}",200);assertStatus(post("/api/v1/herd/movements/batch"),"{\"operationId\":\""+UUID.randomUUID()+"\",\"destinationPaddockId\":\""+b+"\",\"occurredOn\":\"2026-09-09\",\"animals\":[{\"animalId\":\""+one+"\",\"expectedVersion\":1},{\"animalId\":\""+two+"\",\"expectedVersion\":0}]}",200);JsonNode occupancy=body(get("/api/v1/herd/paddocks/"+b+"/occupancy"),null);assertThat(occupancy.path("totalAnimals").asInt()).isEqualTo(2);assertStatus(patch("/api/v1/herd/paddocks/"+b),"{\"expectedVersion\":0,\"status\":\"INACTIVE\"}",409);assertStatus(patch("/api/v1/herd/paddocks/"+a),"{\"expectedVersion\":0,\"name\":\"A renomeado\"}",200);JsonNode history=body(get("/api/v1/herd/animals/"+one+"/history?type=MOVED"),null);assertThat(history.path("totalElements").asInt()).isEqualTo(2);assertThat(history.path("items").get(0).path("details").path("sourcePaddockName").asText()).isEqualTo("A");JsonNode log=body(get("/api/v1/herd/paddocks/"+b+"/movements"),null);assertThat(log.path("totalElements").asInt()).isEqualTo(2);}
 private UUID paddock(String name,String code)throws Exception{UUID id=UUID.randomUUID();assertStatus(post("/api/v1/herd/paddocks"),"{\"id\":\""+id+"\",\"name\":\""+name+"\",\"code\":\""+code+"\"}",201);return id;}
 private UUID animal(String prefix)throws Exception{UUID id=UUID.randomUUID();assertStatus(post("/api/v1/herd/animals"),"{\"id\":\""+id+"\",\"identification\":\""+prefix+"-"+id+"\",\"sex\":\"FEMALE\"}",201);return id;}
 private MvcResult request(MockHttpServletRequestBuilder r,String content)throws Exception{r.header(HttpHeaders.AUTHORIZATION,"Bearer "+token).header("X-Organization-Id",tenant).header("X-Farm-Id",farm);if(content!=null)r.contentType(MediaType.APPLICATION_JSON).content(content);return mvc.perform(r).andReturn();}
 private void assertStatus(MockHttpServletRequestBuilder r,String content,int expected)throws Exception{MvcResult x=request(r,content);assertThat(x.getResponse().getStatus()).isEqualTo(expected);}
 private JsonNode body(MockHttpServletRequestBuilder r,String content)throws Exception{MvcResult x=request(r,content);assertThat(x.getResponse().getStatus()).isEqualTo(200);return json.readTree(x.getResponse().getContentAsString());}
 private void membership(String role)throws Exception{UUID m=UUID.randomUUID();try(Connection c=PostgresTestEnvironment.adminConnection();PreparedStatement s=c.prepareStatement("insert into app.organization_memberships(id,tenant_id,user_id,role_key,status,farm_scope_mode) values(?,?,?,?,'ACTIVE','SELECTED_FARMS');insert into app.membership_farm_scopes(tenant_id,membership_id,farm_id) values(?,?,?)")){s.setObject(1,m);s.setObject(2,tenant);s.setObject(3,user);s.setString(4,role);s.setObject(5,tenant);s.setObject(6,m);s.setObject(7,farm);s.executeUpdate();}}
 private static String token(UUID id)throws Exception{JWTClaimsSet c=new JWTClaimsSet.Builder().issuer(ISSUER).audience("authenticated").subject(id.toString()).claim("role","authenticated").issueTime(new java.util.Date()).expirationTime(java.util.Date.from(Instant.now().plusSeconds(300))).build();SignedJWT jwt=new SignedJWT(new JWSHeader(JWSAlgorithm.HS256),c);jwt.sign(new MACSigner(SECRET.getBytes()));return jwt.serialize();}
}
