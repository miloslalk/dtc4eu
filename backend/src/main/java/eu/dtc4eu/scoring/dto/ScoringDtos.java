package eu.dtc4eu.scoring.dto;

import eu.dtc4eu.scoring.entity.Classification;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public final class ScoringDtos {

    private ScoringDtos() {
    }

    /** One Quant-SPWP grid row; coordinate is "areaNo.letter", e.g. "1.a". */
    public record ItemScoreRowDto(Long areaId, String areaCode, Integer areaNo, String itemLetter, String coordinate,
                                  BigDecimal userMean, BigDecimal familyMean, BigDecimal staffMean,
                                  BigDecimal managerMean, BigDecimal averI, BigDecimal comValue, BigDecimal delValue,
                                  BigDecimal finalScore, Classification classification) {
    }

    /** One Quant-SAWA grid row. */
    public record AreaScoreRowDto(Long areaId, String areaCode, String areaName, Integer areaNo,
                                  BigDecimal userMean, BigDecimal familyMean, BigDecimal staffMean,
                                  BigDecimal managerMean, BigDecimal averI, BigDecimal comValue, BigDecimal delValue,
                                  BigDecimal finalScore, Classification classification) {
    }

    public record FacilityScoresDto(Long facilityId, String facilityCode, Long questionnaireId,
                                    String questionnaireCode, OffsetDateTime computedAt,
                                    BigDecimal overallScore, Classification overallClassification,
                                    List<AreaScoreRowDto> areas, List<ItemScoreRowDto> items,
                                    List<String> strengthPoints, List<String> weaknessPoints,
                                    List<String> strengthAreas, List<String> weaknessAreas) {
    }

    /**
     * Summary Table of Qualitative and Quantitative Community Data (register
     * §3.5). Values are coordinate/area-number lists ("8.c; 9.b" style,
     * serialised as arrays). Qualitative rows are null until QCNC data exists.
     */
    public record SummaryTableDto(List<String> qualWeaknessAreas, List<String> qualWeaknessPoints,
                                  List<String> quantWeaknessAreas, List<String> quantWeaknessPoints,
                                  List<String> qualStrengthAreas, List<String> qualStrengthPoints,
                                  List<String> quantStrengthAreas, List<String> quantStrengthPoints,
                                  List<String> benchmarkAreas, List<String> excellencePoints,
                                  List<String> epBench) {
    }

    public record TernaBenchmarkRowDto(Integer areaNo, String areaCode, String areaName,
                                       Long bestFacilityId, String bestFacilityCode,
                                       BigDecimal bestScore, Classification classification) {
    }

    public record PopulationCellDto(long collected, long roster) {
    }

    /** TAB.2 row: collected submitted questionnaires vs the pseudonymous member roster, per category. */
    public record PopulationRowDto(Long facilityId, String facilityCode,
                                   Map<String, PopulationCellDto> byCategory,
                                   long collectedTotal, long rosterTotal) {
    }

    public record TernaPopulationDto(Long ternaId, String ternaCode, List<PopulationRowDto> facilities,
                                     PopulationRowDto totals) {
    }
}
