package com.gerenciadorrural.infrastructure.database;

import org.junit.jupiter.api.Test;
import java.sql.SQLException; import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat; import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HerdReadFoundationMigrationTest extends PostgresMigrationTestSupport {
    @Test void constraintsAndCompositeFarmForeignKeyProtectAnimals() throws Exception {
        UUID tenantA=organization(),tenantB=organization(),farmA=farm(tenantA),farmA2=farm(tenantA),farmB=farm(tenantB);
        animal(UUID.randomUUID(),tenantA,farmA," A-1 ","FEMALE","ACTIVE");
        assertThatThrownBy(()->animal(UUID.randomUUID(),tenantA,farmA,"a-1","MALE","ACTIVE")).isInstanceOf(SQLException.class);
        assertThatThrownBy(()->animal(UUID.randomUUID(),tenantA,farmB,"A-2","MALE","ACTIVE")).isInstanceOf(SQLException.class);
        assertThatThrownBy(()->animal(UUID.randomUUID(),tenantA,farmA,"   ","MALE","ACTIVE")).isInstanceOf(SQLException.class);
        assertThatThrownBy(()->animal(UUID.randomUUID(),tenantA,farmA,"A-3","OTHER","ACTIVE")).isInstanceOf(SQLException.class);
        assertThatThrownBy(()->animal(UUID.randomUUID(),tenantA,farmA,"A-4","MALE","INVALID")).isInstanceOf(SQLException.class);
        assertThatThrownBy(()->executeAsAdmin("insert into app.animals(id,tenant_id,farm_id,identification,sex,version) values(?,?,?,?,?,-1)",UUID.randomUUID(),tenantA,farmA,"A-5","MALE")).isInstanceOf(SQLException.class);
        animal(UUID.randomUUID(),tenantA,farmA2,"A-1","MALE","ACTIVE");
    }
    @Test void rlsAndGrantsAreRestricted() throws Exception { try(var c=adminConnection();var s=c.createStatement();var r=s.executeQuery("select relrowsecurity,relforcerowsecurity from pg_class where oid='app.animals'::regclass")){r.next();assertThat(r.getBoolean(1)).isTrue();assertThat(r.getBoolean(2)).isTrue();} assertThatThrownBy(()->executeAsAdmin("set role authenticated; select * from app.animals")).isInstanceOf(SQLException.class); }
    private UUID organization() throws SQLException {UUID id=UUID.randomUUID();executeAsAdmin("insert into app.organizations(id,name,status) values(?,?,'ACTIVE')",id,"Org");return id;}
    private UUID farm(UUID tenant) throws SQLException {UUID id=UUID.randomUUID();executeAsAdmin("insert into app.farms(id,tenant_id,name,status) values(?,?,?,'ACTIVE')",id,tenant,"Farm");return id;}
    private void animal(UUID id,UUID tenant,UUID farm,String identification,String sex,String status)throws SQLException{executeAsAdmin("insert into app.animals(id,tenant_id,farm_id,identification,sex,status) values(?,?,?,?,?,?)",id,tenant,farm,identification,sex,status);}
}
