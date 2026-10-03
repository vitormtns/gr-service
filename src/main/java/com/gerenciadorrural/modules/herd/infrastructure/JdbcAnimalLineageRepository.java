package com.gerenciadorrural.modules.herd.infrastructure;

import com.gerenciadorrural.modules.herd.domain.*;
import com.gerenciadorrural.shared.tenancy.TenantId;
import java.util.*;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcAnimalLineageRepository implements AnimalLineageRepository {
  private final NamedParameterJdbcTemplate jdbc;
  public JdbcAnimalLineageRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }
  @Override public List<Node> lineage(TenantId tenant, UUID farm, UUID animal, int depth, int limit) {
    return jdbc.query("""
        with recursive visible as (
          select id, identification, name, birth_date, sex from app.animals
          where tenant_id=:tenant and farm_id=:farm
        ), links as (
          select r.mother_animal_id mother, r.calf_animal_id calf
          from app.animal_maternal_relations r
          join visible m on m.id=r.mother_animal_id
          join visible c on c.id=r.calf_animal_id
          where r.tenant_id=:tenant
        ), ancestors(id, related, generation, path) as (
          select mother, calf, 1, ARRAY[calf,mother] from links where calf=:animal
          union all
          select l.mother,l.calf,a.generation+1,a.path||l.mother
          from ancestors a join links l on l.calf=a.id
          where a.generation < :depth and not l.mother=any(a.path)
        ), descendants(id, related, generation, path) as (
          select calf,mother,1,ARRAY[mother,calf] from links where mother=:animal
          union all
          select l.calf,l.mother,d.generation+1,d.path||l.calf
          from descendants d join links l on l.mother=d.id
          where d.generation < :depth and not l.calf=any(d.path)
        ), tree as (
          select id,related,generation,'ANCESTOR' direction from ancestors
          union all
          select id,related,generation,'DESCENDANT' direction from descendants
        )
        select v.*,t.related,t.generation,t.direction,
          case when t.direction='ANCESTOR'
          then exists(select 1 from links l where l.calf=t.id)
          else exists(select 1 from links l where l.mother=t.id) end further
        from tree t join visible v on v.id=t.id
        order by t.direction,t.generation,v.id limit :limit
        """, new MapSqlParameterSource("tenant", tenant.value()).addValue("farm", farm)
        .addValue("animal", animal).addValue("depth", depth).addValue("limit", limit),
        (r, i) -> new Node(r.getObject("id", UUID.class), r.getString("identification"),
            r.getString("name"), r.getObject("birth_date",java.time.LocalDate.class), HerdAnimalSex.valueOf(r.getString("sex")), r.getString("direction"),
            r.getInt("generation"), r.getObject("related", UUID.class),r.getBoolean("further")));
  }
}
