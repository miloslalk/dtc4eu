package eu.dtc4eu.qualityaudit.controller;

import eu.dtc4eu.qualityaudit.dto.AuditDtos;
import eu.dtc4eu.qualityaudit.service.QualityAuditService;
import eu.dtc4eu.security.AppUserDetails;
import io.swagger.v3.oas.annotations.Operation;
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

@RestController
@RequestMapping("/api/v1/audits")
@RequiredArgsConstructor
public class QualityAuditController {

    private final QualityAuditService qualityAuditService;

    @Operation(summary = "Audits of the selected cycle, within the caller's scope")
    @GetMapping
    public List<AuditDtos.AuditDto> list(@AuthenticationPrincipal AppUserDetails principal) {
        return qualityAuditService.list(principal);
    }

    @GetMapping("/{id}")
    public AuditDtos.AuditDto get(@PathVariable Long id, @AuthenticationPrincipal AppUserDetails principal) {
        return qualityAuditService.get(id, principal);
    }

    @Operation(summary = "Create an audit (assigned coordinators are auto-granted facility scope)")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AuditDtos.AuditDto create(@Valid @RequestBody AuditDtos.SaveAuditRequest request,
                                     @AuthenticationPrincipal AppUserDetails principal) {
        return qualityAuditService.create(request, principal);
    }

    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    @PutMapping("/{id}")
    public AuditDtos.AuditDto update(@PathVariable Long id, @Valid @RequestBody AuditDtos.SaveAuditRequest request,
                                     @AuthenticationPrincipal AppUserDetails principal) {
        return qualityAuditService.update(id, request, principal);
    }

    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, @AuthenticationPrincipal AppUserDetails principal) {
        qualityAuditService.delete(id, principal);
    }

    @Operation(summary = "Replace accredited outcomes (audit coordinators may edit their own audits)")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','AUDIT_COORDINATOR')")
    @PutMapping("/{id}/outcomes")
    public AuditDtos.AuditDto saveOutcomes(@PathVariable Long id,
                                           @Valid @RequestBody AuditDtos.SaveOutcomesRequest request,
                                           @AuthenticationPrincipal AppUserDetails principal) {
        qualityAuditService.requireOutcomeWriter(id, principal);
        return qualityAuditService.saveOutcomes(id, request, principal);
    }
}
