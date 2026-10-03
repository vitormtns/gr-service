package com.gerenciadorrural.modules.herd.application;

import com.gerenciadorrural.modules.herd.domain.*;
import com.gerenciadorrural.shared.tenancy.*;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ReadReproductiveIntelligence {
  private final TenantTransactionExecutor transactions;
  private final HerdReproductionRepository reproduction;
  private final HerdAnimalProfileRepository animals;
  private final Clock clock;
  private final int reviewAfterDays;
  public ReadReproductiveIntelligence(TenantTransactionExecutor transactions,
      HerdReproductionRepository reproduction,HerdAnimalProfileRepository animals,Clock clock,
      @Value("${herd.reproduction.postpartum-review-days:45}") int reviewAfterDays) {
    if(reviewAfterDays<1||reviewAfterDays>365)throw new IllegalArgumentException("O prazo de acompanhamento pós-parto é inválido");
    this.transactions=transactions;this.reproduction=reproduction;this.animals=animals;
    this.clock=clock;this.reviewAfterDays=reviewAfterDays;
  }
  private static void authorize(TenantContext context) {
    if(!Set.of("OWNER","ADMIN","MANAGER","OPERATOR","VIEWER").contains(context.role()))
      throw new HerdMovementForbiddenException();
  }
  public Result animal(TenantContext context,UUID motherId) {
    authorize(context);LocalDate reference=LocalDate.now(clock);
    return transactions.execute(context,()->{
      var mother=animals.findById(context.tenantId(),context.farmId(),motherId)
          .orElseThrow(HerdAnimalNotFoundException::new);
      if(mother.sex()!=HerdAnimalSex.FEMALE)
        return new Result(reference,null,null,null);
      var open=reproduction.findOpenForMother(context.tenantId(),context.farmId(),motherId);
      var calving=mother.status()==HerdAnimalStatus.ACTIVE
          ? open.map(p->ReproductiveIntelligencePolicy.calving(p.expectedCalvingOn(),reference)).orElse(null):null;
      var postpartum=mother.status()!=HerdAnimalStatus.ACTIVE?null:reproduction.latestCalvingOn(context.tenantId(),context.farmId(),motherId)
          .filter(date->!date.isAfter(reference))
          .map(date->ReproductiveIntelligencePolicy.postpartum(date,reference,reviewAfterDays)).orElse(null);
      return new Result(reference,open.map(HerdReproductionRepository.Pregnancy::id).orElse(null),calving,postpartum);
    });
  }
  public Preview preview(TenantContext context,LocalDate serviceOn) {
    authorize(context);
    if(serviceOn==null||serviceOn.isAfter(LocalDate.now(clock)))throw new HerdAnimalCommandInvalidException();
    return new Preview(serviceOn,ReproductionPolicy.expectedCalvingOn(serviceOn),
        ReproductionPolicy.GESTATION_DAYS,"RECORDED_SERVICE_PLUS_CALENDAR_DAYS");
  }
  public record Result(LocalDate referenceDate,UUID openPregnancyId,
      ReproductiveIntelligencePolicy.CalvingAttention calving,
      ReproductiveIntelligencePolicy.Postpartum postpartum) {}
  public record Preview(LocalDate serviceOn,LocalDate expectedCalvingOn,int gestationDays,String policy) {}
}
