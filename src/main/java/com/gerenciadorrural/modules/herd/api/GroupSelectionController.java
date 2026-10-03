package com.gerenciadorrural.modules.herd.api;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.gerenciadorrural.modules.herd.application.*;
import com.gerenciadorrural.modules.organizations.api.ResolvedTenantContext;
import com.gerenciadorrural.shared.tenancy.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/herd/groups")
public class GroupSelectionController {
  private static final ObjectMapper STRICT = new ObjectMapper(JsonFactory.builder()
      .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build());
  private final ManageGroupSelection groups;
  public GroupSelectionController(ManageGroupSelection groups) { this.groups=groups; }
  @PostMapping("/with-animals")
  ResponseEntity<?> create(@ResolvedTenantContext TenantContext context,@RequestBody String body,HttpServletRequest request) {
    noParameters(request);
    var n=object(body,Set.of("operationId","id","name","animalIds"));
    var result=groups.create(context,id(n,"operationId"),id(n,"id"),text(n,"name"),animals(n));
    return ResponseEntity.status(result.replayed()?HttpStatus.OK:HttpStatus.CREATED)
        .cacheControl(CacheControl.noStore()).body(result);
  }
  @PostMapping("/{id}/animals/batch")
  ResponseEntity<?> add(@ResolvedTenantContext TenantContext context,@PathVariable UUID id,@RequestBody String body,HttpServletRequest request) {
    noParameters(request);
    var n=object(body,Set.of("operationId","expectedVersion","animalIds"));
    var v=n.get("expectedVersion");
    if(v==null || !v.isIntegralNumber() || !v.canConvertToLong()) throw new HerdAnimalCommandInvalidException();
    return ResponseEntity.ok().cacheControl(CacheControl.noStore())
        .body(groups.add(context,id(n,"operationId"),id,v.longValue(),animals(n)));
  }
  private static JsonNode object(String body,Set<String> fields) {
    try(var parser=STRICT.getFactory().createParser(body)) {
      JsonNode n=STRICT.readTree(parser);
      if(n==null || !n.isObject() || parser.nextToken()!=null) throw new IllegalArgumentException();
      var names=n.fieldNames(); while(names.hasNext()) if(!fields.contains(names.next())) throw new IllegalArgumentException();
      for(var field:fields) if(!n.hasNonNull(field)) throw new IllegalArgumentException();
      return n;
    } catch(Exception error) { throw new HerdAnimalCommandInvalidException(); }
  }
  private static void noParameters(HttpServletRequest request) {
    if(!request.getParameterMap().isEmpty()) throw new HerdAnimalCommandInvalidException();
  }
  private static String text(JsonNode n,String field) {
    if(!n.path(field).isTextual()) throw new HerdAnimalCommandInvalidException(); return n.get(field).textValue();
  }
  private static UUID id(JsonNode n,String field) {
    try{return UUID.fromString(text(n,field));}catch(IllegalArgumentException error){throw new HerdAnimalCommandInvalidException();}
  }
  private static List<UUID> animals(JsonNode n) {
    if(!n.path("animalIds").isArray()) throw new HerdAnimalCommandInvalidException();
    var result=new ArrayList<UUID>();
    try { for(var a:n.get("animalIds")) { if(!a.isTextual()) throw new IllegalArgumentException(); result.add(UUID.fromString(a.textValue())); } }
    catch(IllegalArgumentException error){throw new HerdAnimalCommandInvalidException();}
    return result;
  }
}
