package com.gerenciadorrural.infrastructure.database;

import com.gerenciadorrural.modules.farms.infrastructure.JdbcFarmProfileQueryRepository;
import com.gerenciadorrural.modules.farms.domain.FarmProfile;
import com.gerenciadorrural.shared.infrastructure.database.*;
import com.gerenciadorrural.shared.tenancy.*;
import com.zaxxer.hikari.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.*;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.sql.*; import java.util.*;
import static org.assertj.core.api.Assertions.*;

class FarmProfileQueryRepositoryIntegrationTest extends PostgresMigrationTestSupport {
 private HikariDataSource ds; private SpringTenantTransactionExecutor tx; private JdbcFarmProfileQueryRepository repo;
 @BeforeEach void setup() throws Exception {try(Connection c=adminConnection();Statement s=c.createStatement()){s.execute("do $$ begin if not exists(select 1 from pg_roles where rolname='farm_profile_runtime') then create role farm_profile_runtime login noinherit nosuperuser nocreatedb nocreaterole noreplication nobypassrls password 'farm-profile-test'; end if; grant app_api to farm_profile_runtime; end $$");}var cfg=new HikariConfig();cfg.setJdbcUrl(POSTGRES.getJdbcUrl());cfg.setUsername("farm_profile_runtime");cfg.setPassword("farm-profile-test");cfg.setMaximumPoolSize(1);cfg.setMinimumIdle(0);ds=new HikariDataSource(cfg);var jdbc=new JdbcTemplate(ds);tx=new SpringTenantTransactionExecutor(new TransactionTemplate(new DataSourceTransactionManager(ds)),new NamedParameterJdbcTemplate(ds),new TransactionalDatabaseRole(jdbc,new DatabaseAccessProperties("app","app_api")));repo=new JdbcFarmProfileQueryRepository(new NamedParameterJdbcTemplate(ds));}
 @AfterEach void close(){if(ds!=null)ds.close();}
 @Test void returnsOnlyActiveFarmFromItsTenantAndClearsTransactionState() throws Exception {UUID a=UUID.randomUUID(),b=UUID.randomUUID();UUID a1=farm(a,"A1","ACTIVE"),a2=farm(a,"A2","ACTIVE"),inactive=farm(a,"A3","INACTIVE"),archived=farm(a,"A4","ARCHIVED"),b1=farm(b,"B1","ACTIVE");
  assertThat(find(a,a1).orElseThrow()).extracting(FarmProfile::id,FarmProfile::organizationId,FarmProfile::name,FarmProfile::status).containsExactly(a1,new TenantId(a),"A1","ACTIVE");
  assertThat(find(a,a2)).isPresent();assertThat(find(b,b1)).isPresent();assertThat(find(a,b1)).isEmpty();assertThat(find(b,a1)).isEmpty();assertThat(find(a,inactive)).isEmpty();assertThat(find(a,archived)).isEmpty();assertThat(find(a,UUID.randomUUID())).isEmpty();assertThat(find(UUID.randomUUID(),a1)).isEmpty();
  TenantContext executionContext=context(a,a1);tx.execute(executionContext,()->{var jdbc=new JdbcTemplate(ds);assertThat(jdbc.queryForObject("select app.current_user_id()",UUID.class)).isEqualTo(executionContext.userId());assertThat(jdbc.queryForObject("select app.current_tenant_id()",UUID.class)).isEqualTo(a);assertThat(jdbc.queryForObject("select current_user",String.class)).isEqualTo("app_api");assertThat(jdbc.queryForObject("select pg_backend_pid()",Integer.class)).isNotNull();return null;});
  try(Connection c=ds.getConnection();Statement s=c.createStatement();var r=s.executeQuery("select current_user,current_setting('app.current_user_id',true),current_setting('app.current_tenant_id',true)")){r.next();assertThat(r.getString(1)).isEqualTo("farm_profile_runtime");assertThat(r.getString(2)).isNullOrEmpty();assertThat(r.getString(3)).isNullOrEmpty();}
  try(Connection c=ds.getConnection();Statement s=c.createStatement()){assertThatThrownBy(()->s.executeQuery("select id from app.farms")).isInstanceOf(SQLException.class);}
  try(Connection c=adminConnection();Statement s=c.createStatement();var r=s.executeQuery("select rolbypassrls from pg_roles where rolname='app_api'")){r.next();assertThat(r.getBoolean(1)).isFalse();}
  try(Connection c=adminConnection();Statement s=c.createStatement();var r=s.executeQuery("select relrowsecurity,relforcerowsecurity from pg_class where oid='app.farms'::regclass")){r.next();assertThat(r.getBoolean(1)).isTrue();assertThat(r.getBoolean(2)).isTrue();}
 }
 private Optional<FarmProfile> find(UUID tenant,UUID farm){return tx.execute(context(tenant,farm),()->repo.findCurrent(new TenantId(tenant),farm));}
 private UUID farm(UUID tenant,String name,String status)throws Exception{UUID id=UUID.randomUUID();try{executeAsAdmin("insert into app.organizations(id,name,status) values(?,?,'ACTIVE')",tenant,"Org"+tenant);}catch(Exception ignored){}executeAsAdmin("insert into app.farms(id,tenant_id,name,status) values(?,?,?,?)",id,tenant,name,status);return id;}
 private static TenantContext context(UUID tenant,UUID farm){return new TenantContext(new TenantId(tenant),UUID.randomUUID(),farm,UUID.randomUUID(),"OWNER","ALL_FARMS");}
}
