package eu.dtc4eu.scoring.service;

import eu.dtc4eu.common.error.NotFoundException;
import eu.dtc4eu.facility.entity.Facility;
import eu.dtc4eu.facility.repository.FacilityRepository;
import eu.dtc4eu.questionnaire.entity.Questionnaire;
import eu.dtc4eu.questionnaire.entity.QuestionnaireArea;
import eu.dtc4eu.questionnaire.entity.QuestionnaireItem;
import eu.dtc4eu.questionnaire.entity.QuestionnaireResponse;
import eu.dtc4eu.questionnaire.entity.QuestionnaireResponseItem;
import eu.dtc4eu.questionnaire.repository.QuestionnaireAreaRepository;
import eu.dtc4eu.questionnaire.repository.QuestionnaireItemRepository;
import eu.dtc4eu.questionnaire.repository.QuestionnaireRepository;
import eu.dtc4eu.questionnaire.repository.QuestionnaireResponseItemRepository;
import eu.dtc4eu.questionnaire.repository.QuestionnaireResponseRepository;
import eu.dtc4eu.scoring.dto.ScoringDtos.AreaScoreRowDto;
import eu.dtc4eu.scoring.dto.ScoringDtos.FacilityScoresDto;
import eu.dtc4eu.scoring.dto.ScoringDtos.ItemScoreRowDto;
import eu.dtc4eu.scoring.dto.ScoringDtos.SummaryTableDto;
import eu.dtc4eu.scoring.entity.Classification;
import eu.dtc4eu.scoring.entity.FacilityAreaScore;
import eu.dtc4eu.scoring.entity.FacilityItemScore;
import eu.dtc4eu.scoring.entity.FacilityQuestionnaireScore;
import eu.dtc4eu.scoring.entity.FacilityScaleScore;
import eu.dtc4eu.scoring.entity.TernaBenchmarkArea;
import eu.dtc4eu.scoring.repository.FacilityAreaScoreRepository;
import eu.dtc4eu.scoring.repository.FacilityItemScoreRepository;
import eu.dtc4eu.scoring.repository.FacilityQuestionnaireScoreRepository;
import eu.dtc4eu.scoring.repository.FacilityScaleScoreRepository;
import eu.dtc4eu.scoring.repository.TernaBenchmarkAreaRepository;
import eu.dtc4eu.scoring.service.FacilityScoringEngine.Coordinate;
import eu.dtc4eu.scoring.service.FacilityScoringEngine.Scores;
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

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Facility-cycle scoring engine orchestration (v0.2 register §3): builds the
 * engine input from submitted responses, rewrites the derived grids and point
 * metrics idempotently, and keeps the terna benchmark areas fresh. Runs after
 * every submit and on demand via the recompute endpoint.
 */
@Service
@RequiredArgsConstructor
public class FacilityScoringService {

    private static final Set<FacilityScaleScore.MetricType> ENGINE_OWNED_METRICS = Set.of(
            FacilityScaleScore.MetricType.QUANT_STRENGTH,
            FacilityScaleScore.MetricType.QUANT_WEAKNESS,
            FacilityScaleScore.MetricType.EXCELLENCE_POINT);

    private final QuestionnaireRepository questionnaireRepository;
    private final QuestionnaireAreaRepository areaRepository;
    private final QuestionnaireItemRepository itemRepository;
    private final QuestionnaireResponseRepository responseRepository;
    private final QuestionnaireResponseItemRepository responseItemRepository;
    private final FacilityRepository facilityRepository;
    private final FacilityItemScoreRepository itemScoreRepository;
    private final FacilityAreaScoreRepository areaScoreRepository;
    private final FacilityQuestionnaireScoreRepository questionnaireScoreRepository;
    private final FacilityScaleScoreRepository scaleScoreRepository;
    private final TernaBenchmarkAreaRepository benchmarkRepository;
    private final TernaRepository ternaRepository;
    private final TernaFacilityRepository ternaFacilityRepository;
    private final DataScopeService dataScopeService;
    private final CycleContext cycleContext;

    /* -------------------------------- recompute -------------------------------- */

    @Transactional
    public void recompute(Long facilityId, Long cycleId, Long questionnaireId) {
        Map<Long, List<Coordinate>> areaCoordinates = areaCoordinates(questionnaireId);
        Map<Long, Coordinate> coordinateByItemId = coordinateByItemId(questionnaireId);

        List<QuestionnaireResponse> responses = responseRepository
                .findByFacilityIdAndAnnualCycleIdAndQuestionnaireIdAndStatus(facilityId, cycleId, questionnaireId,
                        QuestionnaireResponse.Status.SUBMITTED);
        Map<Long, List<QuestionnaireResponseItem>> answersByResponse = responses.isEmpty() ? Map.of()
                : responseItemRepository.findByResponseIdIn(responses.stream().map(QuestionnaireResponse::getId).toList())
                        .stream().collect(Collectors.groupingBy(QuestionnaireResponseItem::getResponseId));

        Map<QuestionnaireResponse.RespondentCategory, List<Map<Coordinate, Integer>>> input =
                new EnumMap<>(QuestionnaireResponse.RespondentCategory.class);
        for (QuestionnaireResponse response : responses) {
            Map<Coordinate, Integer> values = new LinkedHashMap<>();
            for (QuestionnaireResponseItem answer : answersByResponse.getOrDefault(response.getId(), List.of())) {
                Coordinate coordinate = coordinateByItemId.get(answer.getItemId());
                if (coordinate != null && answer.getNumericValue() != null) {
                    values.put(coordinate, answer.getNumericValue());
                }
            }
            input.computeIfAbsent(response.getRespondentCategory(), c -> new ArrayList<>()).add(values);
        }

        FacilityScoringEngine.Result result = FacilityScoringEngine.compute(input, areaCoordinates);
        OffsetDateTime now = OffsetDateTime.now();

        persistGrids(facilityId, cycleId, questionnaireId, result, now);
        persistPointMetrics(facilityId, cycleId, questionnaireId, result, now);

        // Benchmarks of every terna this facility belongs to in the cycle stay
        // as fresh as the last submit.
        for (Terna terna : ternasOf(facilityId, cycleId)) {
            refreshBenchmarks(terna, questionnaireId, now);
        }
    }

    /** Rewrites terna_benchmark_areas for one terna from the members' persisted area finals. */
    @Transactional
    public void refreshBenchmarks(Terna terna, Long questionnaireId, OffsetDateTime now) {
        List<TernaFacility> members = ternaFacilityRepository.findByTernaIdOrderByPositionAsc(terna.getId());
        List<Long> facilityIds = members.stream().map(TernaFacility::getFacilityId).toList();
        Map<Long, Integer> positionByFacility = members.stream().collect(Collectors.toMap(
                TernaFacility::getFacilityId, m -> m.getPosition() != null ? m.getPosition() : Integer.MAX_VALUE));

        Map<Long, List<FacilityAreaScore>> byArea = facilityIds.isEmpty() ? Map.of()
                : areaScoreRepository.findByFacilityIdInAndAnnualCycleIdAndQuestionnaireId(facilityIds,
                        terna.getAnnualCycleId(), questionnaireId).stream()
                        .filter(score -> score.getFinalScore() != null)
                        .collect(Collectors.groupingBy(FacilityAreaScore::getAreaId));

        benchmarkRepository.deleteByTernaIdAndQuestionnaireId(terna.getId(), questionnaireId);
        for (Map.Entry<Long, List<FacilityAreaScore>> entry : byArea.entrySet()) {
            FacilityAreaScore best = entry.getValue().stream()
                    .max(Comparator.comparing(FacilityAreaScore::getFinalScore)
                            // tie → lower terna position wins (documented open point for the client)
                            .thenComparing(score -> -positionByFacility.getOrDefault(score.getFacilityId(),
                                    Integer.MAX_VALUE)))
                    .orElse(null);
            if (best == null) {
                continue;
            }
            TernaBenchmarkArea row = new TernaBenchmarkArea();
            row.setTernaId(terna.getId());
            row.setQuestionnaireId(questionnaireId);
            row.setAreaId(entry.getKey());
            row.setBestFacilityId(best.getFacilityId());
            row.setBestScore(best.getFinalScore());
            row.setComputedAt(now);
            benchmarkRepository.save(row);
        }

        rewriteExcellencePoints(terna, questionnaireId, facilityIds, now);
    }

    /**
     * Expert feedback (Phase 1 prototype review): EP = the facility's
     * quantitative strength POINTS that fall inside its benchmark areas —
     * the areas where it is the best of its terna. Rewritten together with
     * the benchmarks so a sibling facility's submit keeps every member's EP
     * consistent.
     */
    private void rewriteExcellencePoints(Terna terna, Long questionnaireId, List<Long> facilityIds,
                                         OffsetDateTime now) {
        Map<Long, Set<Long>> wonAreasByFacility = benchmarkRepository
                .findByTernaIdAndQuestionnaireId(terna.getId(), questionnaireId).stream()
                .collect(Collectors.groupingBy(TernaBenchmarkArea::getBestFacilityId,
                        Collectors.mapping(TernaBenchmarkArea::getAreaId, Collectors.toSet())));

        for (Long memberId : facilityIds) {
            scaleScoreRepository.deleteByFacilityIdAndAnnualCycleIdAndQuestionnaireIdAndMetricTypeIn(memberId,
                    terna.getAnnualCycleId(), questionnaireId,
                    List.of(FacilityScaleScore.MetricType.EXCELLENCE_POINT));
            Set<Long> wonAreas = wonAreasByFacility.getOrDefault(memberId, Set.of());
            if (wonAreas.isEmpty()) {
                continue;
            }
            scaleScoreRepository
                    .findByFacilityIdAndAnnualCycleIdAndQuestionnaireIdAndMetricTypeIn(memberId,
                            terna.getAnnualCycleId(), questionnaireId,
                            List.of(FacilityScaleScore.MetricType.QUANT_STRENGTH)).stream()
                    .filter(point -> point.getItemLetter() != null && wonAreas.contains(point.getAreaId()))
                    .forEach(point -> savePoint(memberId, terna.getAnnualCycleId(), questionnaireId,
                            FacilityScaleScore.MetricType.EXCELLENCE_POINT, point.getAreaId(),
                            point.getItemLetter(), point.getValue(), now));
        }
    }

    /* ---------------------------------- reads ----------------------------------- */

    @Transactional(readOnly = true)
    public FacilityScoresDto getScores(Long facilityId, Long questionnaireId, AppUserDetails principal) {
        Facility facility = facilityInScope(facilityId, principal);
        Questionnaire questionnaire = questionnaireRepository.findById(questionnaireId)
                .orElseThrow(() -> NotFoundException.of("Questionnaire", questionnaireId));
        Long cycleId = cycleContext.requireId();

        Map<Long, QuestionnaireArea> areas = areaMap(questionnaireId);

        List<ItemScoreRowDto> itemRows = itemScoreRepository
                .findByFacilityIdAndAnnualCycleIdAndQuestionnaireId(facilityId, cycleId, questionnaireId).stream()
                .map(score -> itemRow(score, areas.get(score.getAreaId())))
                .sorted(Comparator.comparing(ItemScoreRowDto::areaNo, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(ItemScoreRowDto::itemLetter))
                .toList();
        List<AreaScoreRowDto> areaRows = areaScoreRepository
                .findByFacilityIdAndAnnualCycleIdAndQuestionnaireId(facilityId, cycleId, questionnaireId).stream()
                .map(score -> areaRow(score, areas.get(score.getAreaId())))
                .sorted(Comparator.comparing(AreaScoreRowDto::areaNo, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        Optional<FacilityQuestionnaireScore> overall = questionnaireScoreRepository
                .findByFacilityIdAndAnnualCycleIdAndQuestionnaireId(facilityId, cycleId, questionnaireId);

        List<FacilityScaleScore> points = scaleScoreRepository
                .findByFacilityIdAndAnnualCycleIdAndQuestionnaireIdAndMetricTypeIn(facilityId, cycleId,
                        questionnaireId, ENGINE_OWNED_METRICS);

        return new FacilityScoresDto(facility.getId(), facility.getFacilityCode(), questionnaire.getId(),
                questionnaire.getCode(),
                overall.map(FacilityQuestionnaireScore::getComputedAt).orElse(null),
                overall.map(FacilityQuestionnaireScore::getOverallScore).orElse(null),
                overall.map(FacilityQuestionnaireScore::getClassification).orElse(null),
                areaRows, itemRows,
                pointLabels(points, FacilityScaleScore.MetricType.QUANT_STRENGTH, true, areas),
                pointLabels(points, FacilityScaleScore.MetricType.QUANT_WEAKNESS, true, areas),
                pointLabels(points, FacilityScaleScore.MetricType.QUANT_STRENGTH, false, areas),
                pointLabels(points, FacilityScaleScore.MetricType.QUANT_WEAKNESS, false, areas));
    }

    @Transactional(readOnly = true)
    public SummaryTableDto getSummary(Long facilityId, Long questionnaireId, AppUserDetails principal) {
        facilityInScope(facilityId, principal);
        Long cycleId = cycleContext.requireId();
        Map<Long, QuestionnaireArea> areas = areaMap(questionnaireId);

        List<FacilityScaleScore> points = scaleScoreRepository
                .findByFacilityIdAndAnnualCycleIdAndQuestionnaireIdAndMetricTypeIn(facilityId, cycleId,
                        questionnaireId, ENGINE_OWNED_METRICS);

        // Benchmark areas OF THIS FACILITY: areas where it is the best of its terna.
        List<Integer> benchmarkAreaNos = ternasOf(facilityId, cycleId).stream()
                .flatMap(terna -> benchmarkRepository.findByTernaIdAndQuestionnaireId(terna.getId(), questionnaireId)
                        .stream())
                .filter(row -> Objects.equals(row.getBestFacilityId(), facilityId))
                .map(row -> areaNo(areas.get(row.getAreaId())))
                .filter(Objects::nonNull)
                .sorted()
                .toList();

        // EP rows are already restricted to the facility's benchmark areas
        // (expert feedback rule); EP-Bench is a narrower subset whose exact
        // rule is still an open question with the client — served as null.
        List<String> excellencePoints = pointLabels(points, FacilityScaleScore.MetricType.EXCELLENCE_POINT, true,
                areas);

        return new SummaryTableDto(
                null, null,
                pointLabels(points, FacilityScaleScore.MetricType.QUANT_WEAKNESS, false, areas),
                pointLabels(points, FacilityScaleScore.MetricType.QUANT_WEAKNESS, true, areas),
                null, null,
                pointLabels(points, FacilityScaleScore.MetricType.QUANT_STRENGTH, false, areas),
                pointLabels(points, FacilityScaleScore.MetricType.QUANT_STRENGTH, true, areas),
                benchmarkAreaNos.stream().map(String::valueOf).toList(),
                excellencePoints, null);
    }

    /* -------------------------------- internals --------------------------------- */

    private void persistGrids(Long facilityId, Long cycleId, Long questionnaireId,
                              FacilityScoringEngine.Result result, OffsetDateTime now) {
        itemScoreRepository.deleteByFacilityIdAndAnnualCycleIdAndQuestionnaireId(facilityId, cycleId, questionnaireId);
        areaScoreRepository.deleteByFacilityIdAndAnnualCycleIdAndQuestionnaireId(facilityId, cycleId, questionnaireId);
        questionnaireScoreRepository.deleteByFacilityIdAndAnnualCycleIdAndQuestionnaireId(facilityId, cycleId,
                questionnaireId);

        for (Map.Entry<Coordinate, Scores> entry : result.items().entrySet()) {
            FacilityItemScore row = new FacilityItemScore();
            row.setFacilityId(facilityId);
            row.setAnnualCycleId(cycleId);
            row.setQuestionnaireId(questionnaireId);
            row.setAreaId(entry.getKey().areaId());
            row.setItemLetter(entry.getKey().letter());
            apply(entry.getValue(), row::setUserMean, row::setFamilyMean, row::setStaffMean, row::setManagerMean,
                    row::setAverI, row::setComValue, row::setDelValue, row::setFinalScore);
            row.setClassification(entry.getValue().classification());
            row.setComputedAt(now);
            itemScoreRepository.save(row);
        }
        for (Map.Entry<Long, Scores> entry : result.areas().entrySet()) {
            FacilityAreaScore row = new FacilityAreaScore();
            row.setFacilityId(facilityId);
            row.setAnnualCycleId(cycleId);
            row.setQuestionnaireId(questionnaireId);
            row.setAreaId(entry.getKey());
            apply(entry.getValue(), row::setUserMean, row::setFamilyMean, row::setStaffMean, row::setManagerMean,
                    row::setAverI, row::setComValue, row::setDelValue, row::setFinalScore);
            row.setClassification(entry.getValue().classification());
            row.setComputedAt(now);
            areaScoreRepository.save(row);
        }
        FacilityQuestionnaireScore overall = new FacilityQuestionnaireScore();
        overall.setFacilityId(facilityId);
        overall.setAnnualCycleId(cycleId);
        overall.setQuestionnaireId(questionnaireId);
        overall.setOverallScore(result.overall());
        overall.setClassification(result.overallClassification());
        overall.setComputedAt(now);
        questionnaireScoreRepository.save(overall);
    }

    /**
     * Point metrics (register §3.4): strength = EFFICACY, weakness = CRITICAL,
     * at item (coordinate) and area level. Excellence Points currently equal
     * the item-level quantitative strength set — the register guarantees EP is
     * computable without QCNC data; the exact qualification rule is flagged to
     * the client and kept in this single place.
     */
    private void persistPointMetrics(Long facilityId, Long cycleId, Long questionnaireId,
                                     FacilityScoringEngine.Result result, OffsetDateTime now) {
        scaleScoreRepository.deleteByFacilityIdAndAnnualCycleIdAndQuestionnaireIdAndMetricTypeIn(facilityId, cycleId,
                questionnaireId, ENGINE_OWNED_METRICS);

        for (Map.Entry<Coordinate, Scores> entry : result.items().entrySet()) {
            Classification classification = entry.getValue().classification();
            if (classification == Classification.EFFICACY) {
                savePoint(facilityId, cycleId, questionnaireId, FacilityScaleScore.MetricType.QUANT_STRENGTH,
                        entry.getKey().areaId(), entry.getKey().letter(), entry.getValue().finalScore(), now);
            } else if (classification == Classification.CRITICAL) {
                savePoint(facilityId, cycleId, questionnaireId, FacilityScaleScore.MetricType.QUANT_WEAKNESS,
                        entry.getKey().areaId(), entry.getKey().letter(), entry.getValue().finalScore(), now);
            }
        }
        for (Map.Entry<Long, Scores> entry : result.areas().entrySet()) {
            Classification classification = entry.getValue().classification();
            if (classification == Classification.EFFICACY) {
                savePoint(facilityId, cycleId, questionnaireId, FacilityScaleScore.MetricType.QUANT_STRENGTH,
                        entry.getKey(), null, entry.getValue().finalScore(), now);
            } else if (classification == Classification.CRITICAL) {
                savePoint(facilityId, cycleId, questionnaireId, FacilityScaleScore.MetricType.QUANT_WEAKNESS,
                        entry.getKey(), null, entry.getValue().finalScore(), now);
            }
        }
    }

    private void savePoint(Long facilityId, Long cycleId, Long questionnaireId,
                           FacilityScaleScore.MetricType metricType, Long areaId, String letter, BigDecimal value,
                           OffsetDateTime now) {
        FacilityScaleScore row = new FacilityScaleScore();
        row.setFacilityId(facilityId);
        row.setAnnualCycleId(cycleId);
        row.setQuestionnaireId(questionnaireId);
        row.setMetricType(metricType);
        row.setAreaId(areaId);
        row.setItemLetter(letter);
        row.setValue(value);
        row.setComputedAt(now);
        scaleScoreRepository.save(row);
    }

    /** The master grid from the item definitions: per area (display order), the distinct letters in order. */
    private Map<Long, List<Coordinate>> areaCoordinates(Long questionnaireId) {
        List<QuestionnaireArea> areas = areaRepository.findByQuestionnaireIdOrderBySortOrderAscIdAsc(questionnaireId);
        Map<Long, List<Coordinate>> grid = new LinkedHashMap<>();
        Map<Long, Set<String>> lettersByArea = itemRepository
                .findByQuestionnaireIdOrderBySortOrderAscIdAsc(questionnaireId).stream()
                .filter(this::isCoordinateItem)
                .collect(Collectors.groupingBy(QuestionnaireItem::getAreaId,
                        Collectors.mapping(QuestionnaireItem::getItemLetter, Collectors.toCollection(
                                java.util.TreeSet::new))));
        for (QuestionnaireArea area : areas) {
            List<Coordinate> coordinates = lettersByArea.getOrDefault(area.getId(), Set.of()).stream()
                    .sorted()
                    .map(letter -> new Coordinate(area.getId(), letter))
                    .toList();
            if (!coordinates.isEmpty()) {
                grid.put(area.getId(), coordinates);
            }
        }
        return grid;
    }

    private Map<Long, Coordinate> coordinateByItemId(Long questionnaireId) {
        return itemRepository.findByQuestionnaireIdOrderBySortOrderAscIdAsc(questionnaireId).stream()
                .filter(this::isCoordinateItem)
                .collect(Collectors.toMap(QuestionnaireItem::getId,
                        item -> new Coordinate(item.getAreaId(), item.getItemLetter())));
    }

    private boolean isCoordinateItem(QuestionnaireItem item) {
        return item.getResponseType() == QuestionnaireItem.ResponseType.LIKERT
                && item.getAreaId() != null && item.getItemLetter() != null;
    }

    private List<Terna> ternasOf(Long facilityId, Long cycleId) {
        List<Long> ternaIds = ternaFacilityRepository.findByFacilityId(facilityId).stream()
                .map(TernaFacility::getTernaId)
                .toList();
        if (ternaIds.isEmpty()) {
            return List.of();
        }
        return ternaRepository.findAllById(ternaIds).stream()
                .filter(terna -> terna.getAnnualCycleId().equals(cycleId))
                .toList();
    }

    /** Standard visibility rule: out-of-scope facilities read as 404. */
    public Facility facilityInScope(Long facilityId, AppUserDetails principal) {
        DataScope scope = dataScopeService.scopeOf(principal);
        return facilityRepository.findById(facilityId)
                .filter(f -> scope.canSeeFacility(f.getId()))
                .orElseThrow(() -> NotFoundException.of("Facility", facilityId));
    }

    private Map<Long, QuestionnaireArea> areaMap(Long questionnaireId) {
        return areaRepository.findByQuestionnaireIdOrderBySortOrderAscIdAsc(questionnaireId).stream()
                .collect(Collectors.toMap(QuestionnaireArea::getId, Function.identity()));
    }

    private Integer areaNo(QuestionnaireArea area) {
        return area != null ? area.getSortOrder() : null;
    }

    private ItemScoreRowDto itemRow(FacilityItemScore score, QuestionnaireArea area) {
        Integer areaNo = areaNo(area);
        String coordinate = (areaNo != null ? areaNo : "?") + "." + score.getItemLetter();
        return new ItemScoreRowDto(score.getAreaId(), area != null ? area.getAreaCode() : null, areaNo,
                score.getItemLetter(), coordinate, score.getUserMean(), score.getFamilyMean(), score.getStaffMean(),
                score.getManagerMean(), score.getAverI(), score.getComValue(), score.getDelValue(),
                score.getFinalScore(), score.getClassification());
    }

    private AreaScoreRowDto areaRow(FacilityAreaScore score, QuestionnaireArea area) {
        return new AreaScoreRowDto(score.getAreaId(), area != null ? area.getAreaCode() : null,
                area != null ? area.getName() : null, areaNo(area), score.getUserMean(), score.getFamilyMean(),
                score.getStaffMean(), score.getManagerMean(), score.getAverI(), score.getComValue(),
                score.getDelValue(), score.getFinalScore(), score.getClassification());
    }

    /** "n.letter" labels for item points, "n" labels for area points, ordered. */
    private List<String> pointLabels(List<FacilityScaleScore> points, FacilityScaleScore.MetricType type,
                                     boolean itemLevel, Map<Long, QuestionnaireArea> areas) {
        return points.stream()
                .filter(p -> p.getMetricType() == type && (p.getItemLetter() != null) == itemLevel)
                .map(p -> {
                    Integer areaNo = areaNo(areas.get(p.getAreaId()));
                    String no = areaNo != null ? String.valueOf(areaNo) : "?";
                    return itemLevel ? no + "." + p.getItemLetter() : no;
                })
                .sorted(Comparator.comparing((String label) -> {
                    int dot = label.indexOf('.');
                    String no = dot > 0 ? label.substring(0, dot) : label;
                    return no.matches("\\d+") ? Integer.parseInt(no) : Integer.MAX_VALUE;
                }).thenComparing(Function.identity()))
                .toList();
    }

    private void apply(Scores scores,
                       java.util.function.Consumer<BigDecimal> user, java.util.function.Consumer<BigDecimal> family,
                       java.util.function.Consumer<BigDecimal> staff, java.util.function.Consumer<BigDecimal> manager,
                       java.util.function.Consumer<BigDecimal> averI, java.util.function.Consumer<BigDecimal> com,
                       java.util.function.Consumer<BigDecimal> del, java.util.function.Consumer<BigDecimal> fin) {
        user.accept(scores.userMean());
        family.accept(scores.familyMean());
        staff.accept(scores.staffMean());
        manager.accept(scores.managerMean());
        averI.accept(scores.averI());
        com.accept(scores.com());
        del.accept(scores.del());
        fin.accept(scores.finalScore());
    }
}
