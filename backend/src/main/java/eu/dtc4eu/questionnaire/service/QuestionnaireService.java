package eu.dtc4eu.questionnaire.service;

import eu.dtc4eu.audit.AuditService;
import eu.dtc4eu.common.error.BadRequestException;
import eu.dtc4eu.common.error.ConflictException;
import eu.dtc4eu.common.error.NotFoundException;
import eu.dtc4eu.questionnaire.dto.QuestionnaireDtos.AnchorDto;
import eu.dtc4eu.questionnaire.dto.QuestionnaireDtos.AreaDto;
import eu.dtc4eu.questionnaire.dto.QuestionnaireDtos.CreateQuestionnaireRequest;
import eu.dtc4eu.questionnaire.dto.QuestionnaireDtos.FormAreaLabelDto;
import eu.dtc4eu.questionnaire.dto.QuestionnaireDtos.FormDto;
import eu.dtc4eu.questionnaire.dto.QuestionnaireDtos.ItemDto;
import eu.dtc4eu.questionnaire.dto.QuestionnaireDtos.QuestionnaireDetailDto;
import eu.dtc4eu.questionnaire.dto.QuestionnaireDtos.QuestionnaireRowDto;
import eu.dtc4eu.questionnaire.dto.QuestionnaireDtos.SaveAreaRequest;
import eu.dtc4eu.questionnaire.dto.QuestionnaireDtos.SaveFormRequest;
import eu.dtc4eu.questionnaire.dto.QuestionnaireDtos.SaveItemRequest;
import eu.dtc4eu.questionnaire.dto.QuestionnaireDtos.UpdateQuestionnaireRequest;
import eu.dtc4eu.questionnaire.entity.Questionnaire;
import eu.dtc4eu.questionnaire.entity.QuestionnaireArea;
import eu.dtc4eu.questionnaire.entity.QuestionnaireForm;
import eu.dtc4eu.questionnaire.entity.QuestionnaireItem;
import eu.dtc4eu.questionnaire.repository.QuestionnaireAreaRepository;
import eu.dtc4eu.questionnaire.repository.QuestionnaireFormAreaLabelRepository;
import eu.dtc4eu.questionnaire.repository.QuestionnaireFormRepository;
import eu.dtc4eu.questionnaire.repository.QuestionnaireItemRepository;
import eu.dtc4eu.questionnaire.repository.QuestionnaireResponseItemRepository;
import eu.dtc4eu.questionnaire.repository.QuestionnaireResponseRepository;
import eu.dtc4eu.questionnaire.repository.QuestionnaireRepository;
import eu.dtc4eu.questionnaire.repository.QuestionnaireScaleAnchorRepository;
import eu.dtc4eu.security.AppUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/** T4: questionnaire structure administration. Definitions are global (not cycle-scoped). */
@Service
@RequiredArgsConstructor
public class QuestionnaireService {

    private final QuestionnaireRepository questionnaireRepository;
    private final QuestionnaireAreaRepository areaRepository;
    private final QuestionnaireItemRepository itemRepository;
    private final QuestionnaireResponseItemRepository responseItemRepository;
    private final QuestionnaireResponseRepository responseRepository;
    private final QuestionnaireFormRepository formRepository;
    private final QuestionnaireScaleAnchorRepository anchorRepository;
    private final QuestionnaireFormAreaLabelRepository formAreaLabelRepository;
    private final ProfileSchemaService profileSchemaService;
    private final AuditService auditService;

    /* ------------------------------ questionnaires ------------------------------ */

    @Transactional(readOnly = true)
    public List<QuestionnaireRowDto> list() {
        return questionnaireRepository.findAll(Sort.by("code")).stream()
                .map(q -> new QuestionnaireRowDto(q.getId(), q.getCode(), q.getName(), q.getScaleMin(),
                        q.getScaleMax(), q.isActive(),
                        areaRepository.countByQuestionnaireId(q.getId()),
                        itemRepository.countByQuestionnaireId(q.getId()),
                        formRepository.countByQuestionnaireId(q.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public QuestionnaireDetailDto get(Long id) {
        Questionnaire questionnaire = find(id);
        List<QuestionnaireForm> forms = formRepository.findByQuestionnaireIdOrderBySortOrderAscIdAsc(id);
        List<FormAreaLabelDto> formAreaLabels = forms.isEmpty() ? List.of()
                : formAreaLabelRepository.findByFormIdIn(forms.stream().map(QuestionnaireForm::getId).toList())
                        .stream()
                        .map(l -> new FormAreaLabelDto(l.getFormId(), l.getAreaId(), l.getDisplayName()))
                        .toList();
        return new QuestionnaireDetailDto(questionnaire.getId(), questionnaire.getCode(), questionnaire.getName(),
                questionnaire.getScaleMin(), questionnaire.getScaleMax(), questionnaire.isActive(),
                areaRepository.findByQuestionnaireIdOrderBySortOrderAscIdAsc(id).stream().map(AreaDto::of).toList(),
                itemRepository.findByQuestionnaireIdOrderBySortOrderAscIdAsc(id).stream().map(ItemDto::of).toList(),
                forms.stream().map(FormDto::of).toList(),
                anchorRepository.findByQuestionnaireIdOrderByValue(id).stream()
                        .map(a -> new AnchorDto(a.getValue(), a.getLabel())).toList(),
                formAreaLabels, questionnaire.getBandCriticalMax(), questionnaire.getBandEfficacyMin(),
                responseRepository.existsByQuestionnaireId(id));
    }

    @Transactional
    public QuestionnaireDetailDto create(CreateQuestionnaireRequest request, AppUserDetails actor) {
        if (questionnaireRepository.existsByCode(request.code())) {
            throw new ConflictException("Questionnaire code already exists");
        }
        Questionnaire questionnaire = new Questionnaire();
        questionnaire.setCode(request.code());
        questionnaire.setName(request.name());
        questionnaire.setScaleMin(request.scaleMin() != null ? request.scaleMin() : 1);
        questionnaire.setScaleMax(request.scaleMax() != null ? request.scaleMax() : 4);
        validateScale(questionnaire.getScaleMin(), questionnaire.getScaleMax());
        questionnaire = questionnaireRepository.save(questionnaire);
        auditService.success(actor.getId(), "CREATE", "questionnaire", questionnaire.getId());
        return get(questionnaire.getId());
    }

    @Transactional
    public QuestionnaireDetailDto update(Long id, UpdateQuestionnaireRequest request, AppUserDetails actor) {
        Questionnaire questionnaire = find(id);
        questionnaire.setName(request.name());
        boolean scaleChange = (request.scaleMin() != null && request.scaleMin() != questionnaire.getScaleMin())
                || (request.scaleMax() != null && request.scaleMax() != questionnaire.getScaleMax());
        boolean bandChange = (request.bandCriticalMax() != null || request.bandEfficacyMin() != null)
                && !(sameValue(request.bandCriticalMax(), questionnaire.getBandCriticalMax())
                && sameValue(request.bandEfficacyMin(), questionnaire.getBandEfficacyMin()));
        if ((scaleChange || bandChange) && responseRepository.existsByQuestionnaireId(id)) {
            // answers and classifications already stored were made on the old scale (spec INS-06)
            throw new ConflictException("The scale and bands cannot change once the questionnaire has responses");
        }
        if (request.scaleMin() != null) {
            questionnaire.setScaleMin(request.scaleMin());
        }
        if (request.scaleMax() != null) {
            questionnaire.setScaleMax(request.scaleMax());
        }
        validateScale(questionnaire.getScaleMin(), questionnaire.getScaleMax());
        if (bandChange) {
            if (request.bandCriticalMax() == null || request.bandEfficacyMin() == null) {
                throw new BadRequestException("Both band limits are required");
            }
            validateBands(request.bandCriticalMax(), request.bandEfficacyMin(), questionnaire);
            questionnaire.setBandCriticalMax(request.bandCriticalMax());
            questionnaire.setBandEfficacyMin(request.bandEfficacyMin());
        } else if (questionnaire.getBandCriticalMax() != null) {
            validateBands(questionnaire.getBandCriticalMax(), questionnaire.getBandEfficacyMin(), questionnaire);
        }
        if (request.active() != null) {
            questionnaire.setActive(request.active());
        }
        auditService.success(actor.getId(), "UPDATE", "questionnaire", questionnaire.getId());
        return get(id);
    }

    /* ---------------------------------- areas ---------------------------------- */

    @Transactional
    public AreaDto addArea(Long questionnaireId, SaveAreaRequest request, AppUserDetails actor) {
        find(questionnaireId);
        if (areaRepository.existsByQuestionnaireIdAndAreaCode(questionnaireId, request.areaCode())) {
            throw new ConflictException("Area code already exists in this questionnaire");
        }
        QuestionnaireArea area = new QuestionnaireArea();
        area.setQuestionnaireId(questionnaireId);
        area.setAreaCode(request.areaCode());
        area.setName(request.name());
        area.setSortOrder(request.sortOrder());
        area = areaRepository.save(area);
        auditService.success(actor.getId(), "ADD_AREA", "questionnaire", questionnaireId);
        return AreaDto.of(area);
    }

    @Transactional
    public AreaDto updateArea(Long questionnaireId, Long areaId, SaveAreaRequest request, AppUserDetails actor) {
        QuestionnaireArea area = ownedArea(questionnaireId, areaId);
        if (!area.getAreaCode().equals(request.areaCode())
                && areaRepository.existsByQuestionnaireIdAndAreaCode(questionnaireId, request.areaCode())) {
            throw new ConflictException("Area code already exists in this questionnaire");
        }
        area.setAreaCode(request.areaCode());
        area.setName(request.name());
        area.setSortOrder(request.sortOrder());
        auditService.success(actor.getId(), "UPDATE_AREA", "questionnaire", questionnaireId);
        return AreaDto.of(area);
    }

    @Transactional
    public void deleteArea(Long questionnaireId, Long areaId, AppUserDetails actor) {
        QuestionnaireArea area = ownedArea(questionnaireId, areaId);
        try {
            areaRepository.delete(area);
            areaRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("Area is referenced by items or scores and cannot be deleted");
        }
        auditService.success(actor.getId(), "DELETE_AREA", "questionnaire", questionnaireId);
    }

    /* ---------------------------------- items ---------------------------------- */

    @Transactional
    public ItemDto addItem(Long questionnaireId, SaveItemRequest request, AppUserDetails actor) {
        find(questionnaireId);
        if (itemRepository.existsByQuestionnaireIdAndItemCode(questionnaireId, request.itemCode())) {
            throw new ConflictException("Item code already exists in this questionnaire");
        }
        validateItemArea(questionnaireId, request.areaId());
        validateItemForm(questionnaireId, null, request);
        QuestionnaireItem item = new QuestionnaireItem();
        item.setQuestionnaireId(questionnaireId);
        applyItem(item, request);
        item = itemRepository.save(item);
        auditService.success(actor.getId(), "ADD_ITEM", "questionnaire", questionnaireId);
        return ItemDto.of(item);
    }

    @Transactional
    public ItemDto updateItem(Long questionnaireId, Long itemId, SaveItemRequest request, AppUserDetails actor) {
        QuestionnaireItem item = ownedItem(questionnaireId, itemId);
        // spec INS-06: an answered item is frozen so stored responses stay comparable
        if (responseItemRepository.existsByItemId(itemId)) {
            throw new ConflictException("Item already has responses and cannot be changed");
        }
        if (!item.getItemCode().equals(request.itemCode())
                && itemRepository.existsByQuestionnaireIdAndItemCode(questionnaireId, request.itemCode())) {
            throw new ConflictException("Item code already exists in this questionnaire");
        }
        validateItemArea(questionnaireId, request.areaId());
        validateItemForm(questionnaireId, itemId, request);
        applyItem(item, request);
        auditService.success(actor.getId(), "UPDATE_ITEM", "questionnaire", questionnaireId);
        return ItemDto.of(item);
    }

    @Transactional
    public void deleteItem(Long questionnaireId, Long itemId, AppUserDetails actor) {
        QuestionnaireItem item = ownedItem(questionnaireId, itemId);
        try {
            itemRepository.delete(item);
            itemRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("Item already has responses and cannot be deleted");
        }
        auditService.success(actor.getId(), "DELETE_ITEM", "questionnaire", questionnaireId);
    }

    /* ---------------------------------- forms ---------------------------------- */

    @Transactional
    public FormDto addForm(Long questionnaireId, SaveFormRequest request, AppUserDetails actor) {
        find(questionnaireId);
        if (request.formCode() != null
                && formRepository.existsByQuestionnaireIdAndFormCode(questionnaireId, request.formCode())) {
            throw new ConflictException("Form code already exists in this questionnaire");
        }
        QuestionnaireForm form = new QuestionnaireForm();
        form.setQuestionnaireId(questionnaireId);
        form.setFormCode(request.formCode());
        form.setAdministrationMode(request.administrationMode());
        form.setStakeholderCategory(request.stakeholderCategory());
        form.setFacilityType(request.facilityType());
        form.setName(request.name());
        form.setSortOrder(request.sortOrder());
        form = formRepository.save(form);
        auditService.success(actor.getId(), "ADD_FORM", "questionnaire", questionnaireId);
        return FormDto.of(form);
    }

    @Transactional
    public void deleteForm(Long questionnaireId, Long formId, AppUserDetails actor) {
        QuestionnaireForm form = formRepository.findById(formId)
                .filter(f -> Objects.equals(f.getQuestionnaireId(), questionnaireId))
                .orElseThrow(() -> NotFoundException.of("Form", formId));
        try {
            formRepository.delete(form);
            formRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("Form already has responses and cannot be deleted");
        }
        auditService.success(actor.getId(), "DELETE_FORM", "questionnaire", questionnaireId);
    }

    /* ----------------------------- profile schemas ------------------------------ */

    /** The per-form profile-header definition (v0.2 register §2.4); 404 when the form has none. */
    @Transactional(readOnly = true)
    public com.fasterxml.jackson.databind.JsonNode getProfileSchema(Long questionnaireId, Long formId) {
        Questionnaire questionnaire = find(questionnaireId);
        QuestionnaireForm form = formRepository.findById(formId)
                .filter(f -> Objects.equals(f.getQuestionnaireId(), questionnaireId))
                .orElseThrow(() -> NotFoundException.of("Form", formId));
        return profileSchemaService.find(questionnaire.getCode(), form.getFormCode())
                .orElseThrow(() -> NotFoundException.of("Profile schema for form", formId));
    }

    /** Empty GAS-Set definition (field labels only, no data) for the Questionnaires-page preview. */
    public com.fasterxml.jackson.databind.JsonNode getGasSetSchema() {
        return profileSchemaService.find("gasset", "GAS_SET")
                .orElseThrow(() -> new NotFoundException("GAS-Set schema not available"));
    }

    /* --------------------------------- helpers --------------------------------- */

    private Questionnaire find(Long id) {
        return questionnaireRepository.findById(id).orElseThrow(() -> NotFoundException.of("Questionnaire", id));
    }

    private QuestionnaireArea ownedArea(Long questionnaireId, Long areaId) {
        return areaRepository.findById(areaId)
                .filter(area -> Objects.equals(area.getQuestionnaireId(), questionnaireId))
                .orElseThrow(() -> NotFoundException.of("Area", areaId));
    }

    private QuestionnaireItem ownedItem(Long questionnaireId, Long itemId) {
        return itemRepository.findById(itemId)
                .filter(item -> Objects.equals(item.getQuestionnaireId(), questionnaireId))
                .orElseThrow(() -> NotFoundException.of("Item", itemId));
    }

    private void validateItemArea(Long questionnaireId, Long areaId) {
        if (areaId != null) {
            areaRepository.findById(areaId)
                    .filter(area -> Objects.equals(area.getQuestionnaireId(), questionnaireId))
                    .orElseThrow(() -> new BadRequestException("Area does not belong to this questionnaire"));
        }
    }

    private void applyItem(QuestionnaireItem item, SaveItemRequest request) {
        item.setAreaId(request.areaId());
        item.setFormId(request.formId());
        item.setItemLetter(request.itemLetter());
        item.setItemCode(request.itemCode());
        item.setItemText(request.itemText());
        item.setResponseType(request.responseType());
        item.setSortOrder(request.sortOrder());
    }

    /** Form-owned items carry a coordinate letter; the (form, area, letter) triple must be unique. */
    private void validateItemForm(Long questionnaireId, Long currentItemId, SaveItemRequest request) {
        if (request.formId() == null) {
            return;
        }
        QuestionnaireForm form = formRepository.findById(request.formId())
                .filter(f -> Objects.equals(f.getQuestionnaireId(), questionnaireId))
                .orElseThrow(() -> new BadRequestException("Form does not belong to this questionnaire"));
        if (request.itemLetter() != null && !request.itemLetter().matches("[a-e]")) {
            throw new BadRequestException("Item letter must be a, b, c, d or e");
        }
        if (request.areaId() != null && request.itemLetter() != null) {
            boolean taken = itemRepository
                    .existsByFormIdAndAreaIdAndItemLetter(form.getId(), request.areaId(), request.itemLetter());
            if (taken && (currentItemId == null || !isSameCoordinate(currentItemId, request))) {
                throw new ConflictException("This form already has an item at that coordinate");
            }
        }
    }

    private boolean isSameCoordinate(Long itemId, SaveItemRequest request) {
        return itemRepository.findById(itemId)
                .filter(existing -> Objects.equals(existing.getFormId(), request.formId())
                        && Objects.equals(existing.getAreaId(), request.areaId())
                        && Objects.equals(existing.getItemLetter(), request.itemLetter()))
                .isPresent();
    }

    private static boolean sameValue(BigDecimal a, BigDecimal b) {
        return a == null ? b == null : b != null && a.compareTo(b) == 0;
    }

    /** Bands must sit inside the scale with CRITICAL below EFFICACY (TAB.5). */
    private static void validateBands(BigDecimal criticalMax, BigDecimal efficacyMin,
                                      Questionnaire questionnaire) {
        if (criticalMax.compareTo(efficacyMin) >= 0
                || criticalMax.compareTo(BigDecimal.valueOf(questionnaire.getScaleMin())) < 0
                || efficacyMin.compareTo(BigDecimal.valueOf(questionnaire.getScaleMax())) > 0) {
            throw new BadRequestException("Bands must satisfy scaleMin ≤ critical limit < efficacy limit ≤ scaleMax");
        }
    }

    private void validateScale(int scaleMin, int scaleMax) {
        if (scaleMin >= scaleMax) {
            throw new BadRequestException("scaleMin must be lower than scaleMax");
        }
    }
}
