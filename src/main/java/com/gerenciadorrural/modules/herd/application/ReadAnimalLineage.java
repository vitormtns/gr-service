package com.gerenciadorrural.modules.herd.application;

import com.gerenciadorrural.modules.herd.domain.*;
import com.gerenciadorrural.shared.tenancy.*;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class ReadAnimalLineage {
  private static final Set<String> READ = Set.of("OWNER", "ADMIN", "MANAGER", "OPERATOR", "VIEWER");
  private final TenantTransactionExecutor transactions;
  private final HerdAnimalProfileRepository animals;
  private final AnimalLineageRepository lineage;
  private final java.time.Clock clock;
  public ReadAnimalLineage(TenantTransactionExecutor transactions, HerdAnimalProfileRepository animals,
      AnimalLineageRepository lineage,java.time.Clock clock) {
    this.transactions = transactions; this.animals = animals; this.lineage = lineage;this.clock=clock;
  }
  public Result read(TenantContext context, UUID animalId, int depth, int limit) {
    if (!READ.contains(context.role())) throw new HerdMovementForbiddenException();
    if (animalId == null || depth < 1 || depth > 20 || limit < 1 || limit > 500)
      throw new HerdDashboardQueryInvalidException();
    return transactions.execute(context, () -> {
      java.time.LocalDate reference=java.time.LocalDate.now(clock);
      animals.findById(context.tenantId(), context.farmId(), animalId).orElseThrow(HerdAnimalNotFoundException::new);
      var nodes = lineage.lineage(context.tenantId(), context.farmId(), animalId, depth, limit+1);
      boolean truncated = nodes.size() > limit;
      var visible=List.copyOf(nodes.subList(0, Math.min(limit,nodes.size())));
      boolean depthLimitReached=visible.stream().anyMatch(n -> n.generation()==depth && n.hasFurtherRelations());
      var items=visible.stream().map(n->new Item(n.animalId(),n.identification(),n.name(),n.sex(),
          n.direction(),n.generation(),n.relatedToAnimalId(),n.hasFurtherRelations(),
          AgeIntelligence.derive(n.birthDate(),reference))).toList();
      return new Result(animalId, reference, depth, limit, truncated, depthLimitReached, items);
    });
  }
  public record Item(UUID animalId,String identification,String name,HerdAnimalSex sex,String direction,
      int generation,UUID relatedToAnimalId,boolean hasFurtherRelations,AgeIntelligence age){}
  public record Result(UUID animalId,java.time.LocalDate referenceDate,int depth,int limit,boolean truncated,
      boolean depthLimitReached,List<Item> items) {}
}
