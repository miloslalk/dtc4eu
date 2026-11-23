package eu.dtc4eu.assignment.service;

import eu.dtc4eu.assignment.dto.AssignmentDtos.AssignedFormDto;
import eu.dtc4eu.assignment.dto.AssignmentDtos.FacilityContextDto;
import eu.dtc4eu.assignment.dto.AssignmentDtos.MyAssignmentsDto;
import eu.dtc4eu.assignment.dto.AssignmentDtos.ProfileDto;
import eu.dtc4eu.assignment.dto.AssignmentDtos.RuleDto;
import eu.dtc4eu.assignment.dto.AssignmentDtos.RuleTestRequest;
import eu.dtc4eu.assignment.dto.AssignmentDtos.RuleTestResult;
import eu.dtc4eu.assignment.dto.AssignmentDtos.SaveProfileRequest;
import eu.dtc4eu.assignment.dto.AssignmentDtos.SaveRuleRequest;
import eu.dtc4eu.assignment.dto.AssignmentDtos.TaskDto;
import eu.dtc4eu.assignment.dto.AssignmentDtos.TaskStatus;
import eu.dtc4eu.assignment.entity.AssignmentRule;
import eu.dtc4eu.assignment.entity.UserProfile;
import eu.dtc4eu.assignment.repository.AssignmentRuleRepository;
import eu.dtc4eu.assignment.repository.UserProfileRepository;
import eu.dtc4eu.assignment.service.RuleConditions.Field;
import eu.dtc4eu.audit.AuditService;
import eu.dtc4eu.common.error.BadRequestException;
import eu.dtc4eu.common.error.NotFoundException;
import eu.dtc4eu.cycle.entity.AnnualCycle;
import eu.dtc4eu.facility.entity.Facility;
import eu.dtc4eu.facility.entity.FacilityCycleParticipation;
import eu.dtc4eu.facility.repository.FacilityCycleParticipationRepository;
import eu.dtc4eu.facility.repository.FacilityRepository;
import eu.dtc4eu.questionnaire.entity.Questionnaire;
import eu.dtc4eu.questionnaire.entity.QuestionnaireForm;
import eu.dtc4eu.questionnaire.entity.QuestionnaireResponse;
import eu.dtc4eu.questionnaire.repository.QuestionnaireFormRepository;
import eu.dtc4eu.questionnaire.repository.QuestionnaireRepository;
import eu.dtc4eu.questionnaire.repository.QuestionnaireResponseRepository;
import eu.dtc4eu.security.AppUserDetails;
import eu.dtc4eu.security.CycleContext;
import eu.dtc4eu.terna.entity.Terna;
import eu.dtc4eu.terna.entity.TernaFacility;
import eu.dtc4eu.terna.repository.TernaFacilityRepository;
import eu.dtc4eu.terna.repository.TernaRepository;
import eu.dtc4eu.user.repository.UserFacilityAccessRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Module 5-bis assignment rule engine (spec COL-01…03, CORE-04). Rules are
 * managed by the Upper Coordinator and evaluated against the respondent's
 * per-cycle profile plus the context derived from the registry (their
 * facilities' setting, subtype, country and activity; their facilities'
 * ternas; the cycle; their roles). Every matching active rule assigns its form;
 * a form matched by several rules is listed once, under the highest-priority
 * rule. Nothing is hardcoded: with no rules, nobody is assigned anything.
 */
@Service
@RequiredArgsConstructor
public class AssignmentService {

    private final AssignmentRuleRepository ruleRepository;
    private final UserProfileRepository profileRepository;
    private final QuestionnaireFormRepository formRepository;
    private final QuestionnaireRepository questionnaireRepository;
    private final QuestionnaireResponseRepository responseRepository;
    private final UserFacilityAccessRepository facilityAccessRepository;
    private final FacilityRepository facilityRepository;
    private final FacilityCycleParticipationRepository participationRepository;
    private final TernaRepository ternaRepository;
    private final TernaFacilityRepository ternaFacilityRepository;
    private final CycleContext cycleContext;
    private final AuditService auditService;

    /* ---------------------------------- rules ---------------------------------- */

    @Transactional(readOnly = true)
    public List<RuleDto> listRules() {
        List<AssignmentRule> rules = ruleRepository.findAllByOrderByPriorityDescIdAsc();
        Map<Long, QuestionnaireForm> forms = formsById(rules.stream().map(AssignmentRule::getTargetFormId).toList());
        return rules.stream().map(rule -> toDto(rule, forms.get(rule.getTargetFormId()))).toList();
    }

    @Transactional
    public RuleDto createRule(SaveRuleRequest request, AppUserDetails actor) {
        AssignmentRule rule = new AssignmentRule();
        rule.setCreatedBy(actor.getId());
        QuestionnaireForm form = apply(rule, request);
        rule = ruleRepository.save(rule);
        auditService.success(actor.getId(), "CREATE", "assignment_rule", rule.getId());
        return toDto(rule, form);
    }

    @Transactional
    public RuleDto updateRule(Long id, SaveRuleRequest request, AppUserDetails actor) {
        AssignmentRule rule = findRule(id);
        QuestionnaireForm form = apply(rule, request);
        auditService.success(actor.getId(), "UPDATE", "assignment_rule", rule.getId());
        return toDto(rule, form);
    }

    @Transactional
    public void deleteRule(Long id, AppUserDetails actor) {
        ruleRepository.delete(findRule(id));
        auditService.success(actor.getId(), "DELETE", "assignment_rule", id);
    }

    /** Spec COL-02: which forms a sample profile would receive today (or on the given date). */
    @Transactional(readOnly = true)
    public RuleTestResult test(RuleTestRequest request) {
        Map<Field, List<String>> context = new EnumMap<>(Field.class);
        request.context().forEach((field, values) -> context.put(field, values == null ? List.of() : values));
        return new RuleTestResult(assignedForms(context, request.onDate() != null ? request.onDate() : LocalDate.now()));
    }

    /* --------------------------------- profile --------------------------------- */

    @Transactional(readOnly = true)
    public ProfileDto myProfile(AppUserDetails principal) {
        Long cycleId = cycleContext.requireId();
        Optional<UserProfile> profile = profileRepository.findByUserIdAndAnnualCycleId(principal.getId(), cycleId);
        return toProfileDto(cycleId, profile.orElse(null), facilityContext(principal.getId(), cycleId));
    }

    @Transactional
    public ProfileDto saveProfile(SaveProfileRequest request, AppUserDetails principal) {
        AnnualCycle cycle = cycleContext.requireWritable();
        if (request.ternaId() != null) {
            Terna terna = ternaRepository.findById(request.ternaId())
                    .orElseThrow(() -> new BadRequestException("Unknown terna " + request.ternaId()));
            if (!terna.getAnnualCycleId().equals(cycle.getId())) {
                throw new BadRequestException("Terna " + terna.getTernaCode() + " belongs to another cycle");
            }
        }
        UserProfile profile = profileRepository.findByUserIdAndAnnualCycleId(principal.getId(), cycle.getId())
                .orElseGet(() -> {
                    UserProfile created = new UserProfile();
                    created.setUserId(principal.getId());
                    created.setAnnualCycleId(cycle.getId());
                    return created;
                });
        profile.setAge(request.age());
        profile.setRoleCategory(request.roleCategory());
        profile.setTernaId(request.ternaId());
        profile.setProjectPhase(request.projectPhase());
        profile.setCountry(blankToNull(request.country()));
        profile.setCity(blankToNull(request.city()));
        profile = profileRepository.saveAndFlush(profile);
        auditService.success(principal.getId(), "UPDATE", "user_profile", profile.getId());
        return toProfileDto(cycle.getId(), profile, facilityContext(principal.getId(), cycle.getId()));
    }

    /* ------------------------------- assignments ------------------------------- */

    /** Spec COL-03: the caller's assigned forms in the selected cycle, as tasks with their status. */
    @Transactional(readOnly = true)
    public MyAssignmentsDto myAssignments(AppUserDetails principal) {
        AnnualCycle cycle = cycleContext.require();
        UserProfile profile = profileRepository.findByUserIdAndAnnualCycleId(principal.getId(), cycle.getId())
                .orElse(null);
        Map<Field, List<String>> context = contextOf(principal, cycle, profile);
        List<AssignedFormDto> forms = assignedForms(context, LocalDate.now());

        Map<Long, List<QuestionnaireResponse>> ownByForm = responseRepository
                .findByCreatedByAndAnnualCycleId(principal.getId(), cycle.getId()).stream()
                .filter(response -> response.getFormId() != null)
                .collect(Collectors.groupingBy(QuestionnaireResponse::getFormId));
        List<TaskDto> tasks = forms.stream().map(form -> task(form, ownByForm.getOrDefault(form.formId(), List.of())))
                .toList();
        boolean profileComplete = profile != null && profile.getRoleCategory() != null;
        return new MyAssignmentsDto(profileComplete, context, tasks);
    }

    private static TaskDto task(AssignedFormDto form, List<QuestionnaireResponse> own) {
        Comparator<QuestionnaireResponse> newestFirst = Comparator.comparing(QuestionnaireResponse::getId).reversed();
        Optional<QuestionnaireResponse> submitted = own.stream()
                .filter(r -> r.getStatus() == QuestionnaireResponse.Status.SUBMITTED).min(newestFirst);
        if (submitted.isPresent()) {
            return new TaskDto(form, TaskStatus.SUBMITTED, submitted.get().getId());
        }
        return own.stream().min(newestFirst)
                .map(draft -> new TaskDto(form, TaskStatus.DRAFT, draft.getId()))
                .orElseGet(() -> new TaskDto(form, TaskStatus.NOT_STARTED, null));
    }

    /** The values each rule field takes for this user in this cycle. */
    Map<Field, List<String>> contextOf(AppUserDetails principal, AnnualCycle cycle, UserProfile profile) {
        List<FacilityContextDto> facilities = facilityContext(principal.getId(), cycle.getId());
        Map<Field, List<String>> context = new EnumMap<>(Field.class);
        context.put(Field.CYCLE, List.of(cycle.getCode()));
        context.put(Field.ROLE, principal.getRoleCodes().stream().sorted().toList());
        context.put(Field.FACILITY_SETTING, distinct(facilities, FacilityContextDto::setting));
        context.put(Field.FACILITY_SUBTYPE, distinct(facilities, FacilityContextDto::subtype));

        // profile values win; registry values fill the gaps
        context.put(Field.CATEGORY, profile != null && profile.getRoleCategory() != null
                ? List.of(profile.getRoleCategory()) : List.of());
        context.put(Field.AGE, profile != null && profile.getAge() != null
                ? List.of(String.valueOf(profile.getAge())) : List.of());
        context.put(Field.COUNTRY, profile != null && profile.getCountry() != null
                ? List.of(profile.getCountry()) : distinct(facilities, FacilityContextDto::country));
        context.put(Field.PROJECT_PHASE, profile != null && profile.getProjectPhase() != null
                ? List.of(profile.getProjectPhase()) : distinct(facilities, FacilityContextDto::activity));
        context.put(Field.TERNA_TYPE, ternaTypes(profile, facilities, cycle.getId()));
        return context;
    }

    private List<String> ternaTypes(UserProfile profile, List<FacilityContextDto> facilities, Long cycleId) {
        Set<Long> ternaIds = new LinkedHashSet<>();
        if (profile != null && profile.getTernaId() != null) {
            ternaIds.add(profile.getTernaId());
        } else {
            facilities.forEach(f -> ternaFacilityRepository.findByFacilityId(f.facilityId()).stream()
                    .map(TernaFacility::getTernaId).forEach(ternaIds::add));
        }
        return ternaRepository.findAllById(ternaIds).stream()
                .filter(terna -> terna.getAnnualCycleId().equals(cycleId))
                .map(terna -> terna.getTernaType().name())
                .distinct()
                .toList();
    }

    private List<AssignedFormDto> assignedForms(Map<Field, List<String>> context, LocalDate day) {
        List<AssignmentRule> matching = ruleRepository.findAllByOrderByPriorityDescIdAsc().stream()
                .filter(rule -> rule.appliesOn(day))
                .filter(rule -> RuleConditions.matches(RuleConditions.parse(rule.getConditions()), context))
                .toList();
        Map<Long, QuestionnaireForm> forms = formsById(matching.stream().map(AssignmentRule::getTargetFormId).toList());
        Map<Long, Questionnaire> questionnaires = questionnaireRepository
                .findAllById(forms.values().stream().map(QuestionnaireForm::getQuestionnaireId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Questionnaire::getId, Function.identity()));

        Map<Long, AssignedFormDto> byForm = new LinkedHashMap<>();
        for (AssignmentRule rule : matching) {
            QuestionnaireForm form = forms.get(rule.getTargetFormId());
            Questionnaire questionnaire = form == null ? null : questionnaires.get(form.getQuestionnaireId());
            // an inactive questionnaire cannot be answered, so it is never assigned
            if (questionnaire == null || !questionnaire.isActive()) {
                continue;
            }
            byForm.putIfAbsent(form.getId(), new AssignedFormDto(form.getId(), questionnaire.getId(),
                    questionnaire.getCode(), questionnaire.getName(), form.getFormCode(), form.getName(),
                    form.getAdministrationMode() == null ? null : form.getAdministrationMode().name(),
                    rule.getId(), rule.getName()));
        }
        return new ArrayList<>(byForm.values());
    }

    /* --------------------------------- internals -------------------------------- */

    private QuestionnaireForm apply(AssignmentRule rule, SaveRuleRequest request) {
        if (request.validFrom() != null && request.validTo() != null && request.validTo().isBefore(request.validFrom())) {
            throw new BadRequestException("validTo must not be before validFrom");
        }
        QuestionnaireForm form = formRepository.findById(request.targetFormId())
                .orElseThrow(() -> new BadRequestException("Unknown form " + request.targetFormId()));
        rule.setName(request.name().trim());
        rule.setDescription(blankToNull(request.description()));
        rule.setConditions(RuleConditions.toMap(RuleConditions.parse(request.conditions())));
        rule.setTargetFormId(form.getId());
        rule.setTargetQuestionnaireId(form.getQuestionnaireId());
        rule.setPriority(request.priority());
        rule.setActive(request.active() == null || request.active());
        rule.setValidFrom(request.validFrom());
        rule.setValidTo(request.validTo());
        return form;
    }

    private List<FacilityContextDto> facilityContext(Long userId, Long cycleId) {
        List<Long> facilityIds = facilityAccessRepository.findFacilityIdsByUserId(userId);
        if (facilityIds.isEmpty()) {
            return List.of();
        }
        Map<Long, FacilityCycleParticipation.Activity> activities = participationRepository
                .findByAnnualCycleIdAndFacilityIdIn(cycleId, facilityIds).stream()
                .filter(p -> p.getActivity() != null)
                .collect(Collectors.toMap(FacilityCycleParticipation::getFacilityId,
                        FacilityCycleParticipation::getActivity, (a, b) -> a));
        return facilityRepository.findAllById(facilityIds).stream()
                .sorted(Comparator.comparing(Facility::getFacilityCode))
                .map(f -> new FacilityContextDto(f.getId(), f.getFacilityCode(),
                        f.getFacilityType() == null ? null : f.getFacilityType().name(),
                        f.getFacilitySubtype() == null ? null : f.getFacilitySubtype().name(),
                        f.getCountry(),
                        activities.containsKey(f.getId()) ? activities.get(f.getId()).name() : null))
                .toList();
    }

    private static List<String> distinct(List<FacilityContextDto> facilities,
                                         Function<FacilityContextDto, String> value) {
        return facilities.stream().map(value).filter(Objects::nonNull).distinct().toList();
    }

    private Map<Long, QuestionnaireForm> formsById(List<Long> ids) {
        return formRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(QuestionnaireForm::getId, Function.identity()));
    }

    private static RuleDto toDto(AssignmentRule rule, QuestionnaireForm form) {
        return RuleDto.of(rule, form == null ? null : form.getFormCode(), form == null ? null : form.getName());
    }

    private static ProfileDto toProfileDto(Long cycleId, UserProfile profile, List<FacilityContextDto> facilities) {
        if (profile == null) {
            return new ProfileDto(cycleId, null, null, null, null, null, null, null, facilities);
        }
        return new ProfileDto(cycleId, profile.getAge(), profile.getRoleCategory(), profile.getTernaId(),
                profile.getProjectPhase(), profile.getCountry(), profile.getCity(), profile.getUpdatedAt(), facilities);
    }

    private AssignmentRule findRule(Long id) {
        return ruleRepository.findById(id).orElseThrow(() -> NotFoundException.of("Assignment rule", id));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
