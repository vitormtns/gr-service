package com.gerenciadorrural.modules.herd.api;

import com.gerenciadorrural.modules.herd.application.*;
import com.gerenciadorrural.modules.organizations.api.ResolvedTenantContext;
import com.gerenciadorrural.shared.tenancy.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/herd")
public class ReproductiveIntelligenceController {
  private final ReadReproductiveIntelligence reads;
  public ReproductiveIntelligenceController(ReadReproductiveIntelligence reads){this.reads=reads;}
  @GetMapping("/animals/{motherId}/reproductive-intelligence")
  ResponseEntity<?> animal(@ResolvedTenantContext TenantContext context,@PathVariable UUID motherId,
      HttpServletRequest request){parameters(request);return ok(reads.animal(context,motherId));}
  @GetMapping("/reproduction/calving-preview")
  ResponseEntity<?> preview(@ResolvedTenantContext TenantContext context,@RequestParam LocalDate serviceOn,
      HttpServletRequest request){parameters(request,"serviceOn");return ok(reads.preview(context,serviceOn));}
  private static void parameters(HttpServletRequest request,String... allowed){
    if(!Set.of(allowed).containsAll(request.getParameterMap().keySet())
        ||request.getParameterMap().values().stream().anyMatch(v->v.length!=1))throw new HerdAnimalQueryException();
  }
  private static ResponseEntity<?> ok(Object result){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(result);}
}
