package eu.dtc4eu.questionnaire.controller;

import eu.dtc4eu.questionnaire.dto.QuestionnaireDtos.AreaDto;
import eu.dtc4eu.questionnaire.dto.QuestionnaireDtos.CreateQuestionnaireRequest;
import eu.dtc4eu.questionnaire.dto.QuestionnaireDtos.FormDto;
import eu.dtc4eu.questionnaire.dto.QuestionnaireDtos.ItemDto;
import eu.dtc4eu.questionnaire.dto.QuestionnaireDtos.QuestionnaireDetailDto;
import eu.dtc4eu.questionnaire.dto.QuestionnaireDtos.QuestionnaireRowDto;
import eu.dtc4eu.questionnaire.dto.QuestionnaireDtos.SaveAreaRequest;
import eu.dtc4eu.questionnaire.dto.QuestionnaireDtos.SaveFormRequest;
import eu.dtc4eu.questionnaire.dto.QuestionnaireDtos.SaveItemRequest;
import eu.dtc4eu.questionnaire.dto.QuestionnaireDtos.UpdateQuestionnaireRequest;
import eu.dtc4eu.questionnaire.service.QuestionnaireService;
import eu.dtc4eu.security.AppUserDetails;
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
 * T4 — questionnaire definitions. Reads are open to authenticated users (the
 * response container needs the structure); writes are admin-only.
 */
@RestController
@RequestMapping("/api/v1/questionnaires")
@RequiredArgsConstructor
@Tag(name = "Questionnaires (definitions)")
public class QuestionnaireController {

    private static final String ADMIN_ONLY = "hasAnyRole('ADMIN','SUPER_ADMIN')";

    private final QuestionnaireService questionnaireService;

    @GetMapping
    public List<QuestionnaireRowDto> list() {
        return questionnaireService.list();
    }

    @Operation(summary = "Full definition: scale bounds, areas, items, forms, anchors")
    @GetMapping("/{id}")
    public QuestionnaireDetailDto get(@PathVariable Long id) {
        return questionnaireService.get(id);
    }

    @Operation(summary = "The GAS-Set form definition, for previewing it without a facility (FIX-LIST #5)")
    @GetMapping("/instruments/gas-set/schema")
    public com.fasterxml.jackson.databind.JsonNode gasSetSchema() {
        return questionnaireService.getGasSetSchema();
    }

    @Operation(summary = "Per-form profile-header definition (v0.2 register §2.4)")
    @GetMapping("/{id}/forms/{formId}/profile-schema")
    public com.fasterxml.jackson.databind.JsonNode profileSchema(@PathVariable Long id, @PathVariable Long formId) {
        return questionnaireService.getProfileSchema(id, formId);
    }

    @PostMapping
    @PreAuthorize(ADMIN_ONLY)
    @ResponseStatus(HttpStatus.CREATED)
    public QuestionnaireDetailDto create(@Valid @RequestBody CreateQuestionnaireRequest request,
                                         @AuthenticationPrincipal AppUserDetails actor) {
        return questionnaireService.create(request, actor);
    }

    @Operation(summary = "Rename, adjust scale bounds, activate/deactivate")
    @PutMapping("/{id}")
    @PreAuthorize(ADMIN_ONLY)
    public QuestionnaireDetailDto update(@PathVariable Long id,
                                         @Valid @RequestBody UpdateQuestionnaireRequest request,
                                         @AuthenticationPrincipal AppUserDetails actor) {
        return questionnaireService.update(id, request, actor);
    }

    /* areas */

    @PostMapping("/{id}/areas")
    @PreAuthorize(ADMIN_ONLY)
    @ResponseStatus(HttpStatus.CREATED)
    public AreaDto addArea(@PathVariable Long id, @Valid @RequestBody SaveAreaRequest request,
                           @AuthenticationPrincipal AppUserDetails actor) {
        return questionnaireService.addArea(id, request, actor);
    }

    @PutMapping("/{id}/areas/{areaId}")
    @PreAuthorize(ADMIN_ONLY)
    public AreaDto updateArea(@PathVariable Long id, @PathVariable Long areaId,
                              @Valid @RequestBody SaveAreaRequest request,
                              @AuthenticationPrincipal AppUserDetails actor) {
        return questionnaireService.updateArea(id, areaId, request, actor);
    }

    @DeleteMapping("/{id}/areas/{areaId}")
    @PreAuthorize(ADMIN_ONLY)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteArea(@PathVariable Long id, @PathVariable Long areaId,
                           @AuthenticationPrincipal AppUserDetails actor) {
        questionnaireService.deleteArea(id, areaId, actor);
    }

    /* items */

    @PostMapping("/{id}/items")
    @PreAuthorize(ADMIN_ONLY)
    @ResponseStatus(HttpStatus.CREATED)
    public ItemDto addItem(@PathVariable Long id, @Valid @RequestBody SaveItemRequest request,
                           @AuthenticationPrincipal AppUserDetails actor) {
        return questionnaireService.addItem(id, request, actor);
    }

    @PutMapping("/{id}/items/{itemId}")
    @PreAuthorize(ADMIN_ONLY)
    public ItemDto updateItem(@PathVariable Long id, @PathVariable Long itemId,
                              @Valid @RequestBody SaveItemRequest request,
                              @AuthenticationPrincipal AppUserDetails actor) {
        return questionnaireService.updateItem(id, itemId, request, actor);
    }

    @DeleteMapping("/{id}/items/{itemId}")
    @PreAuthorize(ADMIN_ONLY)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteItem(@PathVariable Long id, @PathVariable Long itemId,
                           @AuthenticationPrincipal AppUserDetails actor) {
        questionnaireService.deleteItem(id, itemId, actor);
    }

    /* forms */

    @PostMapping("/{id}/forms")
    @PreAuthorize(ADMIN_ONLY)
    @ResponseStatus(HttpStatus.CREATED)
    public FormDto addForm(@PathVariable Long id, @Valid @RequestBody SaveFormRequest request,
                           @AuthenticationPrincipal AppUserDetails actor) {
        return questionnaireService.addForm(id, request, actor);
    }

    @DeleteMapping("/{id}/forms/{formId}")
    @PreAuthorize(ADMIN_ONLY)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteForm(@PathVariable Long id, @PathVariable Long formId,
                           @AuthenticationPrincipal AppUserDetails actor) {
        questionnaireService.deleteForm(id, formId, actor);
    }
}
