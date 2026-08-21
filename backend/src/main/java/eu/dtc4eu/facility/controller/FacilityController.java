package eu.dtc4eu.facility.controller;

import eu.dtc4eu.common.web.PageResponse;
import eu.dtc4eu.facility.dto.FacilityDtos.AddMemberRequest;
import eu.dtc4eu.facility.dto.FacilityDtos.CreateFacilityRequest;
import eu.dtc4eu.facility.dto.FacilityDtos.FacilityDto;
import eu.dtc4eu.facility.dto.FacilityDtos.MemberDto;
import eu.dtc4eu.facility.dto.FacilityDtos.ParticipationDto;
import eu.dtc4eu.facility.dto.FacilityDtos.PutQcncRequest;
import eu.dtc4eu.facility.dto.FacilityDtos.PutSagRequest;
import eu.dtc4eu.facility.dto.FacilityDtos.QcncDto;
import eu.dtc4eu.facility.dto.FacilityDtos.SagDto;
import eu.dtc4eu.facility.dto.FacilityDtos.SetParticipationRequest;
import eu.dtc4eu.facility.dto.FacilityDtos.UpdateFacilityRequest;
import eu.dtc4eu.facility.entity.Facility;
import eu.dtc4eu.facility.entity.FacilityCycleParticipation;
import eu.dtc4eu.facility.dto.QcncDtos;
import eu.dtc4eu.facility.entity.QcncAssessment;
import eu.dtc4eu.facility.service.FacilityService;
import eu.dtc4eu.facility.service.QcncStandardsService;
import eu.dtc4eu.security.AppUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Module 1 — Facilities (spec T2). Reads are data-scoped; writes need an OPEN cycle. */
@RestController
@RequestMapping("/api/v1/facilities")
@RequiredArgsConstructor
@Tag(name = "Facilities (Module 1)")
public class FacilityController {

    private final FacilityService facilityService;
    private final QcncStandardsService qcncStandardsService;

    @Operation(summary = "List facilities (filters: q, setting, subtype, country, city, activity-in-cycle, active)")
    @GetMapping
    public PageResponse<FacilityDto> list(@RequestParam(required = false) String q,
                                          @RequestParam(required = false) Facility.FacilityType type,
                                          @RequestParam(required = false) Facility.FacilitySubtype subtype,
                                          @RequestParam(required = false) String country,
                                          @RequestParam(required = false) String city,
                                          @RequestParam(required = false) FacilityCycleParticipation.Activity activity,
                                          @RequestParam(required = false) Boolean active,
                                          @ParameterObject @PageableDefault(size = 20, sort = "facilityCode",
                                                  direction = Sort.Direction.ASC) Pageable pageable,
                                          @AuthenticationPrincipal AppUserDetails principal) {
        return facilityService.list(q, type, subtype, country, city, activity, active, pageable, principal);
    }

    @Operation(summary = "CSV export of the filtered facility list")
    @GetMapping("/export")
    public ResponseEntity<String> exportCsv(@RequestParam(required = false) String q,
                                            @RequestParam(required = false) Facility.FacilityType type,
                                            @RequestParam(required = false) Facility.FacilitySubtype subtype,
                                            @RequestParam(required = false) String country,
                                            @RequestParam(required = false) String city,
                                            @RequestParam(required = false) FacilityCycleParticipation.Activity activity,
                                            @RequestParam(required = false) Boolean active,
                                            @AuthenticationPrincipal AppUserDetails principal) {
        String csv = facilityService.exportCsv(q, type, subtype, country, city, activity, active, principal);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"facilities.csv\"")
                .body(csv);
    }

    @GetMapping("/{id}")
    public FacilityDto get(@PathVariable Long id, @AuthenticationPrincipal AppUserDetails principal) {
        return facilityService.get(id, principal);
    }

    @Operation(summary = "Register a facility (admin)")
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public FacilityDto create(@Valid @RequestBody CreateFacilityRequest request,
                              @AuthenticationPrincipal AppUserDetails actor) {
        return facilityService.create(request, actor);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public FacilityDto update(@PathVariable Long id, @Valid @RequestBody UpdateFacilityRequest request,
                              @AuthenticationPrincipal AppUserDetails actor) {
        return facilityService.update(id, request, actor);
    }

    /* participation — settable by the facility's internal facilitator or admins */

    @GetMapping("/{id}/participation")
    public ParticipationDto getParticipation(@PathVariable Long id,
                                             @AuthenticationPrincipal AppUserDetails principal) {
        return facilityService.getParticipation(id, principal);
    }

    @Operation(summary = "Set this cycle's participation (upsert per facility+cycle)")
    @PostMapping("/{id}/participation")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','INTERNAL_FACILITATOR')")
    public ParticipationDto setParticipation(@PathVariable Long id,
                                             @Valid @RequestBody SetParticipationRequest request,
                                             @AuthenticationPrincipal AppUserDetails actor) {
        return facilityService.setParticipation(id, request, actor);
    }

    /* members */

    @GetMapping("/{id}/members")
    public List<MemberDto> listMembers(@PathVariable Long id, @AuthenticationPrincipal AppUserDetails principal) {
        return facilityService.listMembers(id, principal);
    }

    @Operation(summary = "Record a member (pseudonymous code) in one of the four categories")
    @PostMapping("/{id}/members")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','INTERNAL_FACILITATOR')")
    @ResponseStatus(HttpStatus.CREATED)
    public MemberDto addMember(@PathVariable Long id, @Valid @RequestBody AddMemberRequest request,
                               @AuthenticationPrincipal AppUserDetails actor) {
        return facilityService.addMember(id, request, actor);
    }

    @DeleteMapping("/{id}/members/{memberId}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','INTERNAL_FACILITATOR')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeMember(@PathVariable Long id, @PathVariable Long memberId,
                             @AuthenticationPrincipal AppUserDetails actor) {
        facilityService.removeMember(id, memberId, actor);
    }

    /* set analysis grid */

    @GetMapping("/{id}/set-analysis-grid")
    public List<SagDto> getSags(@PathVariable Long id, @AuthenticationPrincipal AppUserDetails principal) {
        return facilityService.getSags(id, principal);
    }

    @Operation(summary = "Upsert the grid payload for a variant (A–D) in this cycle")
    @PutMapping("/{id}/set-analysis-grid")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','INTERNAL_FACILITATOR')")
    public SagDto putSag(@PathVariable Long id, @Valid @RequestBody PutSagRequest request,
                         @AuthenticationPrincipal AppUserDetails actor) {
        return facilityService.putSag(id, request, actor);
    }

    @Operation(summary = "GAS-Set schema (Section N stripped for non-clinical readers)")
    @GetMapping("/{id}/set-analysis-grid/schema")
    public com.fasterxml.jackson.databind.JsonNode sagSchema(@PathVariable Long id,
            @AuthenticationPrincipal AppUserDetails principal) {
        return facilityService.getSagSchema(id, principal);
    }

    /* qcnc — standards board (expert-feedback C/NC entry over the V11 catalogue) */

    @Operation(summary = "QCNC board: catalogue + the three administrations' C/NC marks + meta lists")
    @GetMapping("/{id}/qcnc-standards")
    public QcncDtos.QcncBoardDto qcncBoard(@PathVariable Long id,
                                           @AuthenticationPrincipal AppUserDetails principal) {
        return qcncStandardsService.getBoard(id, principal);
    }

    @Operation(summary = "Replace one administration's C/NC results; records the administrator")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','COORDINATOR','AUDIT_COORDINATOR','INTERNAL_FACILITATOR')")
    @PutMapping("/{id}/qcnc-standards/{administrationType}")
    public QcncDtos.QcncBoardDto saveQcncResults(@PathVariable Long id,
                                                 @PathVariable QcncAssessment.AdministrationType administrationType,
                                                 @Valid @RequestBody QcncDtos.SaveResultsRequest request,
                                                 @AuthenticationPrincipal AppUserDetails principal) {
        return qcncStandardsService.saveResults(id, administrationType, request, principal);
    }

    /* qcnc */

    @GetMapping("/{id}/qcnc")
    public QcncDto getQcnc(@PathVariable Long id, @AuthenticationPrincipal AppUserDetails principal) {
        return facilityService.getQcnc(id, principal);
    }

    @Operation(summary = "Upsert this cycle's QCNC assessment (answers keyed Q1..Q5)")
    @PutMapping("/{id}/qcnc")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','INTERNAL_FACILITATOR')")
    public QcncDto putQcnc(@PathVariable Long id, @Valid @RequestBody PutQcncRequest request,
                           @AuthenticationPrincipal AppUserDetails actor) {
        return facilityService.putQcnc(id, request, actor);
    }
}
