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

    /** One of the three administrations: who administered it and its C/NC mark per standard id. */
    public record AdministrationDto(QcncAssessment.AdministrationType administrationType,
                                    Long administeredById, String administeredByName,
                                    OffsetDateTime updatedAt,
                                    Map<Long, String> conformityByStandard) {
    }

    public record QcncBoardDto(List<StandardDto> standards,
                               List<AdministrationDto> administrations,
                               List<Long> strengthStandardIds,
                               List<Long> weaknessStandardIds,
                               List<MetaAreaDto> metaAreas) {
    }

    /** Spec §6.5 status of a meta-area across the three administrations. */
    public enum MetaAreaStatus {STRENGTH, WEAKNESS, NEITHER, INCOMPLETE}

    /**
     * One QCNC meta-area (RO.1 = Q1+Q2, RO.2 = Q3, RO.3 = Q4+Q5) with the
     * count of its required standards marked C in each administration and the
     * DTCRO areas it maps to.
     */
    public record MetaAreaDto(String code, List<String> clusterCodes, List<Integer> dtcroAreas,
                              int requiredStandards,
                              Map<QcncAssessment.AdministrationType, Integer> conformingByAdministration,
                              MetaAreaStatus status) {
    }

    public record SaveResultsRequest(@NotNull @Valid List<ResultEntry> results) {

        public record ResultEntry(@NotNull Long standardId,
                                  @NotNull QcncStandardResult.Conformity conformity,
                                  // QCNC has no scores (Simone, 24 Nov 2026): accepted only to be refused clearly
                                  Integer score,
                                  String note) {
        }
    }
}
