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

import java.util.List;

/** Module 4 — questionnaire responses + scoring (spec T5). */
@RestController
@RequestMapping("/api/v1/responses")
@RequiredArgsConstructor
@Tag(name = "Responses (Module 4)")
public class ResponseController {

    private final ResponseService responseService;

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
