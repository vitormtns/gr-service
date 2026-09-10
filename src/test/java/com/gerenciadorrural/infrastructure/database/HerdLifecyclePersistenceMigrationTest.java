package com.gerenciadorrural.infrastructure.database;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;
import org.testcontainers.containers.PostgreSQLContainer;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class HerdLifecyclePersistenceMigrationTest extends PostgresMigrationTestSupport {
    @Test
    void exposesStructureCompositeForeignKeyAndOperationScope() throws Exception {
        UUID tenant = tenant("A"), farmA = farm(tenant, "A1"), farmB = farm(tenant, "A2"), animalA = animal(tenant, farmA, "A"), operation = UUID.randomUUID();
        try (Connection connection = adminConnection()) {
            assertThat(columns(connection)).containsExactlyInAnyOrder("id", "tenant_id", "farm_id", "animal_id", "event_type", "operation_id", "actor_user_id", "occurred_on", "recorded_at", "resulting_version", "payload");
            assertThat(primaryKey(connection)).containsExactly("id");
            assertThat(indexes(connection)).contains("animal_events_pkey", "animal_events_tenant_farm_operation_unique", "animal_events_history_idx");
            assertThat(compositeForeignKey(connection)).isTrue();
            try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery("select relrowsecurity,relforcerowsecurity from pg_class where oid='app.animal_events'::regclass")) { assertThat(result.next()).isTrue(); assertThat(result.getBoolean(1)).isTrue(); assertThat(result.getBoolean(2)).isTrue(); }
        }
        insertEvent(tenant, farmA, animalA, "SOLD", operation, 1, "{\"notes\":\"ok\"}");
        PSQLException duplicate = failure(() -> insertEvent(tenant, farmA, animalA, "SOLD", operation, 2, "{}"));
        assertThat(duplicate.getSQLState()).isEqualTo("23505");
        assertThat(duplicate.getServerErrorMessage().getConstraint()).isEqualTo("animal_events_tenant_farm_operation_unique");
        PSQLException mismatch = failure(() -> insertEvent(tenant, farmB, animalA, "CREATED", null, 0, "{}"));
        assertThat(mismatch.getSQLState()).isEqualTo("23503");
        assertThat(mismatch.getServerErrorMessage().getConstraint()).isEqualTo("animal_events_animal_fk");
        UUID tenantB = tenant("B"), farmC = farm(tenantB, "C1"), animalC = animal(tenantB, farmC, "C");
        insertEvent(tenantB, farmC, animalC, "SOLD", operation, 1, "{\"notes\":\"independente\"}");
    }

    @Test
    void backfillsPreexistingAnimalsUsingTheVersioned05dFile() throws Exception {
        try (PostgreSQLContainer<?> database = new PostgreSQLContainer<>("postgres:15.8-alpine")) {
            database.start();
            try (Connection connection = DriverManager.getConnection(database.getJdbcUrl(), database.getUsername(), database.getPassword())) {
                applyUntil(connection, "20260909120000_herd_lifecycle_activity_timeline.sql");
                UUID tenant = UUID.randomUUID(), farm = UUID.randomUUID();
                List<LegacyAnimal> existing = List.of(
                        new LegacyAnimal(UUID.randomUUID(), "ACTIVE", "PRE-ACTIVE", "Nome ACTIVE", "2020-01-02"),
                        new LegacyAnimal(UUID.randomUUID(), "TRANSFERRED", "PRE-TRANSFERRED", "Nome TRANSFERRED", null),
                        new LegacyAnimal(UUID.randomUUID(), "ARCHIVED", "PRE-ARCHIVED", "Nome ARCHIVED", "2018-12-31")
                );
                try (var organization = connection.prepareStatement("insert into app.organizations(id,name,status) values(?,'T','ACTIVE')"); var farmInsert = connection.prepareStatement("insert into app.farms(id,tenant_id,name,status) values(?,?,'F','ACTIVE')"); var animals = connection.prepareStatement("insert into app.animals(id,tenant_id,farm_id,identification,name,sex,birth_date,status,created_at) values(?,?,?,?,?,'FEMALE',?,?,current_timestamp - interval '1 day')")) {
                    organization.setObject(1,tenant); organization.executeUpdate(); farmInsert.setObject(1,farm); farmInsert.setObject(2,tenant); farmInsert.executeUpdate();
                    for (LegacyAnimal animal : existing) { animals.setObject(1,animal.id()); animals.setObject(2,tenant); animals.setObject(3,farm); animals.setString(4,animal.identification()); animals.setString(5,animal.name()); animals.setObject(6,animal.birthDate()==null?null:java.sql.Date.valueOf(animal.birthDate())); animals.setString(7,animal.status()); animals.addBatch(); }
                    animals.executeBatch();
                }
                applyFile(connection, "20260909120000_herd_lifecycle_activity_timeline.sql");
                ObjectMapper mapper = new ObjectMapper();
                for (LegacyAnimal animal : existing) {
                    try (var statement = connection.prepareStatement("select e.tenant_id,e.farm_id,e.animal_id,e.event_type,e.actor_user_id,e.resulting_version,e.recorded_at,a.created_at,e.payload from app.animal_events e join app.animals a on a.id=e.animal_id where e.animal_id=?")) {
                        statement.setObject(1, animal.id()); ResultSet result = statement.executeQuery(); assertThat(result.next()).isTrue(); assertThat(result.getObject(1,UUID.class)).isEqualTo(tenant); assertThat(result.getObject(2,UUID.class)).isEqualTo(farm); assertThat(result.getObject(3,UUID.class)).isEqualTo(animal.id()); assertThat(result.getString(4)).isEqualTo("CREATED"); assertThat(result.getObject(5)).isNull(); assertThat(result.getLong(6)).isZero(); assertThat(result.getObject(7,OffsetDateTime.class)).isEqualTo(result.getObject(8,OffsetDateTime.class)); JsonNode payload=mapper.readTree(result.getString(9)); assertThat(payload.isObject()).isTrue(); assertThat(payload.path("identification").asText()).isEqualTo(animal.identification()); assertThat(payload.path("name").asText()).isEqualTo(animal.name()); assertThat(payload.path("sex").asText()).isEqualTo("FEMALE"); if(animal.birthDate()==null)assertThat(payload.path("birthDate").isMissingNode()).isTrue();else assertThat(payload.path("birthDate").asText()).isEqualTo(animal.birthDate()); assertThat(result.next()).isFalse();
                    }
                }
            }
        }
    }

    @Test
    void enforcesAppendOnlyPrivilegesAndTenantRlsAtRuntime() throws Exception {
        UUID tenantA=tenant("A"), farmA=farm(tenantA,"A"), animalA=animal(tenantA,farmA,"A"), tenantB=tenant("B"), farmB=farm(tenantB,"B"), animalB=animal(tenantB,farmB,"B"), event=UUID.randomUUID();
        insertEvent(tenantA,farmA,animalA,"CREATED",null,0,"{}");
        try(Connection connection=apiConnection()){setTenant(connection,tenantA);assertThat(count(connection,"select count(*) from app.animal_events")).isEqualTo(1);execute(connection,"insert into app.animal_events(id,tenant_id,farm_id,animal_id,event_type,resulting_version,payload) values(?,?,?,?, 'CORRECTED',1,'{}')",event,tenantA,farmA,animalA);connection.commit();}
        UUID rejected=UUID.randomUUID();
        try(Connection connection=apiConnection()){setTenant(connection,tenantB);assertThat(count(connection,"select count(*) from app.animal_events")).isZero();assertThat(org.assertj.core.api.Assertions.catchThrowable(()->execute(connection,"insert into app.animal_events(id,tenant_id,farm_id,animal_id,event_type,resulting_version,payload) values(?,?,?,?, 'CORRECTED',1,'{}')",rejected,tenantA,farmA,animalA))).isInstanceOf(SQLException.class);}
        for(String sql:List.of("update app.animal_events set payload='{}' where id='"+event+"'","delete from app.animal_events where id='"+event+"'","truncate app.animal_events")){try(Connection connection=apiConnection()){setTenant(connection,tenantA);assertThat(org.assertj.core.api.Assertions.catchThrowable(()->execute(connection,sql))).isInstanceOf(SQLException.class);}}
        try(Connection connection=adminConnection()){assertThat(count(connection,"select count(*) from app.animal_events where id='"+event+"'")).isOne();assertThat(count(connection,"select count(*) from app.animal_events where id='"+rejected+"'")).isZero();try(Statement statement=connection.createStatement();ResultSet result=statement.executeQuery("select has_table_privilege('public','app.animal_events','select,insert,update,delete,truncate'),has_any_column_privilege('public','app.animal_events','select,insert,update,references')")){assertThat(result.next()).isTrue();assertThat(result.getBoolean(1)).isFalse();assertThat(result.getBoolean(2)).isFalse();}}
        assertThat(animalB).isNotNull();
    }

    private UUID tenant(String name)throws SQLException{UUID id=UUID.randomUUID();executeAsAdmin("insert into app.organizations(id,name,status) values(?,?,'ACTIVE')",id,name);return id;} private UUID farm(UUID tenant,String name)throws SQLException{UUID id=UUID.randomUUID();executeAsAdmin("insert into app.farms(id,tenant_id,name,status) values(?,?,?,'ACTIVE')",id,tenant,name);return id;} private UUID animal(UUID tenant,UUID farm,String suffix)throws SQLException{UUID id=UUID.randomUUID();executeAsAdmin("insert into app.animals(id,tenant_id,farm_id,identification,sex,status) values(?,?,?,?,?,'ACTIVE')",id,tenant,farm,"E-"+suffix+id,"FEMALE");return id;} private void insertEvent(UUID tenant,UUID farm,UUID animal,String type,UUID operation,long version,String payload)throws SQLException{executeAsAdmin("insert into app.animal_events(id,tenant_id,farm_id,animal_id,event_type,operation_id,resulting_version,payload) values(?,?,?,?,?,?,?,cast(? as jsonb))",UUID.randomUUID(),tenant,farm,animal,type,operation,version,payload);} private static long count(Connection c,String sql)throws SQLException{try(Statement s=c.createStatement();ResultSet r=s.executeQuery(sql)){r.next();return r.getLong(1);}}
    private static List<String> columns(Connection c)throws SQLException{try(Statement s=c.createStatement();ResultSet r=s.executeQuery("select column_name from information_schema.columns where table_schema='app' and table_name='animal_events'")){java.util.ArrayList<String> out=new java.util.ArrayList<>();while(r.next())out.add(r.getString(1));return out;}} private static List<String> primaryKey(Connection c)throws SQLException{try(Statement s=c.createStatement();ResultSet r=s.executeQuery("select a.attname from pg_index i join pg_attribute a on a.attrelid=i.indrelid and a.attnum=any(i.indkey) where i.indrelid='app.animal_events'::regclass and i.indisprimary")){java.util.ArrayList<String> out=new java.util.ArrayList<>();while(r.next())out.add(r.getString(1));return out;}} private static List<String> indexes(Connection c)throws SQLException{try(Statement s=c.createStatement();ResultSet r=s.executeQuery("select indexrelid::regclass::text from pg_index where indrelid='app.animal_events'::regclass")){java.util.ArrayList<String> out=new java.util.ArrayList<>();while(r.next())out.add(r.getString(1).replace("app.",""));return out;}} private static boolean compositeForeignKey(Connection c)throws SQLException{try(Statement s=c.createStatement();ResultSet r=s.executeQuery("select pg_get_constraintdef(oid) from pg_constraint where conname='animal_events_animal_fk'")){r.next();return r.getString(1).contains("FOREIGN KEY (tenant_id, farm_id, animal_id)");}}
    private static PSQLException failure(SqlWork work){Throwable x=org.assertj.core.api.Assertions.catchThrowable(work::run);assertThat(x).isInstanceOf(PSQLException.class);return(PSQLException)x;} private interface SqlWork{void run()throws Exception;} private record LegacyAnimal(UUID id,String status,String identification,String name,String birthDate){} private static void applyUntil(Connection c,String name)throws Exception{for(Path p:migrations()){if(p.getFileName().toString().equals(name))return;apply(c,p);}} private static void applyFile(Connection c,String name)throws Exception{for(Path p:migrations())if(p.getFileName().toString().equals(name)){apply(c,p);return;}throw new IllegalArgumentException(name);} private static List<Path> migrations()throws Exception{try(var paths=Files.list(Path.of("supabase","migrations"))){return paths.filter(p->p.toString().endsWith(".sql")).sorted().toList();}} private static void apply(Connection c,Path p)throws Exception{try(Statement s=c.createStatement()){s.execute(Files.readString(p,StandardCharsets.UTF_8));}}
}
