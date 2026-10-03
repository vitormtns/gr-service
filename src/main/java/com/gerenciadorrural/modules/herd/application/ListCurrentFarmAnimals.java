package com.gerenciadorrural.modules.herd.application;
import com.gerenciadorrural.modules.herd.domain.*;
import com.gerenciadorrural.shared.tenancy.*;
import java.util.Objects;
import java.time.*;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
@Service
public class ListCurrentFarmAnimals {
 private final TenantTransactionExecutor transactions;
 private final HerdAnimalQueryRepository repository;
 private final Clock clock;
 @Autowired public ListCurrentFarmAnimals(TenantTransactionExecutor transactions,HerdAnimalQueryRepository repository,Clock clock){this.transactions=transactions;this.repository=repository;this.clock=clock;}
 public ListCurrentFarmAnimals(TenantTransactionExecutor transactions,HerdAnimalQueryRepository repository){this(transactions,repository,Clock.systemUTC());}
 public HerdAnimalPage execute(TenantContext context,HerdAnimalQuery query){
  Objects.requireNonNull(context,"O contexto de tenant é obrigatório");
  Objects.requireNonNull(query,"A consulta é obrigatória");
  LocalDate reference=LocalDate.now(clock);
  return transactions.execute(context,()->{
   var page=repository.list(context.tenantId(),context.farmId(),query);
   return new HerdAnimalPage(page.items().stream().map(a->a.withAgeAt(reference)).toList(),page.page(),page.size(),page.totalElements());
  });
 }
}
