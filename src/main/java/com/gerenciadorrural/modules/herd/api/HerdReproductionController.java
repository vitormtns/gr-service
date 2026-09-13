package com.gerenciadorrural.modules.herd.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.gerenciadorrural.modules.herd.application.ManageHerdReproduction;
import com.gerenciadorrural.modules.herd.application.ReadHerdReproduction;
import com.gerenciadorrural.modules.herd.domain.HerdAnimalSex;
import com.gerenciadorrural.modules.herd.domain.HerdReproductionRepository;
import com.gerenciadorrural.modules.herd.domain.PregnancyStatus;
import com.gerenciadorrural.modules.herd.domain.PregnancyTerminationReason;
import com.gerenciadorrural.modules.herd.domain.ReproductionServiceType;
import com.gerenciadorrural.modules.organizations.api.ResolvedTenantContext;
import com.gerenciadorrural.shared.tenancy.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/herd")
public class HerdReproductionController {

    private final ManageHerdReproduction service;
    private final ReadHerdReproduction reads;

    public HerdReproductionController(ManageHerdReproduction service, ReadHerdReproduction reads) {
        this.service = service;
        this.reads = reads;
    }

    @PostMapping("/animals/{motherId}/breedings")
    ResponseEntity<PregnancyItem> breed(@ResolvedTenantContext TenantContext context,
                                        @PathVariable UUID motherId,
                                        @RequestBody BreedingRequest request) {
        var pregnancy = service.breed(context, motherId, new ManageHerdReproduction.Breeding(
                request.operationId(), request.expectedVersion(), request.serviceType(), request.serviceOn(),
                request.sireReference(), request.expectedCalvingOn(), request.notes()));
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(PregnancyItem.from(pregnancy));
    }

    @PostMapping("/animals/{motherId}/calvings")
    ResponseEntity<ManageHerdReproduction.CalvingResult> calve(
            @ResolvedTenantContext TenantContext context,
            @PathVariable UUID motherId,
            @RequestBody CalvingRequest request) {
        var result = service.calve(context, motherId, new ManageHerdReproduction.Calving(
                request.operationId(), request.expectedVersion(), request.pregnancyId(),
                request.expectedPregnancyVersion(), request.calvedOn(), request.calfId(),
                request.identification(), request.name(), request.sex(), request.birthDate()));
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(result);
    }

    @PostMapping("/pregnancies/{id}/confirmation")
    ResponseEntity<PregnancyItem> confirm(@ResolvedTenantContext TenantContext context,
                                          @PathVariable UUID id,
                                          @RequestBody TransitionRequest request) {
        var pregnancy = service.confirm(context, id, new ManageHerdReproduction.Transition(
                request.operationId(), request.expectedVersion(), request.occurredOn()));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(PregnancyItem.from(pregnancy));
    }

    @PostMapping("/pregnancies/{id}/termination")
    ResponseEntity<PregnancyItem> terminate(@ResolvedTenantContext TenantContext context,
                                            @PathVariable UUID id,
                                            @RequestBody TerminationRequest request) {
        var pregnancy = service.terminate(context, id, new ManageHerdReproduction.Transition(
                request.operationId(), request.expectedVersion(), request.endedOn()), request.reason());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(PregnancyItem.from(pregnancy));
    }

    @GetMapping("/pregnancies/{id}")
    ResponseEntity<PregnancyItem> pregnancy(@ResolvedTenantContext TenantContext context,
                                             @PathVariable UUID id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(PregnancyItem.from(reads.pregnancy(context, id)));
    }

    @GetMapping("/animals/{motherId}/pregnancies")
    ResponseEntity<PregnancyPage> pregnancies(@ResolvedTenantContext TenantContext context,
                                               @PathVariable UUID motherId,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int size,
                                               HttpServletRequest request) {
        onlyPaginationParameters(request);
        var result = reads.pregnancies(context, motherId, page, size);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new PregnancyPage(
                result.items().stream().map(PregnancyItem::from).toList(), result.page(), result.size(),
                result.totalElements(), result.totalPages()));
    }

    @GetMapping("/animals/{motherId}/calves")
    ResponseEntity<List<HerdAnimalController.Item>> calves(@ResolvedTenantContext TenantContext context,
                                                            @PathVariable UUID motherId,
                                                            @RequestParam(defaultValue = "0") int page,
                                                            @RequestParam(defaultValue = "20") int size,
                                                            HttpServletRequest request) {
        onlyPaginationParameters(request);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(reads.calves(
                context, motherId, page, size).stream().map(HerdAnimalController.Item::from).toList());
    }

    @GetMapping("/animals/{calfId}/mother")
    ResponseEntity<HerdAnimalController.Item> mother(@ResolvedTenantContext TenantContext context,
                                                      @PathVariable UUID calfId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(HerdAnimalController.Item.from(reads.mother(context, calfId)));
    }

    private static void onlyPaginationParameters(HttpServletRequest request) {
        if (!Set.of("page", "size").containsAll(request.getParameterMap().keySet())
                || request.getParameterMap().values().stream().anyMatch(values -> values.length != 1)) {
            throw new HerdAnimalQueryException();
        }
    }

    public record PregnancyItem(UUID id, UUID motherId, ReproductionServiceType serviceType,
                                LocalDate serviceOn, String sireReference, LocalDate expectedCalvingOn,
                                PregnancyStatus status, LocalDate confirmedOn, LocalDate endedOn,
                                PregnancyTerminationReason terminationReason, UUID calfAnimalId, long version) {
        static PregnancyItem from(HerdReproductionRepository.Pregnancy pregnancy) {
            return new PregnancyItem(pregnancy.id(), pregnancy.motherAnimalId(), pregnancy.serviceType(),
                    pregnancy.serviceOn(), pregnancy.sireReference(), pregnancy.expectedCalvingOn(),
                    pregnancy.status(), pregnancy.confirmedOn(), pregnancy.endedOn(),
                    pregnancy.terminationReason(), pregnancy.calfAnimalId(), pregnancy.version());
        }
    }

    public record PregnancyPage(List<PregnancyItem> items, int page, int size,
                                long totalElements, int totalPages) { }

    @JsonIgnoreProperties(ignoreUnknown = false)
    @JsonDeserialize(using = ReproductionRequestDeserializers.Breeding.class)
    public record BreedingRequest(UUID operationId, long expectedVersion, ReproductionServiceType serviceType,
                                  LocalDate serviceOn, String sireReference, LocalDate expectedCalvingOn,
                                  String notes) { }

    @JsonIgnoreProperties(ignoreUnknown = false)
    @JsonDeserialize(using = ReproductionRequestDeserializers.Calving.class)
    public record CalvingRequest(UUID operationId, long expectedVersion, UUID pregnancyId,
                                 Long expectedPregnancyVersion, LocalDate calvedOn, UUID calfId,
                                 String identification, String name, HerdAnimalSex sex,
                                 LocalDate birthDate) { }

    @JsonIgnoreProperties(ignoreUnknown = false)
    @JsonDeserialize(using = ReproductionRequestDeserializers.Confirmation.class)
    public record TransitionRequest(UUID operationId, long expectedVersion, LocalDate occurredOn) { }

    @JsonIgnoreProperties(ignoreUnknown = false)
    @JsonDeserialize(using = ReproductionRequestDeserializers.Termination.class)
    public record TerminationRequest(UUID operationId, long expectedVersion, LocalDate endedOn,
                                     PregnancyTerminationReason reason) { }
}
