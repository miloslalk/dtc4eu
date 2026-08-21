package eu.dtc4eu.scoring;

import eu.dtc4eu.questionnaire.entity.QuestionnaireResponse.RespondentCategory;
import eu.dtc4eu.scoring.entity.Classification;
import eu.dtc4eu.scoring.service.FacilityScoringEngine;
import eu.dtc4eu.scoring.service.FacilityScoringEngine.Coordinate;
import eu.dtc4eu.scoring.service.FacilityScoringEngine.Result;
import eu.dtc4eu.scoring.service.FacilityScoringEngine.Scores;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression fixture for the v0.2 register §3.1–§3.3 pipelines (the
 * Terna_Example1 workbook semantics): Excel AVERAGE-skip-missing at every
 * stage, Del absent for letters d/e, exact TAB.5 boundaries (2.0 → CRITICAL,
 * 3.0 → EFFICACY), 3-decimal HALF_UP outputs. Numbers are hand-computed from
 * the register's formulas; swap in the client's workbook values when the
 * Terna_Example1 file is provided.
 */
class FacilityScoringEngineTest {

    private static final long AREA_1 = 101L;
    private static final long AREA_2 = 102L;

    private static Coordinate c(long areaId, String letter) {
        return new Coordinate(areaId, letter);
    }

    private static Map<RespondentCategory, List<Map<Coordinate, Integer>>> responses() {
        return new EnumMap<>(RespondentCategory.class);
    }

    /** Full worked example on one coordinate: every source present, chained AVERAGEs. */
    @Test
    void itemPipelineMatchesTheRegisterFormulas() {
        Map<Long, List<Coordinate>> grid = new LinkedHashMap<>();
        grid.put(AREA_1, List.of(c(AREA_1, "a")));

        var input = responses();
        input.put(RespondentCategory.USER, List.of(Map.of(c(AREA_1, "a"), 4), Map.of(c(AREA_1, "a"), 3)));
        input.put(RespondentCategory.FAMILY, List.of(Map.of(c(AREA_1, "a"), 2)));
        input.put(RespondentCategory.MANAGER, List.of(Map.of(c(AREA_1, "a"), 1)));
        input.put(RespondentCategory.LONG_FORM, List.of(Map.of(c(AREA_1, "a"), 3)));
        input.put(RespondentCategory.DELEGATION, List.of(Map.of(c(AREA_1, "a"), 2)));

        Result result = FacilityScoringEngine.compute(input, grid);
        Scores scores = result.items().get(c(AREA_1, "a"));

        assertThat(scores.userMean()).isEqualByComparingTo("3.500");   // (4+3)/2
        assertThat(scores.familyMean()).isEqualByComparingTo("2.000");
        assertThat(scores.staffMean()).isNull();                        // no STAFF administrations → skipped
        assertThat(scores.managerMean()).isEqualByComparingTo("1.000");
        // Aver-I = AVERAGE(3.5, 2, 1) — STAFF skipped, never zero-filled
        assertThat(scores.averI()).isEqualByComparingTo("2.167");
        assertThat(scores.com()).isEqualByComparingTo("3.000");
        assertThat(scores.del()).isEqualByComparingTo("2.000");
        // Final = AVERAGE(2.166667, 3, 2) = 2.388889 → 2.389
        assertThat(scores.finalScore()).isEqualByComparingTo("2.389");
        assertThat(scores.classification()).isEqualTo(Classification.INTERMEDIATE);
    }

    /** Del is inherently absent for letters d/e; Final averages only what exists. */
    @Test
    void delIsSkippedForLettersDAndE() {
        Map<Long, List<Coordinate>> grid = new LinkedHashMap<>();
        grid.put(AREA_1, List.of(c(AREA_1, "d")));

        var input = responses();
        input.put(RespondentCategory.USER, List.of(Map.of(c(AREA_1, "d"), 3)));
        input.put(RespondentCategory.LONG_FORM, List.of(Map.of(c(AREA_1, "d"), 4)));
        // DELEGATION covers a–c only — this coordinate is simply not on the form
        input.put(RespondentCategory.DELEGATION, List.of(Map.of(c(AREA_1, "a"), 1)));

        Scores scores = FacilityScoringEngine.compute(input, grid).items().get(c(AREA_1, "d"));

        assertThat(scores.del()).isNull();
        // Final = AVERAGE(3, 4) = 3.5
        assertThat(scores.finalScore()).isEqualByComparingTo("3.500");
        assertThat(scores.classification()).isEqualTo(Classification.EFFICACY);
    }

    /** TAB.5 boundary semantics are exact: 2.0 → CRITICAL, 3.0 → EFFICACY. */
    @Test
    void classificationBoundariesAreExact() {
        assertThat(Classification.of(new BigDecimal("1.000"))).isEqualTo(Classification.CRITICAL);
        assertThat(Classification.of(new BigDecimal("2.000"))).isEqualTo(Classification.CRITICAL);
        assertThat(Classification.of(new BigDecimal("2.001"))).isEqualTo(Classification.INTERMEDIATE);
        assertThat(Classification.of(new BigDecimal("2.999"))).isEqualTo(Classification.INTERMEDIATE);
        assertThat(Classification.of(new BigDecimal("3.000"))).isEqualTo(Classification.EFFICACY);
        assertThat(Classification.of(new BigDecimal("4.000"))).isEqualTo(Classification.EFFICACY);
        assertThat(Classification.of(null)).isNull();
    }

    /** Outputs are rounded HALF_UP to 3 decimals, matching the per-response scoring convention. */
    @Test
    void outputsRoundHalfUpToThreeDecimals() {
        Map<Long, List<Coordinate>> grid = new LinkedHashMap<>();
        grid.put(AREA_1, List.of(c(AREA_1, "a")));

        var input = responses();
        input.put(RespondentCategory.USER, List.of(
                Map.of(c(AREA_1, "a"), 1), Map.of(c(AREA_1, "a"), 1), Map.of(c(AREA_1, "a"), 2)));

        Scores scores = FacilityScoringEngine.compute(input, grid).items().get(c(AREA_1, "a"));
        assertThat(scores.userMean()).isEqualByComparingTo("1.333");    // 4/3
        assertThat(scores.finalScore()).isEqualByComparingTo("1.333");
        assertThat(scores.classification()).isEqualTo(Classification.CRITICAL);
    }

    /**
     * Area level repeats the identical structure over the area's items within
     * each source (register §3.2), and the facility overall is the mean of the
     * area finals.
     */
    @Test
    void areaPipelineAndOverallFollowTheSameStructure() {
        Map<Long, List<Coordinate>> grid = new LinkedHashMap<>();
        grid.put(AREA_1, List.of(c(AREA_1, "a"), c(AREA_1, "b")));
        grid.put(AREA_2, List.of(c(AREA_2, "a")));

        var input = responses();
        input.put(RespondentCategory.USER, List.of(Map.of(
                c(AREA_1, "a"), 4, c(AREA_1, "b"), 2, c(AREA_2, "a"), 1)));
        input.put(RespondentCategory.LONG_FORM, List.of(Map.of(
                c(AREA_1, "a"), 4, c(AREA_1, "b"), 4, c(AREA_2, "a"), 3)));

        Result result = FacilityScoringEngine.compute(input, grid);

        Scores area1 = result.areas().get(AREA_1);
        // SAWA-U = mean(4, 2) = 3; SAWA-Com = mean(4, 4) = 4; Aver-I = 3 (only U present)
        assertThat(area1.userMean()).isEqualByComparingTo("3.000");
        assertThat(area1.com()).isEqualByComparingTo("4.000");
        assertThat(area1.averI()).isEqualByComparingTo("3.000");
        // Final = AVERAGE(3, 4) = 3.5
        assertThat(area1.finalScore()).isEqualByComparingTo("3.500");
        assertThat(area1.classification()).isEqualTo(Classification.EFFICACY);

        Scores area2 = result.areas().get(AREA_2);
        // Final = AVERAGE(1, 3) = 2 — exactly on the CRITICAL boundary
        assertThat(area2.finalScore()).isEqualByComparingTo("2.000");
        assertThat(area2.classification()).isEqualTo(Classification.CRITICAL);

        // overall = mean of area finals = (3.5 + 2) / 2 = 2.75
        assertThat(result.overall()).isEqualByComparingTo("2.750");
        assertThat(result.overallClassification()).isEqualTo(Classification.INTERMEDIATE);
    }

    /** A coordinate with no data anywhere yields a fully-null row, never zeros. */
    @Test
    void absentCoordinateStaysNull() {
        Map<Long, List<Coordinate>> grid = new LinkedHashMap<>();
        grid.put(AREA_1, List.of(c(AREA_1, "a"), c(AREA_1, "e")));

        var input = responses();
        input.put(RespondentCategory.USER, List.of(Map.of(c(AREA_1, "a"), 3)));

        Scores empty = FacilityScoringEngine.compute(input, grid).items().get(c(AREA_1, "e"));
        assertThat(empty.userMean()).isNull();
        assertThat(empty.averI()).isNull();
        assertThat(empty.finalScore()).isNull();
        assertThat(empty.classification()).isNull();
    }

    /** No responses at all: grids full of nulls and a null overall. */
    @Test
    void emptyInputProducesNullOverall() {
        Map<Long, List<Coordinate>> grid = new LinkedHashMap<>();
        grid.put(AREA_1, List.of(c(AREA_1, "a")));

        Result result = FacilityScoringEngine.compute(responses(), grid);
        assertThat(result.overall()).isNull();
        assertThat(result.overallClassification()).isNull();
        assertThat(result.items().get(c(AREA_1, "a")).finalScore()).isNull();
    }
}
