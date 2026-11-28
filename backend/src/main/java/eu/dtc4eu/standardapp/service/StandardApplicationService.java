package eu.dtc4eu.standardapp.service;

import eu.dtc4eu.audit.AuditService;
import eu.dtc4eu.common.error.ConflictException;
import eu.dtc4eu.common.error.NotFoundException;
import eu.dtc4eu.cycle.entity.AnnualCycle;
import eu.dtc4eu.cycle.repository.AnnualCycleRepository;
import eu.dtc4eu.facility.entity.Facility;
import eu.dtc4eu.facility.entity.FacilityCycleParticipation;
import eu.dtc4eu.facility.repository.FacilityCycleParticipationRepository;
import eu.dtc4eu.facility.repository.FacilityRepository;
import eu.dtc4eu.questionnaire.entity.Questionnaire;
import eu.dtc4eu.questionnaire.entity.QuestionnaireArea;
import eu.dtc4eu.questionnaire.repository.QuestionnaireAreaRepository;
import eu.dtc4eu.questionnaire.repository.QuestionnaireRepository;
import eu.dtc4eu.scoring.entity.FacilityScaleScore;
import eu.dtc4eu.scoring.repository.FacilityScaleScoreRepository;
import eu.dtc4eu.security.AppUserDetails;
import eu.dtc4eu.security.CycleContext;
import eu.dtc4eu.security.DataScope;
import eu.dtc4eu.security.DataScopeService;
import eu.dtc4eu.standardapp.dto.StandardAppDtos.DocumentApplicationDto;
import eu.dtc4eu.standardapp.dto.StandardAppDtos.FacilityHistoryDto;
import eu.dtc4eu.standardapp.dto.StandardAppDtos.HistoryCycleDto;
import eu.dtc4eu.standardapp.dto.StandardAppDtos.SaveDocumentApplicationRequest;
import eu.dtc4eu.standardapp.entity.GppEffectivenessScore;
import eu.dtc4eu.standardapp.entity.ManualExcellencePoint;
import eu.dtc4eu.standardapp.entity.StandardApplication;
import eu.dtc4eu.standardapp.repository.GppEffectivenessScoreRepository;
import eu.dtc4eu.standardapp.repository.ManualExcellencePointRepository;
import eu.dtc4eu.standardapp.repository.StandardApplicationRepository;
import eu.dtc4eu.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Quality-document applications (GPP / CGS / EEN, v0.2 register §1+§6) and the
 * facility accreditation-history view: cycles in the programme, per-cycle
 * Excellence Points and per-cycle applied documents with their measured
 * application level.
 */
@Service
@RequiredArgsConstructor
public class StandardApplicationService {

    private final StandardApplicationRepository applicationRepository;
    private final ManualExcellencePointRepository manualEpRepository;
    private final GppEffectivenessScoreRepository effectivenessRepository;
    private final eu.dtc4eu.questionnaire.repository.QuestionnaireItemRepository questionnaireItemRepository;
    private final eu.dtc4eu.scoring.repository.FacilityItemScoreRepository itemScoreRepository;
    private final eu.dtc4eu.scoring.repository.FacilityAreaScoreRepository areaScoreRepository;
    private final eu.dtc4eu.scoring.repository.FacilityQuestionnaireScoreRepository questionnaireScoreRepository;
    private final UserRepository userRepository;
    private final FacilityRepository facilityRepository;
    private final FacilityCycleParticipationRepository participationRepository;
    private final AnnualCycleRepository cycleRepository;
    private final FacilityScaleScoreRepository scaleScoreRepository;
    private final QuestionnaireRepository questionnaireRepository;
    private final QuestionnaireAreaRepository areaRepository;
    private final DataScopeService dataScopeService;
    private final CycleContext cycleContext;
    private final AuditService auditService;

    /* ---------------------- document applications (per cycle) ---------------------- */

    @Transactional(readOnly = true)
    public List<DocumentApplicationDto> list(Long facilityId, AppUserDetails principal) {
        facilityInScope(facilityId, principal);
        return applicationRepository
                .findByFacilityIdAndAnnualCycleIdOrderByStandardTypeAscStandardCodeAsc(facilityId,
                        cycleContext.requireId())
                .stream().map(a -> DocumentApplicationDto.of(a, userName(a.getAssessedBy()))).toList();
    }

    @Transactional
    public DocumentApplicationDto create(Long facilityId, SaveDocumentApplicationRequest request,
                                         AppUserDetails actor) {
        facilityInScope(facilityId, actor);
        Long cycleId = cycleContext.requireWritable().getId();
        if (request.standardCode() != null && applicationRepository
                .existsByFacilityIdAndAnnualCycleIdAndStandardTypeAndStandardCode(facilityId, cycleId,
                        request.standardType(), request.standardCode())) {
            throw new ConflictException("This document is already recorded for the facility in this cycle");
        }
        StandardApplication application = new StandardApplication();
        application.setFacilityId(facilityId);
        application.setAnnualCycleId(cycleId);
        apply(application, request, actor);
        application = applicationRepository.save(application);
        auditService.success(actor.getId(), "CREATE", "standard_application", application.getId());
        return DocumentApplicationDto.of(application, userName(application.getAssessedBy()));
    }

    @Transactional
    public DocumentApplicationDto update(Long facilityId, Long applicationId,
                                         SaveDocumentApplicationRequest request, AppUserDetails actor) {
        facilityInScope(facilityId, actor);
        cycleContext.requireWritable();
        StandardApplication application = owned(facilityId, applicationId);
        apply(application, request, actor);
        auditService.success(actor.getId(), "UPDATE", "standard_application", application.getId());
        return DocumentApplicationDto.of(application, userName(application.getAssessedBy()));
    }

    @Transactional
    public void delete(Long facilityId, Long applicationId, AppUserDetails actor) {
        facilityInScope(facilityId, actor);
        cycleContext.requireWritable();
        StandardApplication application = owned(facilityId, applicationId);
        applicationRepository.delete(application);
        auditService.success(actor.getId(), "DELETE", "standard_application", applicationId);
    }

    /* ---------------------------- accreditation history ---------------------------- */

    @Transactional(readOnly = true)
    public FacilityHistoryDto history(Long facilityId, AppUserDetails principal) {
        Facility facility = facilityInScope(facilityId, principal);

        Map<Long, FacilityCycleParticipation> participationByCycle = participationRepository
                .findByFacilityId(facilityId).stream()
                .collect(Collectors.toMap(FacilityCycleParticipation::getAnnualCycleId, Function.identity()));
        Map<Long, List<StandardApplication>> applicationsByCycle = applicationRepository.findByFacilityId(facilityId)
                .stream().collect(Collectors.groupingBy(StandardApplication::getAnnualCycleId));
        List<FacilityScaleScore> epRows = scaleScoreRepository
                .findByFacilityIdAndMetricType(facilityId, FacilityScaleScore.MetricType.EXCELLENCE_POINT).stream()
                .filter(row -> row.getItemLetter() != null)   // item-level EP only (the coordinate lists)
                .toList();

        Map<Long, String> questionnaireCodes = questionnaireRepository.findAll().stream()
                .collect(Collectors.toMap(Questionnaire::getId, Questionnaire::getCode));
        Map<Long, QuestionnaireArea> areas = areaRepository
                .findAllById(epRows.stream().map(FacilityScaleScore::getAreaId).filter(Objects::nonNull).toList())
                .stream().collect(Collectors.toMap(QuestionnaireArea::getId, Function.identity()));

        // cycle → questionnaire code → coordinate labels
        Map<Long, Map<String, List<String>>> epByCycle = new LinkedHashMap<>();
        for (FacilityScaleScore row : epRows) {
            QuestionnaireArea area = row.getAreaId() != null ? areas.get(row.getAreaId()) : null;
            String label = (area != null && area.getSortOrder() != null ? area.getSortOrder() : "?")
                    + "." + row.getItemLetter();
            epByCycle.computeIfAbsent(row.getAnnualCycleId(), c -> new TreeMap<>())
                    .computeIfAbsent(questionnaireCodes.getOrDefault(row.getQuestionnaireId(), "?"),
                            q -> new java.util.ArrayList<>())
                    .add(label);
        }
        // manual entries (register §12.4: historical / ViVaCom EP) merge in verbatim
        for (ManualExcellencePoint manual : manualEpRepository
                .findByFacilityIdOrderByAnnualCycleIdAscQuestionnaireCodeAscLabelAsc(facilityId)) {
            epByCycle.computeIfAbsent(manual.getAnnualCycleId(), c -> new TreeMap<>())
                    .computeIfAbsent(manual.getQuestionnaireCode(), q -> new java.util.ArrayList<>())
                    .add(manual.getLabel());
        }
        epByCycle.values().forEach(byQuestionnaire -> byQuestionnaire.values()
                .forEach(labels -> labels.sort(Comparator.naturalOrder())));

        Set<Long> cycleIds = new HashSet<>();
        cycleIds.addAll(epByCycle.keySet());
        cycleIds.addAll(participationByCycle.keySet());
        cycleIds.addAll(applicationsByCycle.keySet());
        cycleIds.addAll(epByCycle.keySet());

        List<AnnualCycle> ordered = cycleRepository.findAllById(cycleIds).stream()
                .sorted(Comparator.comparing(AnnualCycle::getStartDate, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(AnnualCycle::getCode))
                .toList();
        // the ordinal is per facility (Simone's history tables: "1ST 2023-24 … 4TH 2026-27"):
        // the n-th cycle this facility took part in, by start date
        Map<Long, Integer> ordinalOf = new HashMap<>();
        for (AnnualCycle cycle : ordered) {
            if (participationByCycle.containsKey(cycle.getId())) {
                ordinalOf.put(cycle.getId(), ordinalOf.size() + 1);
            }
        }
        List<HistoryCycleDto> cycles = ordered.stream()
                .map(cycle -> new HistoryCycleDto(cycle.getId(), cycle.getCode(), ordinalOf.get(cycle.getId()),
                        participationByCycle.containsKey(cycle.getId())
                                ? participationByCycle.get(cycle.getId()).getActivity().name() : null,
                        epByCycle.getOrDefault(cycle.getId(), Map.of()),
                        applicationsByCycle.getOrDefault(cycle.getId(), List.of()).stream()
                                .map(DocumentApplicationDto::of).toList()))
                .toList();

        return new FacilityHistoryDto(facility.getId(), facility.getFacilityCode(),
                participationByCycle.size(), cycles);
    }

    /* ------------------------- manual excellence points ------------------------- */

    @Transactional(readOnly = true)
    public List<ManualEpDto> manualEps(Long facilityId, AppUserDetails principal) {
        facilityInScope(facilityId, principal);
        return manualEpRepository.findByFacilityIdOrderByAnnualCycleIdAscQuestionnaireCodeAscLabelAsc(facilityId)
                .stream().map(p -> new ManualEpDto(p.getId(), p.getAnnualCycleId(), p.getQuestionnaireCode(),
                        p.getLabel())).toList();
    }

    /**
     * Replaces the manual EP labels of one facility+cycle+questionnaire.
     * Deliberately NOT gated on a writable cycle: the whole point is
     * backfilling historical (closed) cycles.
     */
    @Transactional
    public List<ManualEpDto> putManualEps(Long facilityId, Long cycleId, String questionnaireCode,
                                          List<String> labels, AppUserDetails actor) {
        facilityInScope(facilityId, actor);
        if (!cycleRepository.existsById(cycleId)) {
            throw NotFoundException.of("Cycle", cycleId);
        }
        String code = questionnaireCode.trim().toUpperCase();
        manualEpRepository.deleteByFacilityIdAndAnnualCycleIdAndQuestionnaireCode(facilityId, cycleId, code);
        for (String label : labels.stream().map(String::trim).filter(l -> !l.isEmpty()).distinct().toList()) {
            ManualExcellencePoint point = new ManualExcellencePoint();
            point.setFacilityId(facilityId);
            point.setAnnualCycleId(cycleId);
            point.setQuestionnaireCode(code);
            point.setLabel(label);
            point.setCreatedBy(actor.getId());
            manualEpRepository.save(point);
        }
        auditService.success(actor.getId(), "PUT_MANUAL_EP", "facility", facilityId,
                Map.of("cycleId", cycleId, "questionnaire", code, "labels", labels.size()));
        return manualEps(facilityId, actor);
    }

    public record ManualEpDto(Long id, Long cycleId, String questionnaireCode, String label) {
    }

    /* --------------------------- GPP effectiveness (Module 6) --------------------------- */

    public record EffectivenessDto(Long questionnaireItemId, String itemCode,
                                   java.math.BigDecimal individualEffectiveness,
                                   java.math.BigDecimal meanItemEffectiveness,
                                   java.math.BigDecimal meanAreaEffectiveness,
                                   java.math.BigDecimal meanQuestEffectiveness,
                                   Integer areasWithScore, Integer areasTotal,
                                   java.time.OffsetDateTime computedAt) {
    }

    @Transactional(readOnly = true)
    public EffectivenessDto getEffectiveness(Long facilityId, Long applicationId, AppUserDetails principal) {
        facilityInScope(facilityId, principal);
        StandardApplication application = owned(facilityId, applicationId);
        return effectivenessRepository.findFirstByStandardApplicationIdOrderByIdDesc(application.getId())
                .map(this::effectivenessDto)
                .orElse(null);
    }

    /**
     * Links a GPP application to its corresponding questionnaire item and
     * snapshots the effectiveness levels from the Phase-1 scoring tables.
     * ASSUMPTION (flagged to the client): individual = the facility's item
     * final score, mean item = its Aver-I, mean area = the area final, mean
     * questionnaire = the facility overall.
     */
    @Transactional
    public EffectivenessDto linkEffectiveness(Long facilityId, Long applicationId, Long questionnaireItemId,
                                              AppUserDetails actor) {
        facilityInScope(facilityId, actor);
        cycleContext.requireWritable();
        StandardApplication application = owned(facilityId, applicationId);
        if (application.getStandardType() != StandardApplication.StandardType.GPP) {
            throw new eu.dtc4eu.common.error.BadRequestException(
                    "Effectiveness scoring applies to GPP documents only");
        }
        eu.dtc4eu.questionnaire.entity.QuestionnaireItem item = questionnaireItemRepository
                .findById(questionnaireItemId)
                .orElseThrow(() -> NotFoundException.of("Questionnaire item", questionnaireItemId));

        Long cycleId = application.getAnnualCycleId();
        Long questionnaireId = item.getQuestionnaireId();

        java.math.BigDecimal individual = null;
        java.math.BigDecimal meanItem = null;
        for (eu.dtc4eu.scoring.entity.FacilityItemScore score : itemScoreRepository
                .findByFacilityIdAndAnnualCycleIdAndQuestionnaireId(facilityId, cycleId, questionnaireId)) {
            if (Objects.equals(score.getAreaId(), item.getAreaId())
                    && Objects.equals(score.getItemLetter(), item.getItemLetter())) {
                individual = score.getFinalScore();
                meanItem = score.getAverI();
                break;
            }
        }
        java.math.BigDecimal meanArea = null;
        int areasWithScore = 0;
        int areasTotal = 0;
        for (eu.dtc4eu.scoring.entity.FacilityAreaScore score : areaScoreRepository
                .findByFacilityIdAndAnnualCycleIdAndQuestionnaireId(facilityId, cycleId, questionnaireId)) {
            areasTotal++;
            if (score.getFinalScore() != null) {
                areasWithScore++;
            }
            if (Objects.equals(score.getAreaId(), item.getAreaId())) {
                meanArea = score.getFinalScore();
            }
        }
        java.math.BigDecimal meanQuest = questionnaireScoreRepository
                .findByFacilityIdAndAnnualCycleIdAndQuestionnaireId(facilityId, cycleId, questionnaireId)
                .map(eu.dtc4eu.scoring.entity.FacilityQuestionnaireScore::getOverallScore)
                .orElse(null);

        effectivenessRepository.deleteByStandardApplicationId(application.getId());
        GppEffectivenessScore snapshot = new GppEffectivenessScore();
        snapshot.setStandardApplicationId(application.getId());
        snapshot.setQuestionnaireItemId(questionnaireItemId);
        snapshot.setIndividualEffectiveness(individual);
        snapshot.setMeanItemEffectiveness(meanItem);
        snapshot.setMeanAreaEffectiveness(meanArea);
        snapshot.setMeanQuestEffectiveness(meanQuest);
        snapshot.setAreasWithScore(areasWithScore);
        snapshot.setAreasTotal(areasTotal);
        effectivenessRepository.save(snapshot);
        auditService.success(actor.getId(), "LINK_GPP_EFFECTIVENESS", "standard_application", applicationId,
                Map.of("itemId", questionnaireItemId));
        return effectivenessDto(snapshot);
    }

    private EffectivenessDto effectivenessDto(GppEffectivenessScore score) {
        String itemCode = score.getQuestionnaireItemId() == null ? null
                : questionnaireItemRepository.findById(score.getQuestionnaireItemId())
                        .map(eu.dtc4eu.questionnaire.entity.QuestionnaireItem::getItemCode).orElse(null);
        return new EffectivenessDto(score.getQuestionnaireItemId(), itemCode,
                score.getIndividualEffectiveness(), score.getMeanItemEffectiveness(),
                score.getMeanAreaEffectiveness(), score.getMeanQuestEffectiveness(),
                score.getAreasWithScore(), score.getAreasTotal(), score.getComputedAt());
    }

    /* ---------------------------------- internals ---------------------------------- */

    private void apply(StandardApplication application, SaveDocumentApplicationRequest request,
                       AppUserDetails actor) {
        application.setStandardType(request.standardType());
        application.setStandardCode(request.standardCode());
        application.setServiceName(request.serviceName());
        application.setServiceType(request.serviceType());
        application.setNumUsers(request.numUsers());
        application.setNumStaff(request.numStaff());
        application.setOrigin(request.origin());
        application.setApplicationCyclesCount(request.applicationCyclesCount());
        application.setApplicationLevelPct(request.applicationLevelPct());
        application.setAssessedBy(actor.getId());
        application.setAssessedAt(OffsetDateTime.now());
    }

    String userName(Long userId) {
        return userId == null ? null
                : userRepository.findById(userId).map(u -> u.getFullName()).orElse(null);
    }

    private StandardApplication owned(Long facilityId, Long applicationId) {
        return applicationRepository.findById(applicationId)
                .filter(a -> Objects.equals(a.getFacilityId(), facilityId)
                        && Objects.equals(a.getAnnualCycleId(), cycleContext.requireId()))
                .orElseThrow(() -> NotFoundException.of("Document application", applicationId));
    }

    private Facility facilityInScope(Long facilityId, AppUserDetails principal) {
        DataScope scope = dataScopeService.scopeOf(principal);
        return facilityRepository.findById(facilityId)
                .filter(f -> scope.canSeeFacility(f.getId()))
                .orElseThrow(() -> NotFoundException.of("Facility", facilityId));
    }
}
