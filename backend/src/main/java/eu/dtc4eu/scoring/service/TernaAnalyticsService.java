package eu.dtc4eu.scoring.service;

import eu.dtc4eu.common.error.NotFoundException;
import eu.dtc4eu.facility.entity.Facility;
import eu.dtc4eu.facility.entity.FacilityMember;
import eu.dtc4eu.facility.repository.FacilityMemberRepository;
import eu.dtc4eu.facility.repository.FacilityRepository;
import eu.dtc4eu.questionnaire.entity.QuestionnaireArea;
import eu.dtc4eu.questionnaire.entity.QuestionnaireResponse;
import eu.dtc4eu.questionnaire.repository.QuestionnaireAreaRepository;
import eu.dtc4eu.questionnaire.repository.QuestionnaireResponseRepository;
import eu.dtc4eu.scoring.dto.ScoringDtos.PopulationCellDto;
import eu.dtc4eu.scoring.dto.ScoringDtos.PopulationRowDto;
import eu.dtc4eu.scoring.dto.ScoringDtos.TernaBenchmarkRowDto;
import eu.dtc4eu.scoring.dto.ScoringDtos.TernaPopulationDto;
import eu.dtc4eu.scoring.entity.Classification;
import eu.dtc4eu.scoring.repository.TernaBenchmarkAreaRepository;
import eu.dtc4eu.security.AppUserDetails;
import eu.dtc4eu.security.CycleContext;
import eu.dtc4eu.security.DataScope;
import eu.dtc4eu.security.DataScopeService;
import eu.dtc4eu.terna.entity.Terna;
import eu.dtc4eu.terna.entity.TernaFacility;
import eu.dtc4eu.terna.repository.TernaFacilityRepository;
import eu.dtc4eu.terna.repository.TernaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Terna-level analytics (v0.2 register §3.4 benchmark areas and §3.6
 * population table TAB.2).
 */
@Service
@RequiredArgsConstructor
public class TernaAnalyticsService {

    /** The four individual categories of TAB.2 (collective forms are excluded from the coverage counts). */
    private static final List<QuestionnaireResponse.RespondentCategory> INDIVIDUAL_CATEGORIES = List.of(
            QuestionnaireResponse.RespondentCategory.USER, QuestionnaireResponse.RespondentCategory.FAMILY,
            QuestionnaireResponse.RespondentCategory.STAFF, QuestionnaireResponse.RespondentCategory.MANAGER);

    private final TernaRepository ternaRepository;
    private final TernaFacilityRepository ternaFacilityRepository;
    private final TernaBenchmarkAreaRepository benchmarkRepository;
    private final QuestionnaireAreaRepository areaRepository;
    private final QuestionnaireResponseRepository responseRepository;
    private final FacilityRepository facilityRepository;
    private final FacilityMemberRepository memberRepository;
    private final FacilityScoringService facilityScoringService;
    private final DataScopeService dataScopeService;
    private final CycleContext cycleContext;

    /**
     * Benchmark Strength Areas of the terna. Recomputed idempotently on read
     * so terna-membership changes after the last submit are reflected.
     */
    @Transactional
    public List<TernaBenchmarkRowDto> benchmarks(Long ternaId, Long questionnaireId, AppUserDetails principal) {
        Terna terna = findInScope(ternaId, principal);
        facilityScoringService.refreshBenchmarks(terna, questionnaireId, OffsetDateTime.now());

        Map<Long, QuestionnaireArea> areas = areaRepository
                .findByQuestionnaireIdOrderBySortOrderAscIdAsc(questionnaireId).stream()
                .collect(Collectors.toMap(QuestionnaireArea::getId, Function.identity()));
        Map<Long, String> facilityCodes = facilityCodes(terna);
        Classification.Bands bands = facilityScoringService.bandsOf(questionnaireId);

        return benchmarkRepository.findByTernaIdAndQuestionnaireId(terna.getId(), questionnaireId).stream()
                .map(row -> {
                    QuestionnaireArea area = areas.get(row.getAreaId());
                    return new TernaBenchmarkRowDto(area != null ? area.getSortOrder() : null,
                            area != null ? area.getAreaCode() : null, area != null ? area.getName() : null,
                            row.getBestFacilityId(), facilityCodes.get(row.getBestFacilityId()),
                            row.getBestScore(), Classification.of(row.getBestScore(), bands));
                })
                .sorted(Comparator.comparing(TernaBenchmarkRowDto::areaNo,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    /** TAB.2: collected submitted questionnaires vs the member roster, per facility and category. */
    @Transactional(readOnly = true)
    public TernaPopulationDto population(Long ternaId, Long questionnaireId, AppUserDetails principal) {
        Terna terna = findInScope(ternaId, principal);
        Long cycleId = cycleContext.requireId();
        Map<Long, String> facilityCodes = facilityCodes(terna);

        List<PopulationRowDto> rows = new ArrayList<>();
        Map<String, long[]> totals = new LinkedHashMap<>();
        for (TernaFacility member : ternaFacilityRepository.findByTernaIdOrderByPositionAsc(terna.getId())) {
            Map<String, PopulationCellDto> byCategory = new LinkedHashMap<>();
            long collectedTotal = 0;
            long rosterTotal = 0;
            for (QuestionnaireResponse.RespondentCategory category : INDIVIDUAL_CATEGORIES) {
                long collected = questionnaireId != null
                        ? responseRepository.countByFacilityIdAndAnnualCycleIdAndQuestionnaireIdAndRespondentCategoryAndStatus(
                                member.getFacilityId(), cycleId, questionnaireId, category,
                                QuestionnaireResponse.Status.SUBMITTED)
                        : responseRepository.countByFacilityIdAndAnnualCycleIdAndRespondentCategoryAndStatus(
                                member.getFacilityId(), cycleId, category, QuestionnaireResponse.Status.SUBMITTED);
                long roster = memberRepository.countByFacilityIdAndAnnualCycleIdAndCategory(member.getFacilityId(),
                        cycleId, FacilityMember.Category.valueOf(category.name()));
                byCategory.put(category.name(), new PopulationCellDto(collected, roster));
                collectedTotal += collected;
                rosterTotal += roster;
                totals.computeIfAbsent(category.name(), k -> new long[2]);
                totals.get(category.name())[0] += collected;
                totals.get(category.name())[1] += roster;
            }
            rows.add(new PopulationRowDto(member.getFacilityId(), facilityCodes.get(member.getFacilityId()),
                    byCategory, collectedTotal, rosterTotal));
        }

        Map<String, PopulationCellDto> totalCells = totals.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey,
                        e -> new PopulationCellDto(e.getValue()[0], e.getValue()[1]),
                        (a, b) -> a, LinkedHashMap::new));
        PopulationRowDto totalRow = new PopulationRowDto(null, null, totalCells,
                totalCells.values().stream().mapToLong(PopulationCellDto::collected).sum(),
                totalCells.values().stream().mapToLong(PopulationCellDto::roster).sum());

        return new TernaPopulationDto(terna.getId(), terna.getTernaCode(), rows, totalRow);
    }

    private Terna findInScope(Long id, AppUserDetails principal) {
        DataScope scope = dataScopeService.scopeOf(principal);
        return ternaRepository.findById(id)
                .filter(terna -> terna.getAnnualCycleId().equals(cycleContext.requireId()))
                .filter(terna -> scope.canSeeTerna(terna.getId()))
                .orElseThrow(() -> NotFoundException.of("Terna", id));
    }

    private Map<Long, String> facilityCodes(Terna terna) {
        List<Long> ids = ternaFacilityRepository.findByTernaIdOrderByPositionAsc(terna.getId()).stream()
                .map(TernaFacility::getFacilityId)
                .toList();
        return facilityRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Facility::getId, Facility::getFacilityCode));
    }
}
