package eu.dtc4eu.scoring.service;

import eu.dtc4eu.questionnaire.entity.QuestionnaireResponse.RespondentCategory;
import eu.dtc4eu.scoring.entity.Classification;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Pure implementation of the v0.2 register §3.1–§3.2 pipelines (the
 * Terna_Example1 workbook). Every stage uses Excel AVERAGE semantics: absent
 * components are skipped, never zero-filled; a value exists only if at least
 * one input exists. Intermediates carry scale 6; outputs are rounded to
 * 3 decimals HALF_UP (matching the per-response ScoringService convention)
 * and classified per TAB.5 with exact boundaries on the unrounded value.
 */
public final class FacilityScoringEngine {

    private static final int INTERMEDIATE_SCALE = 6;
    private static final int OUTPUT_SCALE = 3;

    private FacilityScoringEngine() {
    }

    /** A coordinate of the master grid: area × letter a–e. */
    public record Coordinate(Long areaId, String letter) {
    }

    /** One row of the workbook: the source columns (U-I, F-I, S-I, M-I, Com, Del) plus the derived values. */
    public record Scores(BigDecimal userMean, BigDecimal familyMean, BigDecimal staffMean, BigDecimal managerMean,
                         BigDecimal averI, BigDecimal com, BigDecimal del, BigDecimal finalScore,
                         Classification classification, BigDecimal unroundedFinal) {

        Scores classifiedBy(Classification.Bands bands) {
            return new Scores(userMean, familyMean, staffMean, managerMean, averI, com, del, finalScore,
                    Classification.of(unroundedFinal, bands), unroundedFinal);
        }
    }

    /** {@code unroundedOverall} keeps the full-precision mean so bands can be re-applied (spec §6.8). */
    public record Result(Map<Coordinate, Scores> items, Map<Long, Scores> areas,
                         BigDecimal overall, Classification overallClassification, BigDecimal unroundedOverall) {
    }

    /** Unrounded row used internally so area aggregation matches the workbook's full-precision chain. */
    private record Raw(BigDecimal u, BigDecimal f, BigDecimal s, BigDecimal m, BigDecimal averI,
                       BigDecimal com, BigDecimal del, BigDecimal fin) {
    }

    /**
     * @param responses       per category, one map (coordinate → Likert value) per submitted administration
     * @param areaCoordinates the master grid: per area (in display order), its coordinates (letters in order)
     */
    public static Result compute(Map<RespondentCategory, List<Map<Coordinate, Integer>>> responses,
                                 Map<Long, List<Coordinate>> areaCoordinates) {
        Classification.Bands bands = Classification.Bands.DTCRO;
        Map<Coordinate, Raw> rawItems = new LinkedHashMap<>();
        Map<Coordinate, Scores> items = new LinkedHashMap<>();
        for (List<Coordinate> coordinates : areaCoordinates.values()) {
            for (Coordinate coordinate : coordinates) {
                Raw raw = itemRow(coordinate, responses);
                rawItems.put(coordinate, raw);
                items.put(coordinate, round(raw, bands));
            }
        }

        Map<Long, Scores> areas = new LinkedHashMap<>();
        List<BigDecimal> areaFinals = new ArrayList<>();
        for (Map.Entry<Long, List<Coordinate>> entry : areaCoordinates.entrySet()) {
            List<Raw> rows = entry.getValue().stream().map(rawItems::get).toList();
            BigDecimal u = meanOfPresent(rows.stream().map(Raw::u).toList());
            BigDecimal f = meanOfPresent(rows.stream().map(Raw::f).toList());
            BigDecimal s = meanOfPresent(rows.stream().map(Raw::s).toList());
            BigDecimal m = meanOfPresent(rows.stream().map(Raw::m).toList());
            BigDecimal averI = meanOfPresent(Arrays.asList(u, f, s, m));
            BigDecimal com = meanOfPresent(rows.stream().map(Raw::com).toList());
            BigDecimal del = meanOfPresent(rows.stream().map(Raw::del).toList());
            BigDecimal fin = meanOfPresent(Arrays.asList(averI, com, del));
            areas.put(entry.getKey(), round(new Raw(u, f, s, m, averI, com, del, fin), bands));
            areaFinals.add(fin);
        }

        BigDecimal overallRaw = meanOfPresent(areaFinals);
        return new Result(items, areas, scale(overallRaw, OUTPUT_SCALE), Classification.of(overallRaw, bands), overallRaw);
    }

    private static Raw itemRow(Coordinate coordinate,
                               Map<RespondentCategory, List<Map<Coordinate, Integer>>> responses) {
        BigDecimal u = categoryMean(coordinate, responses.get(RespondentCategory.USER));
        BigDecimal f = categoryMean(coordinate, responses.get(RespondentCategory.FAMILY));
        BigDecimal s = categoryMean(coordinate, responses.get(RespondentCategory.STAFF));
        BigDecimal m = categoryMean(coordinate, responses.get(RespondentCategory.MANAGER));
        BigDecimal averI = meanOfPresent(Arrays.asList(u, f, s, m));
        BigDecimal com = categoryMean(coordinate, responses.get(RespondentCategory.LONG_FORM));
        BigDecimal del = categoryMean(coordinate, responses.get(RespondentCategory.DELEGATION));
        BigDecimal fin = meanOfPresent(Arrays.asList(averI, com, del));
        return new Raw(u, f, s, m, averI, com, del, fin);
    }

    /** Simple unweighted mean over the administrations that answered this coordinate; null if none did. */
    private static BigDecimal categoryMean(Coordinate coordinate, List<Map<Coordinate, Integer>> administrations) {
        if (administrations == null || administrations.isEmpty()) {
            return null;
        }
        long count = 0;
        long sum = 0;
        for (Map<Coordinate, Integer> administration : administrations) {
            Integer value = administration.get(coordinate);
            if (value != null) {
                sum += value;
                count++;
            }
        }
        if (count == 0) {
            return null;
        }
        return BigDecimal.valueOf(sum).divide(BigDecimal.valueOf(count), INTERMEDIATE_SCALE, RoundingMode.HALF_UP);
    }

    /** Excel AVERAGE: mean over the non-null values; null when all are absent. */
    static BigDecimal meanOfPresent(List<BigDecimal> values) {
        List<BigDecimal> present = values.stream().filter(Objects::nonNull).toList();
        if (present.isEmpty()) {
            return null;
        }
        BigDecimal sum = present.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(present.size()), INTERMEDIATE_SCALE, RoundingMode.HALF_UP);
    }

    private static Scores round(Raw raw, Classification.Bands bands) {
        BigDecimal fin = scale(raw.fin(), OUTPUT_SCALE);
        return new Scores(scale(raw.u(), OUTPUT_SCALE), scale(raw.f(), OUTPUT_SCALE), scale(raw.s(), OUTPUT_SCALE),
                scale(raw.m(), OUTPUT_SCALE), scale(raw.averI(), OUTPUT_SCALE), scale(raw.com(), OUTPUT_SCALE),
                // bands apply to the unrounded value (spec §6.8): 2.9996 stays INTERMEDIATE
                scale(raw.del(), OUTPUT_SCALE), fin, Classification.of(raw.fin(), bands), raw.fin());
    }

    /**
     * Re-applies a questionnaire's own TAB.5 bands (V15) to a computed result; null bands leave it
     * unclassified (e.g. ViVaCom until the client supplies its table). Kept separate from
     * {@link #compute} so the band type does not widen the frozen scoring↔questionnaire signature.
     */
    public static Result classify(Result result, Classification.Bands bands) {
        Map<Coordinate, Scores> items = new LinkedHashMap<>();
        result.items().forEach((coordinate, scores) -> items.put(coordinate, scores.classifiedBy(bands)));
        Map<Long, Scores> areas = new LinkedHashMap<>();
        result.areas().forEach((areaId, scores) -> areas.put(areaId, scores.classifiedBy(bands)));
        return new Result(items, areas, result.overall(), Classification.of(result.unroundedOverall(), bands),
                result.unroundedOverall());
    }

    private static BigDecimal scale(BigDecimal value, int scale) {
        return value == null ? null : value.setScale(scale, RoundingMode.HALF_UP);
    }
}
