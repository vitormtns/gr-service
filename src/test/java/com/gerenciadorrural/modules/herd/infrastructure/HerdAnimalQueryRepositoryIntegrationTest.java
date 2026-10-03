package com.gerenciadorrural.infrastructure.database;
import com.gerenciadorrural.modules.herd.infrastructure.JdbcHerdAnimalQueryRepository;import com.gerenciadorrural.modules.herd.domain.*;import com.gerenciadorrural.shared.tenancy.*;import com.gerenciadorrural.shared.infrastructure.database.*;import com.zaxxer.hikari.*;import org.junit.jupiter.api.*;import org.springframework.jdbc.core.*;import org.springframework.jdbc.core.namedparam.*;import org.springframework.jdbc.datasource.DataSourceTransactionManager;import org.springframework.transaction.support.TransactionTemplate;import java.util.*;import static org.assertj.core.api.Assertions.*;
class HerdAnimalQueryRepositoryIntegrationTest extends PostgresMigrationTestSupport {HikariDataSource ds;SpringTenantTransactionExecutor tx;JdbcHerdAnimalQueryRepository repo;@BeforeEach void setup()throws Exception{executeAsAdmin("do $$ begin if not exists(select 1 from pg_roles where rolname='herd_runtime') then create role herd_runtime login noinherit password 'herd-test';end if;grant app_api to herd_runtime;end $$");var c=new HikariConfig();c.setJdbcUrl(POSTGRES.getJdbcUrl());c.setUsername("herd_runtime");c.setPassword("herd-test");c.setMaximumPoolSize(2);ds=new HikariDataSource(c);var j=new JdbcTemplate(ds);tx=new SpringTenantTransactionExecutor(new TransactionTemplate(new DataSourceTransactionManager(ds)),new NamedParameterJdbcTemplate(ds),new TransactionalDatabaseRole(j,new DatabaseAccessProperties("app","app_api")));repo=new JdbcHerdAnimalQueryRepository(new NamedParameterJdbcTemplate(ds));}@AfterEach void close(){ds.close();}@Test void isolatesFarmAndTenantAndEscapesLiteralSearch()throws Exception{UUID a=org(),b=org(),a1=farm(a),a2=farm(a),b1=farm(b);animal(a,a1,"A-002","Água 100%", "FEMALE","ACTIVE");animal(a,a1,"A-001","A_B", "MALE","SOLD");animal(a,a2,"A-003","Outro", "MALE","ACTIVE");animal(b,b1,"B-001","D'Água", "FEMALE","ACTIVE");var first=list(a,a1,null,0,1);assertThat(first.items()).extracting(HerdAnimalSummary::identification).containsExactly("A-001");assertThat(first.totalElements()).isEqualTo(2);assertThat(list(a,a1,null,1,1).items()).extracting(HerdAnimalSummary::identification).containsExactly("A-002");assertThat(list(a,a2,null,0,50).totalElements()).isEqualTo(1);assertThat(list(a,b1,null,0,50).totalElements()).isZero();assertThat(list(a,a1,"%",0,50).items()).hasSize(1);assertThat(list(a,a1,"_",0,50).items()).hasSize(1);assertThat(list(a,a1,"água",0,50).items()).hasSize(1);tx.execute(ctx(a,a1),()->{var j=new JdbcTemplate(ds);assertThat(j.queryForObject("select current_user",String.class)).isEqualTo("app_api");assertThat(j.queryForObject("select app.current_tenant_id()",UUID.class)).isEqualTo(a);return null;});}@Test void ageTransitionsArePagedInclusiveAndIsolatedWithCalendarMonthEnds()throws Exception {
 UUID tenant=org(), other=org(), farm=farm(tenant), otherFarm=farm(tenant), foreignFarm=farm(other);
 var reference=java.time.LocalDate.of(2026,4,30);
 for(var f:List.of(farm,otherFarm,foreignFarm)) {
  UUID t=f.equals(foreignFarm)?other:tenant;
  animal(t,f,"END","Fim do m?s","FEMALE","ACTIVE");
  executeAsAdmin("update app.animals set birth_date='2026-01-31' where tenant_id=? and farm_id=?",t,f);
 }
 animal(tenant,farm,"D15",null,"MALE","ACTIVE");
 executeAsAdmin("update app.animals set birth_date='2026-02-15' where tenant_id=? and identification='D15'",tenant);
 animal(tenant,farm,"D16",null,"MALE","ACTIVE");
 executeAsAdmin("update app.animals set birth_date='2026-02-16' where tenant_id=? and identification='D16'",tenant);
 animal(tenant,farm,"SOLD",null,"FEMALE","SOLD");
 executeAsAdmin("update app.animals set birth_date='2026-01-31' where tenant_id=? and identification='SOLD'",tenant);
 animal(tenant,farm,"MATURE",null,"MALE","ACTIVE");
 executeAsAdmin("update app.animals set birth_date='2020-01-01' where tenant_id=? and identification='MATURE'",tenant);
 var ages=new com.gerenciadorrural.modules.herd.infrastructure.JdbcHerdAgeIntelligenceRepository(new NamedParameterJdbcTemplate(ds));
 tx.execute(ctx(tenant,farm),()-> {
  assertThat(ages.transitionCount(new TenantId(tenant),farm,reference,0)).isZero();
  assertThat(ages.transitionCount(new TenantId(tenant),farm,reference,1)).isEqualTo(1);
  assertThat(ages.transitionCount(new TenantId(tenant),farm,reference,14)).isEqualTo(1);
  assertThat(ages.transitionCount(new TenantId(tenant),farm,reference,15)).isEqualTo(2);
  assertThat(ages.transitionCount(new TenantId(tenant),farm,reference,16)).isEqualTo(3);
  var first=ages.transitions(new TenantId(tenant),farm,reference,15,1,0);
  assertThat(first).extracting(com.gerenciadorrural.modules.herd.domain.HerdAgeIntelligenceRepository.Animal::identification).containsExactly("END");
  assertThat(AgeIntelligence.derive(first.getFirst().birthDate(),reference).transitionOn()).isEqualTo(reference.plusDays(1));
  assertThat(ages.transitions(new TenantId(tenant),farm,reference,15,1,1)).extracting(com.gerenciadorrural.modules.herd.domain.HerdAgeIntelligenceRepository.Animal::identification).containsExactly("D15");
  assertThat(ages.transitionCount(new TenantId(other),foreignFarm,reference,15)).isZero();
  assertThat(ages.transitionCount(new TenantId(tenant),foreignFarm,reference,15)).isZero();
  return null;
 });
}
HerdAnimalPage list(UUID t,UUID f,String search,int p,int s){return tx.execute(ctx(t,f),()->repo.list(new TenantId(t),f,new HerdAnimalQuery(search,null,null,p,s)));}UUID org()throws Exception{UUID i=UUID.randomUUID();executeAsAdmin("insert into app.organizations values(?,?,'ACTIVE',current_timestamp,current_timestamp,0)",i,"Org");return i;}UUID farm(UUID t)throws Exception{UUID i=UUID.randomUUID();executeAsAdmin("insert into app.farms(id,tenant_id,name,status) values(?,?,?,'ACTIVE')",i,t,"Farm");return i;}void animal(UUID t,UUID f,String id,String n,String sex,String status)throws Exception{executeAsAdmin("insert into app.animals(id,tenant_id,farm_id,identification,name,sex,status) values(?,?,?,?,?,?,?)",UUID.randomUUID(),t,f,id,n,sex,status);}TenantContext ctx(UUID t,UUID f){return new TenantContext(new TenantId(t),UUID.randomUUID(),f,UUID.randomUUID(),"OWNER","ALL_FARMS");}}
