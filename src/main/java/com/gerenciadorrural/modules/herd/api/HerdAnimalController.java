package com.gerenciadorrural.modules.herd.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.gerenciadorrural.modules.herd.application.*;
import com.gerenciadorrural.modules.herd.domain.*;
import com.gerenciadorrural.modules.organizations.api.ResolvedTenantContext;
import com.gerenciadorrural.shared.tenancy.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.*;

@RestController @RequestMapping("/api/v1/herd/animals") public class HerdAnimalController {
 private static final Set<String> ALLOWED=Set.of("search","sex","status","page","size"); private static final long MAX_OFFSET=Integer.MAX_VALUE;
 private final ListCurrentFarmAnimals list; private final CreateCurrentFarmAnimal create;
 public HerdAnimalController(ListCurrentFarmAnimals list,CreateCurrentFarmAnimal create){this.list=list;this.create=create;}
 @GetMapping public ResponseEntity<Response> get(@ResolvedTenantContext TenantContext context,@RequestParam(required=false) String search,@RequestParam(required=false) String sex,@RequestParam(required=false) String status,@RequestParam(required=false) String page,@RequestParam(required=false) String size,HttpServletRequest request){if(!ALLOWED.containsAll(request.getParameterMap().keySet())||request.getParameterMap().values().stream().anyMatch(v->v.length!=1))throw new HerdAnimalQueryException();var result=list.execute(context,query(search,sex,status,page,size));return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Response.from(result));}
 @PostMapping public ResponseEntity<Item> create(@ResolvedTenantContext TenantContext context,@RequestBody CreateRequest request){var result=create.execute(context,new CreateCurrentFarmAnimalCommand(request.id(),request.identification(),request.name(),request.sex(),request.birthDate()));return ResponseEntity.status(result.outcome()==CreateCurrentFarmAnimalResult.Outcome.CREATED?HttpStatus.CREATED:HttpStatus.OK).cacheControl(CacheControl.noStore()).body(Item.from(result.animal()));}
 private HerdAnimalQuery query(String search,String sex,String status,String page,String size){try{String normalized=search==null?null:search.trim();if(normalized!=null&&normalized.isEmpty())normalized=null;if(normalized!=null&&normalized.length()>100)throw new HerdAnimalQueryException();int p=page==null?0:Integer.parseInt(page),s=size==null?50:Integer.parseInt(size);if(p<0||s<1||s>100)throw new HerdAnimalQueryException();long offset=(long)p*s;if(offset>MAX_OFFSET)throw new HerdAnimalQueryException();return new HerdAnimalQuery(normalized,sex==null?null:HerdAnimalSex.valueOf(sex),status==null?null:HerdAnimalStatus.valueOf(status),p,s);}catch(IllegalArgumentException e){throw new HerdAnimalQueryException();}}
 public record Response(List<Item> items,int page,int size,long totalElements,int totalPages){static Response from(HerdAnimalPage p){return new Response(p.items().stream().map(Item::from).toList(),p.page(),p.size(),p.totalElements(),p.totalPages());}}
 public record Item(String id,String identification,String name,String sex,LocalDate birthDate,String status,long version){static Item from(HerdAnimalSummary a){return new Item(a.id().toString(),a.identification(),a.name(),a.sex().name(),a.birthDate(),a.status().name(),a.version());}}
 @JsonIgnoreProperties(ignoreUnknown=false) public record CreateRequest(UUID id,String identification,String name,HerdAnimalSex sex,LocalDate birthDate){}
}
