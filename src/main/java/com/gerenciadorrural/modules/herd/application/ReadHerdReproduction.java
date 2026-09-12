package com.gerenciadorrural.modules.herd.application;

import com.gerenciadorrural.modules.herd.domain.*;
import com.gerenciadorrural.shared.tenancy.TenantContext;
import com.gerenciadorrural.shared.tenancy.TenantTransactionExecutor;
import org.springframework.stereotype.Service;
import java.util.*;

@Service public class ReadHerdReproduction {
 private final TenantTransactionExecutor tx; private final HerdReproductionRepository pregnancies; private final MaternalRelationRepository maternal; private final HerdAnimalProfileRepository animals;
 public ReadHerdReproduction(TenantTransactionExecutor tx,HerdReproductionRepository pregnancies,MaternalRelationRepository maternal,HerdAnimalProfileRepository animals){this.tx=tx;this.pregnancies=pregnancies;this.maternal=maternal;this.animals=animals;}
 public HerdReproductionRepository.Pregnancy pregnancy(TenantContext c,UUID id){return tx.execute(c,()->pregnancies.find(c.tenantId(),c.farmId(),id,false).orElseThrow(HerdAnimalNotFoundException::new));}
 public Page pregnancies(TenantContext c,UUID mother,int page,int size){valid(page,size);return tx.execute(c,()->{required(c,mother);return new Page(pregnancies.list(c.tenantId(),c.farmId(),mother,size,(long)page*size),page,size,pregnancies.count(c.tenantId(),c.farmId(),mother));});}
 public List<HerdAnimalSummary> calves(TenantContext c,UUID mother,int page,int size){valid(page,size);return tx.execute(c,()->{required(c,mother);return maternal.calfIds(c.tenantId(),mother,size,(long)page*size).stream().map(id->animals.findById(c.tenantId(),c.farmId(),id)).flatMap(Optional::stream).toList();});}
 public HerdAnimalSummary mother(TenantContext c,UUID calf){return tx.execute(c,()->{required(c,calf);UUID mother=maternal.motherId(c.tenantId(),calf).orElseThrow(HerdAnimalNotFoundException::new);return animals.findById(c.tenantId(),c.farmId(),mother).orElseThrow(HerdAnimalNotFoundException::new);});}
 private void required(TenantContext c,UUID id){if(id==null||animals.findById(c.tenantId(),c.farmId(),id).isEmpty())throw new HerdAnimalNotFoundException();}
 private static void valid(int p,int s){if(p<0||s<1||s>100)throw new HerdAnimalCommandInvalidException();}
 public record Page(List<HerdReproductionRepository.Pregnancy> items,int page,int size,long totalElements){public int totalPages(){return totalElements==0?0:(int)((totalElements+size-1)/size);}}
}
