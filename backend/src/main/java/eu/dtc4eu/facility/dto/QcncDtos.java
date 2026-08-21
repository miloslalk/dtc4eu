package eu.dtc4eu.facility.dto;

import eu.dtc4eu.facility.entity.QcncAssessment;
import eu.dtc4eu.facility.entity.QcncStandard;
import eu.dtc4eu.facility.entity.QcncStandardResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

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

    /** One of the three administrations: who administered it and its C/NC marks per standard id. */
    public record AdministrationDto(QcncAssessment.AdministrationType administrationType,
                                    Long administeredById, String administeredByName,
                                    OffsetDateTime updatedAt,
                                    Map<Long, String> conformityByStandard) {
    }

    public record QcncBoardDto(List<StandardDto> standards,
                               List<AdministrationDto> administrations,
                               List<Long> strengthStandardIds,
                               List<Long> weaknessStandardIds) {
    }

    public record SaveResultsRequest(@NotNull @Valid List<ResultEntry> results) {

        public record ResultEntry(@NotNull Long standardId,
                                  @NotNull QcncStandardResult.Conformity conformity,
                                  String note) {
        }
    }
}
