package eu.dtc4eu.standardapp.controller;

import eu.dtc4eu.security.AppUserDetails;
import eu.dtc4eu.standardapp.dto.StandardAppDtos.DocumentApplicationDto;
import eu.dtc4eu.standardapp.dto.StandardAppDtos.FacilityHistoryDto;
import eu.dtc4eu.standardapp.dto.StandardAppDtos.SaveDocumentApplicationRequest;
import eu.dtc4eu.standardapp.service.StandardApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Quality-document applications (GPP / CGS / EEN) per facility and cycle, and
 * the accreditation-history view (v0.2 register §6). Writes are for admins
 * and the coordinators who administer the assessment forms.
 */
@RestController
@RequestMapping("/api/v1/facilities/{facilityId}")
@RequiredArgsConstructor
@Tag(name = "Quality documents & accreditation history")
public class StandardApplicationController {

    private static final String ASSESSORS = "hasAnyRole('ADMIN','SUPER_ADMIN','COORDINATOR','AUDIT_COORDINATOR')";

    private final StandardApplicationService service;

    @Operation(summary = "Quality documents applied by the facility in the selected cycle")
    @GetMapping("/document-applications")
    public List<DocumentApplicationDto> list(@PathVariable Long facilityId,
                                             @AuthenticationPrincipal AppUserDetails principal) {
        return service.list(facilityId, principal);
    }

    @PostMapping("/document-applications")
    @PreAuthorize(ASSESSORS)
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentApplicationDto create(@PathVariable Long facilityId,
                                         @Valid @RequestBody SaveDocumentApplicationRequest request,
                                         @AuthenticationPrincipal AppUserDetails actor) {
        return service.create(facilityId, request, actor);
    }

    @PutMapping("/document-applications/{applicationId}")
    @PreAuthorize(ASSESSORS)
    public DocumentApplicationDto update(@PathVariable Long facilityId, @PathVariable Long applicationId,
                                         @Valid @RequestBody SaveDocumentApplicationRequest request,
                                         @AuthenticationPrincipal AppUserDetails actor) {
        return service.update(facilityId, applicationId, request, actor);
    }

    @DeleteMapping("/document-applications/{applicationId}")
    @PreAuthorize(ASSESSORS)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long facilityId, @PathVariable Long applicationId,
                       @AuthenticationPrincipal AppUserDetails actor) {
        service.delete(facilityId, applicationId, actor);
    }

    @Operation(summary = "Accreditation history: cycles in programme, per-cycle EP lists and document applications")
    @GetMapping("/history")
    public FacilityHistoryDto history(@PathVariable Long facilityId,
                                      @AuthenticationPrincipal AppUserDetails principal) {
        return service.history(facilityId, principal);
    }

    /* ---------------- manual excellence points (register §12.4) ---------------- */

    @Operation(summary = "Manual EP labels of the facility across all cycles")
    @GetMapping("/manual-excellence-points")
    public java.util.List<StandardApplicationService.ManualEpDto> manualEps(
            @PathVariable Long facilityId, @AuthenticationPrincipal AppUserDetails principal) {
        return service.manualEps(facilityId, principal);
    }

    public record PutManualEpsRequest(@jakarta.validation.constraints.NotNull Long cycleId,
                                      @jakarta.validation.constraints.NotBlank String questionnaireCode,
                                      @jakarta.validation.constraints.NotNull java.util.List<String> labels) {
    }

    @Operation(summary = "Replace the manual EP labels of one cycle+questionnaire (works on closed cycles)")
    @PreAuthorize(ASSESSORS)
    @PutMapping("/manual-excellence-points")
    public java.util.List<StandardApplicationService.ManualEpDto> putManualEps(
            @PathVariable Long facilityId, @Valid @RequestBody PutManualEpsRequest request,
            @AuthenticationPrincipal AppUserDetails principal) {
        return service.putManualEps(facilityId, request.cycleId(),
                request.questionnaireCode(), request.labels(), principal);
    }

    /* ---------------------- GPP effectiveness (Module 6) ---------------------- */

    @Operation(summary = "Effectiveness snapshot of a GPP document application")
    @GetMapping("/document-applications/{applicationId}/effectiveness")
    public StandardApplicationService.EffectivenessDto effectiveness(
            @PathVariable Long facilityId, @PathVariable Long applicationId,
            @AuthenticationPrincipal AppUserDetails principal) {
        return service.getEffectiveness(facilityId, applicationId, principal);
    }

    public record LinkEffectivenessRequest(@jakarta.validation.constraints.NotNull Long questionnaireItemId) {
    }

    @Operation(summary = "Link the corresponding questionnaire item and snapshot effectiveness levels")
    @PreAuthorize(ASSESSORS)
    @PutMapping("/document-applications/{applicationId}/effectiveness")
    public StandardApplicationService.EffectivenessDto linkEffectiveness(
            @PathVariable Long facilityId, @PathVariable Long applicationId,
            @Valid @RequestBody LinkEffectivenessRequest request,
            @AuthenticationPrincipal AppUserDetails principal) {
        return service.linkEffectiveness(facilityId, applicationId,
                request.questionnaireItemId(), principal);
    }
}
