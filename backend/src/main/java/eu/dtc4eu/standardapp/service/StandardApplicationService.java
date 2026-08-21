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
import eu.dtc4eu.standardapp.entity.StandardApplication;
import eu.dtc4eu.standardapp.repository.StandardApplicationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
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
                .stream().map(DocumentApplicationDto::of).toList();
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
        apply(application, request);
        application = applicationRepository.save(application);
        auditService.success(actor.getId(), "CREATE", "standard_application", application.getId());
        return DocumentApplicationDto.of(application);
    }

    @Transactional
    public DocumentApplicationDto update(Long facilityId, Long applicationId,
                                         SaveDocumentApplicationRequest request, AppUserDetails actor) {
        facilityInScope(facilityId, actor);
        cycleContext.requireWritable();
        StandardApplication application = owned(facilityId, applicationId);
        apply(application, request);
        auditService.success(actor.getId(), "UPDATE", "standard_application", application.getId());
        return DocumentApplicationDto.of(application);
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
        epByCycle.values().forEach(byQuestionnaire -> byQuestionnaire.values()
                .forEach(labels -> labels.sort(Comparator.naturalOrder())));

        Set<Long> cycleIds = new HashSet<>();
        cycleIds.addAll(participationByCycle.keySet());
        cycleIds.addAll(applicationsByCycle.keySet());
        cycleIds.addAll(epByCycle.keySet());

        List<HistoryCycleDto> cycles = cycleRepository.findAllById(cycleIds).stream()
                .sorted(Comparator.comparing(AnnualCycle::getSequenceNumber,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(AnnualCycle::getStartDate, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(cycle -> new HistoryCycleDto(cycle.getId(), cycle.getCode(), cycle.getSequenceNumber(),
                        participationByCycle.containsKey(cycle.getId())
                                ? participationByCycle.get(cycle.getId()).getActivity().name() : null,
                        epByCycle.getOrDefault(cycle.getId(), Map.of()),
                        applicationsByCycle.getOrDefault(cycle.getId(), List.of()).stream()
                                .map(DocumentApplicationDto::of).toList()))
                .toList();

        return new FacilityHistoryDto(facility.getId(), facility.getFacilityCode(),
                participationByCycle.size(), cycles);
    }

    /* ---------------------------------- internals ---------------------------------- */

    private void apply(StandardApplication application, SaveDocumentApplicationRequest request) {
        application.setStandardType(request.standardType());
        application.setStandardCode(request.standardCode());
        application.setServiceName(request.serviceName());
        application.setServiceType(request.serviceType());
        application.setNumUsers(request.numUsers());
        application.setNumStaff(request.numStaff());
        application.setOrigin(request.origin());
        application.setApplicationCyclesCount(request.applicationCyclesCount());
        application.setApplicationLevelPct(request.applicationLevelPct());
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
