package eu.dtc4eu.questionnaire.controller;

import eu.dtc4eu.common.web.PageResponse;
import eu.dtc4eu.questionnaire.dto.ResponseDtos.AreaScoreDto;
import eu.dtc4eu.questionnaire.dto.ResponseDtos.CreateResponseRequest;
import eu.dtc4eu.questionnaire.dto.ResponseDtos.ResponseDetailDto;
import eu.dtc4eu.questionnaire.dto.ResponseDtos.ResponseRowDto;
import eu.dtc4eu.questionnaire.dto.ResponseDtos.SaveItemsRequest;
import eu.dtc4eu.questionnaire.dto.ResponseDtos.SaveItemsResult;
import eu.dtc4eu.questionnaire.dto.ResponseDtos.UpdateResponseRequest;
import eu.dtc4eu.questionnaire.entity.QuestionnaireResponse;
import eu.dtc4eu.questionnaire.dto.ImportDtos.ImportReport;
import eu.dtc4eu.questionnaire.service.ResearchExportService;
import eu.dtc4eu.questionnaire.service.ResponseImportService;
import eu.dtc4eu.questionnaire.service.ResponseService;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

import java.util.List;

/** Module 4 — questionnaire responses + scoring (spec T5). */
@RestController
@RequestMapping("/api/v1/responses")
@RequiredArgsConstructor
@Tag(name = "Responses (Module 4)")
public class ResponseController {

    private final ResponseService responseService;
    private final ResponseImportService importService;
    private final ResearchExportService researchExportService;

    private static final String DATA_ENTRY =
            "hasAnyRole('SUPER_ADMIN','ADMIN','UPPER_COORDINATOR','COORDINATOR','AUDIT_COORDINATOR','INTERNAL_FACILITATOR')";

    @Operation(summary = "List responses (filters: facility, questionnaire, category, status; cycle implicit)")
    @GetMapping
    public PageResponse<ResponseRowDto> list(@RequestParam(required = false) Long facilityId,
                                             @RequestParam(required = false) Long questionnaireId,
                                             @RequestParam(required = false)
                                             QuestionnaireResponse.RespondentCategory category,
                                             @RequestParam(required = false) QuestionnaireResponse.Status status,
                                             @ParameterObject @PageableDefault(size = 20, sort = "id",
                                                     direction = Sort.Direction.DESC) Pageable pageable,
                                             @AuthenticationPrincipal AppUserDetails principal) {
        return responseService.list(facilityId, questionnaireId, category, status, pageable, principal);
    }

    @Operation(summary = "Raw item-level CSV export (research/admin)")
    @GetMapping("/export")
    @PreAuthorize("hasAnyRole('RESEARCH_TEAM','ADMIN','SUPER_ADMIN')")
    public ResponseEntity<String> export(@RequestParam(required = false) Long facilityId,
                                         @RequestParam(required = false) Long questionnaireId,
                                         @RequestParam(required = false)
                                         QuestionnaireResponse.RespondentCategory category,
                                         @RequestParam(required = false) QuestionnaireResponse.Status status,
                                         @AuthenticationPrincipal AppUserDetails principal) {
        String csv = responseService.exportItemsCsv(facilityId, questionnaireId, category, status, principal);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"response-items.csv\"")
                .body(csv);
    }

    @Operation(summary = "SPSS / R research export (REP-05, REP-07): zip with wide data CSV, codebook, "
            + ".sps syntax and an R reader, for one questionnaire")
    @GetMapping("/export/research")
    @PreAuthorize("hasAnyRole('RESEARCH_TEAM','ADMIN','SUPER_ADMIN')")
    public ResponseEntity<byte[]> exportResearch(@RequestParam Long questionnaireId,
                                                 @RequestParam(required = false) Long facilityId,
                                                 @RequestParam(required = false)
                                                 QuestionnaireResponse.RespondentCategory category,
                                                 @RequestParam(required = false) QuestionnaireResponse.Status status,
                                                 @AuthenticationPrincipal AppUserDetails principal) {
        ResearchExportService.ResearchBundle bundle = researchExportService.export(questionnaireId, facilityId,
                category, status, principal);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/zip"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + bundle.fileName() + "\"")
                .body(bundle.content());
    }

    @Operation(summary = "XLSX template for entering one form's paper questionnaires (spec COL-06)")
    @GetMapping("/import/template")
    @PreAuthorize(DATA_ENTRY)
    public ResponseEntity<byte[]> importTemplate(@RequestParam Long questionnaireId, @RequestParam Long formId) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\""
                        + importService.templateFileName(questionnaireId, formId) + "\"")
                .body(importService.template(questionnaireId, formId));
    }

    @Operation(summary = "Validate (commit=false) or import (commit=true) a filled XLSX template; "
            + "nothing is imported while any row is invalid")
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(DATA_ENTRY)
    public ImportReport importResponses(@RequestParam Long questionnaireId, @RequestParam Long formId,
                                        @RequestParam Long facilityId,
                                        @RequestParam(defaultValue = "false") boolean commit,
                                        @RequestParam("file") MultipartFile file,
                                        @AuthenticationPrincipal AppUserDetails actor) throws IOException {
        try (InputStream in = file.getInputStream()) {
            return importService.importFile(questionnaireId, formId, facilityId, in, commit, actor);
        }
    }

    @GetMapping("/{id}")
    public ResponseDetailDto get(@PathVariable Long id, @AuthenticationPrincipal AppUserDetails principal) {
        return responseService.get(id, principal);
    }

    @Operation(summary = "Open a response (DRAFT) for a subject code")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseDetailDto create(@Valid @RequestBody CreateResponseRequest request,
                                    @AuthenticationPrincipal AppUserDetails actor) {
        return responseService.create(request, actor);
    }

    @Operation(summary = "Update response header data (DRAFT only)")
    @PutMapping("/{id}")
    public ResponseDetailDto update(@PathVariable Long id, @Valid @RequestBody UpdateResponseRequest request,
                                    @AuthenticationPrincipal AppUserDetails actor) {
        return responseService.update(id, request, actor);
    }

    @Operation(summary = "Autosave raw answers (Likert + free text); DRAFT only, partial batches allowed")
    @PutMapping("/{id}/items")
    public SaveItemsResult saveItems(@PathVariable Long id, @Valid @RequestBody SaveItemsRequest request,
                                     @AuthenticationPrincipal AppUserDetails actor) {
        return responseService.saveItems(id, request, actor);
    }

    @Operation(summary = "Submit: validate required items → lock → compute area scores")
    @PostMapping("/{id}/submit")
    public List<AreaScoreDto> submit(@PathVariable Long id, @AuthenticationPrincipal AppUserDetails actor) {
        return responseService.submit(id, actor);
    }

    @Operation(summary = "Computed area means (empty until submitted)")
    @GetMapping("/{id}/scores")
    public List<AreaScoreDto> scores(@PathVariable Long id, @AuthenticationPrincipal AppUserDetails principal) {
        return responseService.getScores(id, principal);
    }
}
