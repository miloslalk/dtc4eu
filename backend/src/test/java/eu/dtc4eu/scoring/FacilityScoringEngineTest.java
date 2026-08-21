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
 * the register's formulas; the two ternaExample1* tests below additionally
 * replay real columns from the client's Terna_Example1 workbook
 * (tools/dtc4euexpertfeedbackontheplatform (1)).
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

    /**
     * The workbook computes area category scores as the AVERAGE of the item
     * category means (sheet DTC-TC1: D13 = mean of N13:N17 present values),
     * NOT as a pooled mean over raw answers. With unequal per-item response
     * counts the two differ — this pins the workbook semantics.
     */
    @Test
    void areaCategoryMeanIsMeanOfItemMeansNotPooledRawMean() {
        Map<Long, List<Coordinate>> grid = new LinkedHashMap<>();
        grid.put(AREA_1, List.of(c(AREA_1, "a"), c(AREA_1, "b")));

        var input = responses();
        // respondent 1 answers only 1.a=4; respondent 2 answers 1.a=2 and 1.b=1
        input.put(RespondentCategory.USER, List.of(
                Map.of(c(AREA_1, "a"), 4),
                Map.of(c(AREA_1, "a"), 2, c(AREA_1, "b"), 1)));

        Result result = FacilityScoringEngine.compute(input, grid);
        // item means: 1.a=3, 1.b=1 → area mean-of-means = 2.000 (pooled raw would be 7/3 = 2.333)
        assertThat(result.areas().get(AREA_1).userMean()).isEqualByComparingTo("2.000");
    }

    /**
     * Real numbers from the Terna_Example1 workbook, sheet "DTC - TC 1": the
     * Community (column S) and Delegation (column T) administrations are
     * single questionnaires with integer answers, so they replay exactly.
     * Asserts every area Com (column I) and Del (column J).
     */
    @Test
    void ternaExample1CommunityAndDelegationColumnsReplayExactly() {
        long[] areaIds = new long[9];
        Map<Long, List<Coordinate>> grid = new LinkedHashMap<>();
        for (int areaNo = 1; areaNo <= 9; areaNo++) {
            long areaId = 200L + areaNo;
            areaIds[areaNo - 1] = areaId;
            grid.put(areaId, List.of(c(areaId, "a"), c(areaId, "b"), c(areaId, "c"),
                    c(areaId, "d"), c(areaId, "e")));
        }

        int[][] community = { // column S, areas 1..9 × letters a..e
                {3, 4, 2, 4, 3}, {3, 4, 3, 4, 3}, {3, 3, 4, 2, 4},
                {3, 3, 1, 4, 4}, {3, 3, 4, 4, 4}, {4, 4, 3, 2, 3},
                {2, 2, 4, 4, 4}, {4, 2, 2, 3, 3}, {4, 3, 2, 3, 4}};
        int[][] delegation = { // column T, areas 1..9 × letters a..c
                {3, 4, 3}, {2, 2, 2}, {4, 3, 4}, {3, 4, 1}, {4, 3, 4},
                {3, 4, 4}, {4, 4, 4}, {4, 2, 2}, {4, 2, 2}};

        Map<Coordinate, Integer> communityAnswers = new LinkedHashMap<>();
        Map<Coordinate, Integer> delegationAnswers = new LinkedHashMap<>();
        String letters = "abcde";
        for (int a = 0; a < 9; a++) {
            for (int l = 0; l < 5; l++) {
                communityAnswers.put(c(areaIds[a], String.valueOf(letters.charAt(l))), community[a][l]);
            }
            for (int l = 0; l < 3; l++) {
                delegationAnswers.put(c(areaIds[a], String.valueOf(letters.charAt(l))), delegation[a][l]);
            }
        }
        var input = responses();
        input.put(RespondentCategory.LONG_FORM, List.of(communityAnswers));
        input.put(RespondentCategory.DELEGATION, List.of(delegationAnswers));

        Result result = FacilityScoringEngine.compute(input, grid);

        String[] expectedCom = {"3.200", "3.400", "3.200", "3.000", "3.600", "3.200", "3.200", "2.800", "3.200"};
        String[] expectedDel = {"3.333", "2.000", "3.667", "2.667", "3.667", "3.667", "4.000", "2.667", "2.667"};
        for (int a = 0; a < 9; a++) {
            Scores area = result.areas().get(areaIds[a]);
            assertThat(area.com()).as("area %d Com (workbook column I)", a + 1)
                    .isEqualByComparingTo(expectedCom[a]);
            assertThat(area.del()).as("area %d Del (workbook column J)", a + 1)
                    .isEqualByComparingTo(expectedDel[a]);
            assertThat(area.averI()).as("area %d has no individual administrations", a + 1).isNull();
        }
    }
}
