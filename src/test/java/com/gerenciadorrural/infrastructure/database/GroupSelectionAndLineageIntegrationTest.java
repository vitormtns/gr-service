package com.gerenciadorrural.infrastructure.database;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gerenciadorrural.modules.herd.application.*;
import com.gerenciadorrural.modules.herd.domain.*;
import com.gerenciadorrural.modules.herd.infrastructure.*;
import com.gerenciadorrural.shared.tenancy.*;
import com.gerenciadorrural.shared.infrastructure.database.*;
import com.zaxxer.hikari.*;
import java.time.Clock;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.*;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;

class GroupSelectionAndLineageIntegrationTest extends PostgresMigrationTestSupport {
  HikariDataSource ds;
  SpringTenantTransactionExecutor tx;
  NamedParameterJdbcTemplate jdbc;
  ManageGroupSelection commands;
  UUID tenant, farm, otherFarm, foreignTenant, foreignFarm;
  @BeforeEach void setup() throws Exception {
    executeAsAdmin("do $$ begin if not exists(select 1 from pg_roles where rolname='group_runtime') then create role group_runtime login noinherit password 'test-group';end if;grant app_api to group_runtime;end $$");
    var c=new HikariConfig(); c.setJdbcUrl(POSTGRES.getJdbcUrl()); c.setUsername("group_runtime");
    c.setPassword("test-group"); c.setMaximumPoolSize(2); ds=new HikariDataSource(c);
    jdbc=new NamedParameterJdbcTemplate(ds);
    tx=new SpringTenantTransactionExecutor(new TransactionTemplate(new DataSourceTransactionManager(ds)),
        jdbc,new TransactionalDatabaseRole(new JdbcTemplate(ds),new DatabaseAccessProperties("app","app_api")));
    var groups=new JdbcHerdGroupRepository(jdbc);
    commands=new ManageGroupSelection(tx,groups,new HerdGroupService(tx,groups,Clock.systemUTC()),
        new JdbcGroupSelectionRepository(jdbc),new ObjectMapper().findAndRegisterModules());
    tenant=organization(); foreignTenant=organization(); farm=farm(tenant); otherFarm=farm(tenant); foreignFarm=farm(foreignTenant);
  }
  @AfterEach void close(){ds.close();}

  @Test void groupCreationIsAtomicAndRetriesPreserveReceiptWithNoOpCounts() throws Exception {
    UUID a=animal(tenant,farm,"A"),b=animal(tenant,farm,"B"),foreign=animal(tenant,otherFarm,"OUT");
    UUID op=UUID.randomUUID(),group=UUID.randomUUID();
    var created=commands.create(context(tenant,farm,"OWNER"),op,group,"Seleção",List.of(b,a));
    assertThat(created.addedCount()).isEqualTo(2);
    assertThat(created.group().version()).isEqualTo(1);
    var replay=commands.create(context(tenant,farm,"OWNER"),op,group,"Seleção",List.of(a,b));
    assertThat(replay.replayed()).isTrue();
    assertThat(replay.group()).isEqualTo(created.group());
    assertThatThrownBy(()->commands.create(context(tenant,farm,"OWNER"),op,group,"Outro",List.of(a,b)))
        .isInstanceOf(HerdOperationIdempotencyConflictException.class);
    var noOp=commands.add(context(tenant,farm,"OWNER"),UUID.randomUUID(),group,1L,List.of(a,b));
    assertThat(noOp.addedCount()).isZero();
    assertThat(noOp.alreadyMemberCount()).isEqualTo(2);
    assertThat(noOp.group().version()).isEqualTo(1);
    UUID failedGroup=UUID.randomUUID();
    assertThatThrownBy(()->commands.create(context(tenant,farm,"OWNER"),UUID.randomUUID(),failedGroup,"Inválido",List.of(a,foreign)))
        .isInstanceOf(HerdGroupNotFoundException.class);
    assertThat(tx.execute(context(tenant,farm,"OWNER"),()->new JdbcHerdGroupRepository(jdbc).find(new TenantId(tenant),farm,failedGroup,false))).isEmpty();
    assertThatThrownBy(()->commands.add(context(tenant,farm,"VIEWER"),UUID.randomUUID(),group,1L,List.of(a)))
        .isInstanceOf(HerdGroupForbiddenException.class);
    assertThatThrownBy(()->commands.add(context(tenant,farm,"OWNER"),UUID.randomUUID(),group,0L,List.of(a)))
        .isInstanceOf(HerdAnimalVersionConflictException.class);
    tx.execute(context(foreignTenant,foreignFarm,"OWNER"),()->{
      assertThat(new JdbcGroupSelectionRepository(jdbc).receipt(new TenantId(tenant),farm,op)).isEmpty();
      return null;
    });
  }

  @Test void lineageTraversesGenerationsWithAuthorizedPathsAndCalvesPagination() throws Exception {
    UUID grandma=animal(tenant,farm,"AVÓ"),mother=animal(tenant,farm,"MÃE"),self=animal(tenant,farm,"ATUAL"),
        child=animal(tenant,farm,"CRIA"),grandchild=animal(tenant,farm,"NETA"),out=animal(tenant,otherFarm,"FORA"),
        behind=animal(tenant,farm,"ATRÁS");
    relation(tenant,grandma,mother);relation(tenant,mother,self);relation(tenant,self,child);relation(tenant,child,grandchild);
    relation(tenant,self,out);relation(tenant,out,behind);
    executeAsAdmin("update app.animals set birth_date='2026-01-31' where tenant_id=? and id=?",tenant,child);
    var repo=new JdbcAnimalLineageRepository(jdbc);
    tx.execute(context(tenant,farm,"OWNER"),()->{
      var nodes=repo.lineage(new TenantId(tenant),farm,self,5,100);
      assertThat(nodes).extracting(AnimalLineageRepository.Node::animalId).containsExactlyInAnyOrder(grandma,mother,child,grandchild);
      assertThat(nodes.stream().filter(n->n.animalId().equals(grandma)).findFirst().orElseThrow().generation()).isEqualTo(2);
      assertThat(nodes.stream().filter(n->n.animalId().equals(child)).findFirst().orElseThrow().birthDate())
          .isEqualTo(java.time.LocalDate.of(2026,1,31));
      var limited=repo.lineage(new TenantId(tenant),farm,self,1,100);
      assertThat(limited).hasSize(2).allSatisfy(n->assertThat(n.hasFurtherRelations()).isTrue());
      var calves=new JdbcMaternalRelationRepository(jdbc).currentFarmCalves(new TenantId(tenant),farm,self,1,0);
      assertThat(calves).extracting(HerdAnimalSummary::id).containsExactly(child);
      assertThat(new JdbcMaternalRelationRepository(jdbc).currentFarmCalves(new TenantId(tenant),farm,self,1,1)).isEmpty();
      return null;
    });
    tx.execute(context(foreignTenant,foreignFarm,"OWNER"),()->{
      assertThat(repo.lineage(new TenantId(tenant),farm,self,5,100)).isEmpty(); return null;
    });
  }

  @Test void groupInvalidInputDoesNotWriteAndAllNewTablesForceRls() throws Exception {
    UUID a=animal(tenant,farm,"A");
    for(var ids:List.of(List.<UUID>of(),List.of(a,a),Collections.nCopies(101,a))) {
      assertThatThrownBy(()->commands.create(context(tenant,farm,"OWNER"),UUID.randomUUID(),UUID.randomUUID(),"Lote",ids))
          .isInstanceOf(HerdAnimalCommandInvalidException.class);
    }
    tx.execute(context(tenant,farm,"OWNER"),()->{
      assertThat(jdbc.queryForObject("select relrowsecurity and relforcerowsecurity from pg_class where oid='app.herd_group_operations'::regclass",Map.of(),Boolean.class)).isTrue();
      assertThat(jdbc.queryForObject("select count(*) from app.herd_group_operations where tenant_id=:tenant and farm_id=:farm",Map.of("tenant",tenant,"farm",farm),Long.class)).isZero();
      return null;
    });
  }
  UUID organization()throws Exception{UUID id=UUID.randomUUID();executeAsAdmin("insert into app.organizations(id,name,status) values(?,?,'ACTIVE')",id,"Organização");return id;}
  UUID farm(UUID t)throws Exception{UUID id=UUID.randomUUID();executeAsAdmin("insert into app.farms(id,tenant_id,name,status) values(?,?,?,'ACTIVE')",id,t,"Fazenda");return id;}
  UUID animal(UUID t,UUID f,String label)throws Exception{UUID id=UUID.randomUUID();executeAsAdmin("insert into app.animals(id,tenant_id,farm_id,identification,sex,status) values(?,?,?,?,'FEMALE','ACTIVE')",id,t,f,label);return id;}
  void relation(UUID t,UUID mother,UUID calf)throws Exception{executeAsAdmin("insert into app.animal_maternal_relations(tenant_id,mother_animal_id,calf_animal_id) values(?,?,?)",t,mother,calf);}
  TenantContext context(UUID t,UUID f,String role){return new TenantContext(new TenantId(t),UUID.randomUUID(),f,UUID.randomUUID(),role,"ALL_FARMS");}
}
