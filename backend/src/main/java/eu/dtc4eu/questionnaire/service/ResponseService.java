package eu.dtc4eu.questionnaire.service;

import eu.dtc4eu.audit.AuditService;
import eu.dtc4eu.common.error.BadRequestException;
import eu.dtc4eu.common.error.ConflictException;
import eu.dtc4eu.common.error.NotFoundException;
import eu.dtc4eu.common.web.CsvWriter;
import eu.dtc4eu.common.web.PageResponse;
import eu.dtc4eu.facility.entity.Facility;
import eu.dtc4eu.facility.repository.FacilityRepository;
import eu.dtc4eu.questionnaire.dto.ResponseDtos.AreaScoreDto;
import eu.dtc4eu.questionnaire.dto.ResponseDtos.CreateResponseRequest;
import eu.dtc4eu.questionnaire.dto.ResponseDtos.ItemAnswer;
import eu.dtc4eu.questionnaire.dto.ResponseDtos.ResponseDetailDto;
import eu.dtc4eu.questionnaire.dto.ResponseDtos.ResponseItemDto;
import eu.dtc4eu.questionnaire.dto.ResponseDtos.ResponseRowDto;
import eu.dtc4eu.questionnaire.dto.ResponseDtos.SaveItemsRequest;
import eu.dtc4eu.questionnaire.dto.ResponseDtos.SaveItemsResult;
import eu.dtc4eu.questionnaire.dto.ResponseDtos.UpdateResponseRequest;
import eu.dtc4eu.questionnaire.entity.Questionnaire;
import eu.dtc4eu.questionnaire.entity.QuestionnaireArea;
import eu.dtc4eu.questionnaire.entity.QuestionnaireForm;
import eu.dtc4eu.questionnaire.entity.QuestionnaireItem;
import eu.dtc4eu.questionnaire.entity.QuestionnaireResponse;
import eu.dtc4eu.questionnaire.entity.QuestionnaireResponseItem;
import eu.dtc4eu.questionnaire.entity.ResponseAreaScore;
import eu.dtc4eu.questionnaire.repository.QuestionnaireAreaRepository;
import eu.dtc4eu.questionnaire.repository.QuestionnaireFormRepository;
import eu.dtc4eu.questionnaire.repository.QuestionnaireItemRepository;
import eu.dtc4eu.questionnaire.repository.QuestionnaireRepository;
import eu.dtc4eu.questionnaire.repository.QuestionnaireResponseItemRepository;
import eu.dtc4eu.questionnaire.repository.QuestionnaireResponseRepository;
import eu.dtc4eu.questionnaire.repository.ResponseAreaScoreRepository;
import eu.dtc4eu.security.AppUserDetails;
import eu.dtc4eu.security.CycleContext;
import eu.dtc4eu.security.DataScope;
import eu.dtc4eu.security.DataScopeService;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Module 4 core (spec T5). Responses are cycle-scoped and data-scoped; raw
 * item answers autosave while DRAFT and freeze at submit, when the scoring
 * engine derives the area means.
 */
@Service
@RequiredArgsConstructor
public class ResponseService {

    private final QuestionnaireResponseRepository responseRepository;
    private final QuestionnaireResponseItemRepository responseItemRepository;
    private final ResponseAreaScoreRepository scoreRepository;
    private final QuestionnaireRepository questionnaireRepository;
    private final QuestionnaireItemRepository itemRepository;
    private final QuestionnaireAreaRepository areaRepository;
    private final QuestionnaireFormRepository formRepository;
    private final FacilityRepository facilityRepository;
    private final ScoringService scoringService;
    private final eu.dtc4eu.scoring.service.FacilityScoringService facilityScoringService;
    private final ProfileSchemaService profileSchemaService;
    private final ProfileValidationService profileValidationService;
    private final DataScopeService dataScopeService;
    private final CycleContext cycleContext;
    private final AuditService auditService;

    /* ---------------------------------- reads ---------------------------------- */

    @Transactional(readOnly = true)
    public PageResponse<ResponseRowDto> list(Long facilityId, Long questionnaireId,
                                             QuestionnaireResponse.RespondentCategory category,
                                             QuestionnaireResponse.Status status, Pageable pageable,
                                             AppUserDetails principal) {
        DataScope scope = dataScopeService.scopeOf(principal);
        Page<QuestionnaireResponse> page = responseRepository.findAll(
                specification(facilityId, questionnaireId, category, status, scope), pageable);

        Map<Long, String> questionnaireCodes = questionnaireRepository
                .findAllById(page.getContent().stream().map(QuestionnaireResponse::getQuestionnaireId).toList())
                .stream().collect(Collectors.toMap(Questionnaire::getId, Questionnaire::getCode));
        Map<Long, String> facilityCodes = facilityRepository
                .findAllById(page.getContent().stream().map(QuestionnaireResponse::getFacilityId)
                        .filter(Objects::nonNull).toList())
                .stream().collect(Collectors.toMap(Facility::getId, Facility::getFacilityCode));

        return PageResponse.of(page, r -> new ResponseRowDto(r.getId(), r.getQuestionnaireId(),
                questionnaireCodes.get(r.getQuestionnaireId()), r.getFacilityId(),
                r.getFacilityId() != null ? facilityCodes.get(r.getFacilityId()) : null,
                r.getSubjectCode(), r.getRespondentCategory(), r.getStatus(), r.getSubmittedAt(), r.getCreatedAt()));
    }

    @Transactional(readOnly = true)
    public ResponseDetailDto get(Long id, AppUserDetails principal) {
        return toDetail(findInScope(id, principal));
    }

    @Transactional(readOnly = true)
    public List<AreaScoreDto> getScores(Long id, AppUserDetails principal) {
        QuestionnaireResponse response = findInScope(id, principal);
        Map<Long, QuestionnaireArea> areas = areaRepository
                .findByQuestionnaireIdOrderBySortOrderAscIdAsc(response.getQuestionnaireId()).stream()
                .collect(Collectors.toMap(QuestionnaireArea::getId, Function.identity()));
        return scoreRepository.findByResponseId(id).stream()
                .sorted(java.util.Comparator.comparing(score -> {
                    QuestionnaireArea area = areas.get(score.getAreaId());
                    return area != null && area.getSortOrder() != null ? area.getSortOrder() : Integer.MAX_VALUE;
                }))
                .map(score -> {
                    QuestionnaireArea area = areas.get(score.getAreaId());
                    return new AreaScoreDto(score.getAreaId(),
                            area != null ? area.getAreaCode() : "?",
                            area != null ? area.getName() : "?",
                            score.getMeanValue(), score.getComputedAt());
                })
                .toList();
    }

    /* ---------------------------------- writes --------------------------------- */

    @Transactional
    public ResponseDetailDto create(CreateResponseRequest request, AppUserDetails actor) {
        Long cycleId = cycleContext.requireWritable().getId();

        Questionnaire questionnaire = questionnaireRepository.findById(request.questionnaireId())
                .orElseThrow(() -> NotFoundException.of("Questionnaire", request.questionnaireId()));
        if (!questionnaire.isActive()) {
            throw new BadRequestException("Questionnaire " + questionnaire.getCode() + " is not active");
        }

        QuestionnaireForm form = null;
        if (request.formId() != null) {
            form = formRepository.findById(request.formId())
                    .filter(f -> f.getQuestionnaireId().equals(questionnaire.getId()))
                    .orElseThrow(() -> new BadRequestException("Form does not belong to this questionnaire"));
        } else if (formRepository.countByQuestionnaireId(questionnaire.getId()) > 0) {
            throw new BadRequestException("Questionnaire " + questionnaire.getCode()
                    + " is administered through forms — formId is required");
        }
        if (form == null && request.respondentCategory() == null) {
            throw new BadRequestException("respondentCategory is required for form-less questionnaires");
        }

        DataScope scope = dataScopeService.scopeOf(actor);
        if (request.facilityId() != null) {
            facilityRepository.findById(request.facilityId())
                    .filter(f -> scope.canSeeFacility(f.getId()) || scope.respondentOnly())
                    .orElseThrow(() -> NotFoundException.of("Facility", request.facilityId()));
        }

        validateProfile(form, questionnaire.getCode(), request.profileSnapshot(), false);

        QuestionnaireResponse response = new QuestionnaireResponse();
        response.setQuestionnaireId(questionnaire.getId());
        response.setFormId(form != null ? form.getId() : null);
        response.setAnnualCycleId(cycleId);
        response.setFacilityId(request.facilityId());
        response.setSubjectCode(request.subjectCode());
        response.setRespondentCategory(form != null ? categoryOf(form) : request.respondentCategory());
        response.setAdministrationMode(form != null ? form.getAdministrationMode()
                : request.administrationMode());
        response.setProfileSnapshot(request.profileSnapshot());
        response.setCreatedBy(actor.getId());
        response = responseRepository.save(response);

        auditService.success(actor.getId(), "CREATE", "questionnaire_response", response.getId());
        return toDetail(response);
    }

    @Transactional
    public ResponseDetailDto update(Long id, UpdateResponseRequest request, AppUserDetails actor) {
        QuestionnaireResponse response = findInScope(id, actor);
        requireDraft(response);
        cycleContext.requireWritable();

        if (request.facilityId() != null && !Objects.equals(request.facilityId(), response.getFacilityId())) {
            DataScope scope = dataScopeService.scopeOf(actor);
            facilityRepository.findById(request.facilityId())
                    .filter(f -> scope.canSeeFacility(f.getId()) || scope.respondentOnly())
                    .orElseThrow(() -> NotFoundException.of("Facility", request.facilityId()));
        }
        response.setFacilityId(request.facilityId());
        response.setSubjectCode(request.subjectCode());
        if (response.getFormId() == null && request.respondentCategory() != null) {
            // form-bound responses keep the category derived from their form
            response.setRespondentCategory(request.respondentCategory());
        }
        if (response.getFormId() == null && request.administrationMode() != null) {
            response.setAdministrationMode(request.administrationMode());
        }
        if (request.profileSnapshot() != null) {
            validateProfileOf(response, request.profileSnapshot(), false);
            response.setProfileSnapshot(request.profileSnapshot());
        }
        auditService.success(actor.getId(), "UPDATE", "questionnaire_response", response.getId());
        return toDetail(response);
    }

    /** Autosave: upsert raw answers while DRAFT; Likert values validated against the scale bounds. */
    @Transactional
    public SaveItemsResult saveItems(Long id, SaveItemsRequest request, AppUserDetails actor) {
        QuestionnaireResponse response = findInScope(id, actor);
        requireDraft(response);
        cycleContext.requireWritable();

        Questionnaire questionnaire = questionnaireRepository.findById(response.getQuestionnaireId()).orElseThrow();
        Map<Long, QuestionnaireItem> definition = definitionItems(response).stream()
                .collect(Collectors.toMap(QuestionnaireItem::getId, Function.identity()));

        for (ItemAnswer answer : request.items()) {
            QuestionnaireItem item = definition.get(answer.itemId());
            if (item == null) {
                throw new BadRequestException("Item " + answer.itemId() + " does not belong to "
                        + (response.getFormId() != null ? "this response's form" : "questionnaire "
                        + questionnaire.getCode()));
            }
            if (item.getResponseType() == QuestionnaireItem.ResponseType.LIKERT) {
                Integer value = answer.numericValue();
                if (value != null && (value < questionnaire.getScaleMin() || value > questionnaire.getScaleMax())) {
                    throw new BadRequestException("Value for item " + item.getItemCode() + " must be between "
                            + questionnaire.getScaleMin() + " and " + questionnaire.getScaleMax());
                }
            }
            QuestionnaireResponseItem row = responseItemRepository
                    .findByResponseIdAndItemId(response.getId(), item.getId())
                    .orElseGet(() -> {
                        QuestionnaireResponseItem created = new QuestionnaireResponseItem();
                        created.setResponseId(response.getId());
                        created.setItemId(item.getId());
                        return created;
                    });
            if (item.getResponseType() == QuestionnaireItem.ResponseType.LIKERT) {
                row.setNumericValue(answer.numericValue());
                row.setTextValue(null);
            } else {
                row.setTextValue(answer.textValue());
                row.setNumericValue(null);
            }
            responseItemRepository.save(row);
        }

        long total = requiredLikertCount(response);
        long answered = responseItemRepository.countByResponseIdAndNumericValueNotNull(response.getId());
        return new SaveItemsResult(answered, total, response.getStatus());
    }

    /** Submit: validate required items → lock → compute scores (spec T5). */
    @Transactional
    public List<AreaScoreDto> submit(Long id, AppUserDetails actor) {
        QuestionnaireResponse response = findInScope(id, actor);
        requireDraft(response);
        cycleContext.requireWritable();

        long total = requiredLikertCount(response);
        long answered = responseItemRepository.countByResponseIdAndNumericValueNotNull(response.getId());
        if (answered < total) {
            throw new BadRequestException("Cannot submit: " + (total - answered)
                    + " required Likert item(s) not answered");
        }
        validateProfileOf(response, response.getProfileSnapshot(), true);

        response.setStatus(QuestionnaireResponse.Status.SUBMITTED);
        response.setSubmittedAt(OffsetDateTime.now());
        responseRepository.saveAndFlush(response);
        scoringService.computeScores(response.getId(), response.getQuestionnaireId());
        if (response.getFacilityId() != null) {
            // facility-cycle aggregates (register §3) stay as fresh as the last submit
            facilityScoringService.recompute(response.getFacilityId(), response.getAnnualCycleId(),
                    response.getQuestionnaireId());
        }
        auditService.success(actor.getId(), "SUBMIT", "questionnaire_response", response.getId(),
                Map.of("answered", answered, "total", total));
        return getScores(id, actor);
    }

    /* ---------------------------------- export ---------------------------------- */

    /** Raw item-level export for the research role (spec T5); pseudonymous codes only. */
    @Transactional(readOnly = true)
    public String exportItemsCsv(Long facilityId, Long questionnaireId,
                                 QuestionnaireResponse.RespondentCategory category,
                                 QuestionnaireResponse.Status status, AppUserDetails principal) {
        DataScope scope = dataScopeService.scopeOf(principal);
        List<QuestionnaireResponse> responses = responseRepository.findAll(
                specification(facilityId, questionnaireId, category, status, scope), Sort.by("id"));

        Map<Long, String> questionnaireCodes = questionnaireRepository.findAll().stream()
                .collect(Collectors.toMap(Questionnaire::getId, Questionnaire::getCode));
        Map<Long, String> facilityCodes = facilityRepository.findAll().stream()
                .collect(Collectors.toMap(Facility::getId, Facility::getFacilityCode));
        Map<Long, String> formCodes = formRepository.findAll().stream()
                .filter(f -> f.getFormCode() != null)
                .collect(Collectors.toMap(QuestionnaireForm::getId, QuestionnaireForm::getFormCode));

        CsvWriter csv = new CsvWriter().row("response_id", "questionnaire", "form_code", "facility_code",
                "subject_code", "respondent_category", "status", "submitted_at", "item_code", "area_code",
                "item_letter", "numeric_value", "text_value");

        for (QuestionnaireResponse response : responses) {
            Map<Long, QuestionnaireItem> items = itemRepository
                    .findByQuestionnaireIdOrderBySortOrderAscIdAsc(response.getQuestionnaireId()).stream()
                    .collect(Collectors.toMap(QuestionnaireItem::getId, Function.identity()));
            Map<Long, QuestionnaireArea> areas = areaRepository
                    .findByQuestionnaireIdOrderBySortOrderAscIdAsc(response.getQuestionnaireId()).stream()
                    .collect(Collectors.toMap(QuestionnaireArea::getId, Function.identity()));
            for (QuestionnaireResponseItem answer : responseItemRepository.findByResponseId(response.getId())) {
                QuestionnaireItem item = items.get(answer.getItemId());
                QuestionnaireArea area = item != null && item.getAreaId() != null ? areas.get(item.getAreaId()) : null;
                csv.row(response.getId(), questionnaireCodes.get(response.getQuestionnaireId()),
                        response.getFormId() != null ? formCodes.get(response.getFormId()) : null,
                        response.getFacilityId() != null ? facilityCodes.get(response.getFacilityId()) : null,
                        response.getSubjectCode(), response.getRespondentCategory(), response.getStatus(),
                        response.getSubmittedAt(), item != null ? item.getItemCode() : answer.getItemId(),
                        area != null ? area.getAreaCode() : null, item != null ? item.getItemLetter() : null,
                        answer.getNumericValue(), answer.getTextValue());
            }
        }
        auditService.success(principal.getId(), "EXPORT", "questionnaire_response", null,
                Map.of("responses", responses.size(), "cycleId", cycleContext.requireId()));
        return csv.build();
    }

    /** The caller's in-scope responses for the filters, oldest first (shared by the research exports). */
    @Transactional(readOnly = true)
    public List<QuestionnaireResponse> scopedResponses(Long facilityId, Long questionnaireId,
                                                       QuestionnaireResponse.RespondentCategory category,
                                                       QuestionnaireResponse.Status status, AppUserDetails principal) {
        return responseRepository.findAll(specification(facilityId, questionnaireId, category, status,
                dataScopeService.scopeOf(principal)), Sort.by("id"));
    }

    /* --------------------------------- internals -------------------------------- */

    /** The item definition of a response: its form's items when form-bound, otherwise the whole questionnaire. */
    private List<QuestionnaireItem> definitionItems(QuestionnaireResponse response) {
        return response.getFormId() != null
                ? itemRepository.findByFormIdOrderBySortOrderAscIdAsc(response.getFormId())
                : itemRepository.findByQuestionnaireIdOrderBySortOrderAscIdAsc(response.getQuestionnaireId());
    }

    private long requiredLikertCount(QuestionnaireResponse response) {
        if (response.getFormId() != null) {
            return itemRepository.countByFormIdAndResponseType(response.getFormId(),
                    QuestionnaireItem.ResponseType.LIKERT);
        }
        return itemRepository.findByQuestionnaireIdOrderBySortOrderAscIdAsc(response.getQuestionnaireId()).stream()
                .filter(item -> item.getResponseType() == QuestionnaireItem.ResponseType.LIKERT)
                .count();
    }

    /** Validate a profile snapshot against the response form's schema; forms without a schema skip validation. */
    private void validateProfileOf(QuestionnaireResponse response, Map<String, Object> profile, boolean strict) {
        if (response.getFormId() == null) {
            return;
        }
        QuestionnaireForm form = formRepository.findById(response.getFormId()).orElse(null);
        String questionnaireCode = questionnaireRepository.findById(response.getQuestionnaireId())
                .map(Questionnaire::getCode).orElse(null);
        validateProfile(form, questionnaireCode, profile, strict);
    }

    private void validateProfile(QuestionnaireForm form, String questionnaireCode,
                                 Map<String, Object> profile, boolean strict) {
        if (form == null || form.getFormCode() == null || questionnaireCode == null) {
            return;
        }
        profileSchemaService.find(questionnaireCode, form.getFormCode())
                .ifPresent(schema -> profileValidationService.validate(profile, schema, strict));
    }

    /** A form fixes the respondent category: its stakeholder category, or the collective mode itself. */
    private QuestionnaireResponse.RespondentCategory categoryOf(QuestionnaireForm form) {
        return switch (form.getAdministrationMode()) {
            case LONG_FORM -> QuestionnaireResponse.RespondentCategory.LONG_FORM;
            case DELEGATION -> QuestionnaireResponse.RespondentCategory.DELEGATION;
            case INDIVIDUAL -> {
                if (form.getStakeholderCategory() == null) {
                    throw new BadRequestException("Individual form has no stakeholder category");
                }
                yield QuestionnaireResponse.RespondentCategory.valueOf(form.getStakeholderCategory().name());
            }
        };
    }

    private void requireDraft(QuestionnaireResponse response) {
        if (response.isSubmitted()) {
            throw new ConflictException("Response was submitted and is read-only");
        }
    }

    /**
     * Visibility (spec §6): admins/research see all; facility-scoped users see
     * responses of their facilities; everyone sees responses they created.
     */
    private QuestionnaireResponse findInScope(Long id, AppUserDetails principal) {
        DataScope scope = dataScopeService.scopeOf(principal);
        return responseRepository.findById(id)
                .filter(r -> r.getAnnualCycleId().equals(cycleContext.requireId()))
                .filter(r -> scope.all()
                        || Objects.equals(r.getCreatedBy(), principal.getId())
                        || (r.getFacilityId() != null && scope.canSeeFacility(r.getFacilityId())))
                .orElseThrow(() -> NotFoundException.of("Response", id));
    }

    private Specification<QuestionnaireResponse> specification(Long facilityId, Long questionnaireId,
                                                               QuestionnaireResponse.RespondentCategory category,
                                                               QuestionnaireResponse.Status status,
                                                               DataScope scope) {
        Long cycleId = cycleContext.requireId();
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("annualCycleId"), cycleId));
            if (!scope.all()) {
                Predicate own = cb.equal(root.get("createdBy"), scope.userId());
                if (scope.facilityIds().isEmpty()) {
                    predicates.add(own);
                } else {
                    predicates.add(cb.or(own, root.get("facilityId").in(scope.facilityIds())));
                }
            }
            if (facilityId != null) {
                predicates.add(cb.equal(root.get("facilityId"), facilityId));
            }
            if (questionnaireId != null) {
                predicates.add(cb.equal(root.get("questionnaireId"), questionnaireId));
            }
            if (category != null) {
                predicates.add(cb.equal(root.get("respondentCategory"), category));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private ResponseDetailDto toDetail(QuestionnaireResponse response) {
        Questionnaire questionnaire = questionnaireRepository.findById(response.getQuestionnaireId()).orElseThrow();
        String facilityCode = response.getFacilityId() != null
                ? facilityRepository.findById(response.getFacilityId()).map(Facility::getFacilityCode).orElse(null)
                : null;
        List<ResponseItemDto> items = responseItemRepository.findByResponseId(response.getId()).stream()
                .map(item -> new ResponseItemDto(item.getItemId(), item.getNumericValue(), item.getTextValue()))
                .toList();
        long total = requiredLikertCount(response);
        long answered = responseItemRepository.countByResponseIdAndNumericValueNotNull(response.getId());
        return new ResponseDetailDto(response.getId(), questionnaire.getId(), questionnaire.getCode(),
                response.getFormId(), response.getFacilityId(), facilityCode, response.getSubjectCode(),
                response.getRespondentCategory(), response.getAdministrationMode(), response.getStatus(),
                response.getSubmittedAt(), response.getProfileSnapshot(), items, answered, total);
    }
}
