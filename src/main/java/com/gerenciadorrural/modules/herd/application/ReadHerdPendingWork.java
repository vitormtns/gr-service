package com.gerenciadorrural.modules.herd.application;

import com.gerenciadorrural.modules.herd.domain.*;
import com.gerenciadorrural.shared.tenancy.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.time.*; import java.time.temporal.ChronoUnit; import java.util.*;

@Service public class ReadHerdPendingWork {
 private static final Set<String> ROLES=Set.of("OWNER","ADMIN","MANAGER","OPERATOR","VIEWER");
 private final TenantTransactionExecutor tx; private final HerdManagementRepository repo; private final Clock clock; private final int weighingDays, upcomingDays;
 public ReadHerdPendingWork(TenantTransactionExecutor tx,HerdManagementRepository repo,Clock clock,@Value("${herd.pending-work.weighing-due-days:90}") int weighingDays,@Value("${herd.pending-work.calving-upcoming-days:14}") int upcomingDays){this.tx=tx;this.repo=repo;this.clock=clock;if(weighingDays<0||upcomingDays<0)throw new IllegalArgumentException("Pending-work windows must be non-negative");this.weighingDays=weighingDays;this.upcomingDays=upcomingDays;}
 public Page page(TenantContext c,PendingWorkType type,UUID animalId,int page,int size){if(!ROLES.contains(c.role()))throw new HerdMovementForbiddenException();if(page<0||size<1||size>100||(long)page*size>Integer.MAX_VALUE)throw new HerdAnimalCommandInvalidException();LocalDate today=LocalDate.now(clock);return tx.execute(c,()->{var items=repo.pending(c.tenantId(),c.farmId(),today,weighingDays,upcomingDays,type,animalId,size,(long)page*size).stream().map(x->Item.from(x,today)).toList();return new Page(items,page,size,repo.pendingCount(c.tenantId(),c.farmId(),today,weighingDays,upcomingDays,type,animalId));});}
 public record Page(List<Item> items,int page,int size,long totalElements){public int totalPages(){return totalElements==0?0:(int)((totalElements+size-1)/size);}}
 public record Item(PendingWorkType type,UUID animalId,String identification,String name,UUID farmId,LocalDate dueOn,LocalDate expectedOn,Long daysOverdue,Long daysUntil,UUID pregnancyId,HealthTreatmentType treatmentType,LocalDate lastPerformedOn,LocalDate lastWeightOn){static Item from(HerdManagementRepository.Pending x,LocalDate now){Long overdue=x.type()==PendingWorkType.CALVING_OVERDUE||x.type()==PendingWorkType.VACCINATION_DUE||x.type()==PendingWorkType.DEWORMING_DUE?ChronoUnit.DAYS.between(x.date(),now):null;Long until=x.type()==PendingWorkType.CALVING_UPCOMING?ChronoUnit.DAYS.between(now,x.date()):null;return new Item(x.type(),x.animalId(),x.identification(),x.name(),x.farmId(),x.type()==PendingWorkType.CALVING_UPCOMING||x.type()==PendingWorkType.CALVING_OVERDUE?null:x.date(),x.type()==PendingWorkType.CALVING_UPCOMING||x.type()==PendingWorkType.CALVING_OVERDUE?x.date():null,overdue,until,x.pregnancyId(),x.treatmentType(),x.lastPerformedOn(),x.lastWeightOn());}}
}
