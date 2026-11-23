package eu.dtc4eu.scoring.controller;

import eu.dtc4eu.audit.AuditService;
import eu.dtc4eu.scoring.dto.ScoringDtos.FacilityScoresDto;
import eu.dtc4eu.scoring.dto.ScoringDtos.SummaryTableDto;
import eu.dtc4eu.scoring.dto.ScoringDtos.TernaBenchmarkRowDto;
import eu.dtc4eu.scoring.dto.ScoringDtos.TernaPopulationDto;
import eu.dtc4eu.scoring.service.FacilityScoringService;
import eu.dtc4eu.scoring.service.TernaAnalyticsService;
import eu.dtc4eu.scoring.service.TernaExportService;
import eu.dtc4eu.security.AppUserDetails;
import eu.dtc4eu.security.CycleContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Facility-cycle computed scores and terna analytics (v0.2 register §3).
 * Reads follow the standard data-scope rule (out of scope → 404); the
 * recompute backfill is for admins and coordinators.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Scores & analytics")
public class ScoringController {

    private final FacilityScoringService facilityScoringService;
    private final TernaAnalyticsService ternaAnalyticsService;
    private final TernaExportService ternaExportService;
    private final CycleContext cycleContext;
    private final AuditService auditService;

    @Operation(summary = "Quant-SPWP / Quant-SAWA grids with effectiveness classifications")
    @GetMapping("/facilities/{facilityId}/scores")
    public FacilityScoresDto scores(@PathVariable Long facilityId, @RequestParam Long questionnaireId,
                                    @AuthenticationPrincipal AppUserDetails principal) {
        return facilityScoringService.getScores(facilityId, questionnaireId, principal);
    }

    @Operation(summary = "Summary Table of qualitative and quantitative community data (register §3.5)")
    @GetMapping("/facilities/{facilityId}/scores/summary")
    public SummaryTableDto summary(@PathVariable Long facilityId, @RequestParam Long questionnaireId,
                                   @AuthenticationPrincipal AppUserDetails principal) {
        return facilityScoringService.getSummary(facilityId, questionnaireId, principal);
    }

    @Operation(summary = "Recompute the facility's derived scores for the selected cycle (backfill/repair)")
    @PostMapping("/facilities/{facilityId}/scores/recompute")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','COORDINATOR')")
    public FacilityScoresDto recompute(@PathVariable Long facilityId, @RequestParam Long questionnaireId,
                                       @AuthenticationPrincipal AppUserDetails principal) {
        facilityScoringService.facilityInScope(facilityId, principal);
        facilityScoringService.recompute(facilityId, cycleContext.requireId(), questionnaireId);
        auditService.success(principal.getId(), "RECOMPUTE_SCORES", "facility", facilityId,
                Map.of("questionnaireId", questionnaireId, "cycleId", cycleContext.requireId()));
        return facilityScoringService.getScores(facilityId, questionnaireId, principal);
    }

    @Operation(summary = "Benchmark Strength Areas of the terna (register §3.4)")
    @GetMapping("/ternas/{ternaId}/analytics/benchmarks")
    public List<TernaBenchmarkRowDto> benchmarks(@PathVariable Long ternaId, @RequestParam Long questionnaireId,
                                                 @AuthenticationPrincipal AppUserDetails principal) {
        return ternaAnalyticsService.benchmarks(ternaId, questionnaireId, principal);
    }

    @Operation(summary = "Terna population table TAB.2: collected questionnaires vs participating subjects")
    @GetMapping("/ternas/{ternaId}/analytics/population")
    public TernaPopulationDto population(@PathVariable Long ternaId,
                                         @RequestParam(required = false) Long questionnaireId,
                                         @AuthenticationPrincipal AppUserDetails principal) {
        return ternaAnalyticsService.population(ternaId, questionnaireId, principal);
    }

    @Operation(summary = "Terna report workbook (REP-02): one sheet per facility with TAB.3, TAB.4, "
            + "the area mean and the benchmark areas, plus TAB.2")
    @GetMapping("/ternas/{ternaId}/analytics/export.xlsx")
    public ResponseEntity<byte[]> exportTerna(@PathVariable Long ternaId, @RequestParam Long questionnaireId,
                                              @AuthenticationPrincipal AppUserDetails principal) {
        TernaExportService.TernaWorkbook workbook = ternaExportService.export(ternaId, questionnaireId, principal);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + workbook.fileName() + "\"")
                .body(workbook.content());
    }
}
