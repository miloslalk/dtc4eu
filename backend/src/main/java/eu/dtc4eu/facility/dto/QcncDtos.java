package eu.dtc4eu.facility.dto;

import eu.dtc4eu.facility.entity.QcncAssessment;
import eu.dtc4eu.facility.entity.QcncStandard;
import eu.dtc4eu.facility.entity.QcncStandardResult;
import eu.dtc4eu.scoring.entity.Classification;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public final class QcncDtos {

    private QcncDtos() {
    }

    public record StandardDto(Long id, String clusterCode, String letterCode,
                              QcncStandard.StandardType standardType, boolean mandatory,
                              Map<String, Object> settingParameters, String description, Integer sortOrder) {

        public static StandardDto of(QcncStandard standard) {
            return new StandardDto(standard.getId(), standard.getClusterCode(), standard.getLetterCode(),
                    standard.getStandardType(), standard.isMandatory(), standard.getSettingParameters(),
                    standard.getDescription(), standard.getSortOrder());
        }
    }

    /** One of the three administrations: who administered it and its C/NC marks and 1–4 scores per standard id. */
    public record AdministrationDto(QcncAssessment.AdministrationType administrationType,
                                    Long administeredById, String administeredByName,
                                    OffsetDateTime updatedAt,
                                    Map<Long, String> conformityByStandard,
                                    Map<Long, Integer> scoreByStandard) {
    }

    /** Mean of the administrations' scores for one standard, classified per TAB.5 (expert feedback). */
    public record StandardScoreDto(Long standardId, BigDecimal meanScore, Classification classification) {
    }

    public record QcncBoardDto(List<StandardDto> standards,
                               List<AdministrationDto> administrations,
                               List<StandardScoreDto> standardScores,
                               List<Long> strengthStandardIds,
                               List<Long> weaknessStandardIds) {
    }

    public record SaveResultsRequest(@NotNull @Valid List<ResultEntry> results) {

        public record ResultEntry(@NotNull Long standardId,
                                  @NotNull QcncStandardResult.Conformity conformity,
                                  @Min(1) @Max(4) Integer score,
                                  String note) {
        }
    }
}
