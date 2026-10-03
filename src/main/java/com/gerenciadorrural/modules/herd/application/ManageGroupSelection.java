package com.gerenciadorrural.modules.herd.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gerenciadorrural.modules.herd.domain.*;
import com.gerenciadorrural.shared.tenancy.*;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class ManageGroupSelection {
  private static final Set<String> WRITE = Set.of("OWNER", "ADMIN", "MANAGER");
  private final TenantTransactionExecutor transactions;
  private final HerdGroupRepository groups;
  private final HerdGroupService management;
  private final GroupSelectionRepository selections;
  private final ObjectMapper json;
  public ManageGroupSelection(TenantTransactionExecutor transactions, HerdGroupRepository groups,
      HerdGroupService management, GroupSelectionRepository selections, ObjectMapper json) {
    this.transactions=transactions; this.groups=groups; this.management=management;
    this.selections=selections; this.json=json;
  }
  public Result create(TenantContext context, UUID operation, UUID id, String name, List<UUID> animalIds) {
    if(name == null) throw new HerdAnimalCommandInvalidException();
    return run(context,operation,id,null,name.strip(),animalIds);
  }
  public Result add(TenantContext context, UUID operation, UUID id, Long expectedVersion, List<UUID> animalIds) {
    if(expectedVersion == null || expectedVersion < 0) throw new HerdAnimalCommandInvalidException();
    return run(context,operation,id,expectedVersion,null,animalIds);
  }
  private Result run(TenantContext context, UUID operation, UUID id, Long version, String name, List<UUID> animalIds) {
    if(!WRITE.contains(context.role())) throw new HerdGroupForbiddenException();
    if(operation == null || id == null || animalIds == null || animalIds.isEmpty() || animalIds.size()>100
        || animalIds.stream().anyMatch(Objects::isNull) || new HashSet<>(animalIds).size()!=animalIds.size())
      throw new HerdAnimalCommandInvalidException();
    var selected=animalIds.stream().sorted().toList();
    String command=encode(new Command(id,version,name,selected));
    return transactions.execute(context,()->{
      selections.lockOperation(context.tenantId(),context.farmId(),operation);
      var old=selections.receipt(context.tenantId(),context.farmId(),operation);
      if(old.isPresent()) {
        if(!old.get().command().equals(command)) throw new HerdOperationIdempotencyConflictException();
        var receipt=decode(old.get().result());
        return new Result(receipt.group(),receipt.addedCount(),receipt.alreadyMemberCount(),true);
      }
      if(selections.lockSelectedAnimals(context.tenantId(),context.farmId(),selected)!=selected.size())
        throw new HerdGroupNotFoundException();
      HerdGroup group;
      if(name != null) {
        // A criação do agregado e de todos os membros participa da mesma transação.
        if(groups.find(context.tenantId(),context.farmId(),id,true).isPresent()) throw new HerdGroupConflictException();
        group=management.create(context,id,name,HerdGroup.Kind.MANUAL,HerdGroup.Rules.empty()).group();
      } else {
        group=groups.find(context.tenantId(),context.farmId(),id,true).orElseThrow(HerdGroupNotFoundException::new);
        if(group.status()!=HerdGroup.Status.ACTIVE || group.kind()!=HerdGroup.Kind.MANUAL) throw new HerdGroupConflictException();
        if(group.version()!=version) throw new HerdAnimalVersionConflictException();
      }
      int added=selections.addMembers(context.tenantId(),context.farmId(),id,selected);
      if(added>0) group=groups.bumpVersion(context.tenantId(),context.farmId(),id,group.version())
          .orElseThrow(HerdAnimalVersionConflictException::new);
      var result=new Result(group,added,selected.size()-added,false);
      selections.saveReceipt(context.tenantId(),context.farmId(),operation,id,command,encode(result));
      return result;
    });
  }
  private String encode(Object value) {
    try { return json.writeValueAsString(value); }
    catch(Exception error) { throw new IllegalStateException("Não foi possível serializar o recibo de grupo",error); }
  }
  private Result decode(String value) {
    try { return json.readValue(value,Result.class); }
    catch(Exception error) { throw new IllegalStateException("Não foi possível ler o recibo de grupo",error); }
  }
  private record Command(UUID groupId, Long expectedVersion, String name, List<UUID> animalIds) {}
  public record Result(HerdGroup group, int addedCount, int alreadyMemberCount, boolean replayed) {}
}
