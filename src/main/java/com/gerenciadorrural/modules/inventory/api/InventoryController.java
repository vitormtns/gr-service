package com.gerenciadorrural.modules.inventory.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.gerenciadorrural.modules.inventory.application.InventoryService;
import com.gerenciadorrural.modules.inventory.domain.InventoryModels.*;
import com.gerenciadorrural.modules.organizations.api.ResolvedTenantContext;
import com.gerenciadorrural.shared.tenancy.TenantContext;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@RestController @RequestMapping("/api/v1/inventory") public class InventoryController {
 private final InventoryService service; public InventoryController(InventoryService service){this.service=service;}
 @PostMapping("/products") ResponseEntity<?> product(@ResolvedTenantContext TenantContext c,@RequestBody ProductCreate r){return ResponseEntity.status(HttpStatus.CREATED).body(service.createProduct(c,r.id,r.name,r.code,r.category,r.baseUnit));}
 @GetMapping("/products") List<Product> products(@ResolvedTenantContext TenantContext c){return service.products(c);}
 @GetMapping("/products/{id}") Product product(@ResolvedTenantContext TenantContext c,@PathVariable UUID id){return service.product(c,id);}
 @PatchMapping("/products/{id}") Product productPatch(@ResolvedTenantContext TenantContext c,@PathVariable UUID id,@RequestBody ProductPatch r){return service.patchProduct(c,id,r.expectedVersion(),r.name(),r.hasName(),r.code(),r.hasCode(),r.category(),r.hasCategory(),r.status(),r.hasStatus());}
 @PostMapping("/locations") ResponseEntity<?> location(@ResolvedTenantContext TenantContext c,@RequestBody LocationCreate r){return ResponseEntity.status(HttpStatus.CREATED).body(service.createLocation(c,r.id,r.name,r.code));}
 @GetMapping("/locations") List<Location> locations(@ResolvedTenantContext TenantContext c){return service.locations(c);}
 @GetMapping("/locations/{id}") Location location(@ResolvedTenantContext TenantContext c,@PathVariable UUID id){return service.location(c,id);}
 @PatchMapping("/locations/{id}") Location locationPatch(@ResolvedTenantContext TenantContext c,@PathVariable UUID id,@RequestBody LocationPatch r){return service.patchLocation(c,id,r.expectedVersion(),r.name(),r.hasName(),r.code(),r.hasCode(),r.status(),r.hasStatus());}
 @PostMapping("/movements") Movement move(@ResolvedTenantContext TenantContext c,@RequestBody MovementRequest r){return service.move(c,r.operationId,r.type,r.productId,r.sourceLocationId,r.destinationLocationId,r.quantity,r.occurredOn,r.notes);}
 @GetMapping("/stock") InventoryService.Page<Balance> stock(@ResolvedTenantContext TenantContext c,@RequestParam(required=false)UUID productId,@RequestParam(required=false)UUID locationId,@RequestParam(required=false)String search,@RequestParam(defaultValue="false")boolean onlyPositive,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="50")int size){page(page,size);return service.stock(c,locationId,productId,search,onlyPositive,page,size);}
 @GetMapping("/locations/{id}/stock") InventoryService.Page<Balance> locationStock(@ResolvedTenantContext TenantContext c,@PathVariable UUID id,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="50")int size){page(page,size);service.location(c,id);return service.stock(c,id,null,null,false,page,size);}
 @GetMapping("/movements") InventoryService.Page<Movement> movements(@ResolvedTenantContext TenantContext c,@RequestParam(required=false)Type type,@RequestParam(required=false)UUID productId,@RequestParam(required=false)UUID locationId,@RequestParam(required=false)LocalDate from,@RequestParam(required=false)LocalDate to,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="50")int size){page(page,size);if(from!=null&&to!=null&&from.isAfter(to))throw new InventoryService.Invalid();return service.history(c,type,productId,locationId,from,to,page,size);}
 @GetMapping("/movements/{id}") Movement movement(@ResolvedTenantContext TenantContext c,@PathVariable UUID id){return service.movement(c,id);} private static void page(int p,int s){if(p<0||s<1||s>100)throw new InventoryService.Invalid();}
 @JsonIgnoreProperties(ignoreUnknown=false) @JsonDeserialize(using=InventoryProductCreateRequestDeserializer.class) public static class ProductCreate {public UUID id;public String name,code,category;public Unit baseUnit;}
 @JsonIgnoreProperties(ignoreUnknown=false) @JsonDeserialize(using=InventoryProductPatchRequestDeserializer.class) public static class ProductPatch {private Long expectedVersion;private String name,code,category;private Status status;private boolean hasName,hasCode,hasCategory,hasStatus;public Long expectedVersion(){return expectedVersion;}public String name(){return name;}public String code(){return code;}public String category(){return category;}public Status status(){return status;}public boolean hasName(){return hasName;}public boolean hasCode(){return hasCode;}public boolean hasCategory(){return hasCategory;}public boolean hasStatus(){return hasStatus;}void expectedVersion(Long x){expectedVersion=x;}void name(String x){name=x;hasName=true;}void code(String x){code=x;hasCode=true;}void category(String x){category=x;hasCategory=true;}void status(Status x){status=x;hasStatus=true;}}
 @JsonIgnoreProperties(ignoreUnknown=false) @JsonDeserialize(using=InventoryLocationCreateRequestDeserializer.class) public static class LocationCreate {public UUID id;public String name,code;}
 @JsonIgnoreProperties(ignoreUnknown=false) @JsonDeserialize(using=InventoryLocationPatchRequestDeserializer.class) public static class LocationPatch {private Long expectedVersion;private String name,code;private Status status;private boolean hasName,hasCode,hasStatus;public Long expectedVersion(){return expectedVersion;}public String name(){return name;}public String code(){return code;}public Status status(){return status;}public boolean hasName(){return hasName;}public boolean hasCode(){return hasCode;}public boolean hasStatus(){return hasStatus;}void expectedVersion(Long x){expectedVersion=x;}void name(String x){name=x;hasName=true;}void code(String x){code=x;hasCode=true;}void status(Status x){status=x;hasStatus=true;}}
 @JsonIgnoreProperties(ignoreUnknown=false) @JsonDeserialize(using=InventoryMovementRequestDeserializer.class) public static class MovementRequest {public UUID operationId,productId,sourceLocationId,destinationLocationId;public Type type;public BigDecimal quantity;public LocalDate occurredOn;public String notes;}
}
