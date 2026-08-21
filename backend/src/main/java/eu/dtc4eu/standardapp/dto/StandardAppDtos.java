package eu.dtc4eu.standardapp.dto;

import eu.dtc4eu.standardapp.entity.StandardApplication;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public final class StandardAppDtos {

    private StandardAppDtos() {
    }

    public record DocumentApplicationDto(Long id, StandardApplication.StandardType standardType, String standardCode,
                                         String serviceName, String serviceType, Integer numUsers, Integer numStaff,
                                         StandardApplication.Origin origin, Integer applicationCyclesCount,
                                         BigDecimal applicationLevelPct) {

        public static DocumentApplicationDto of(StandardApplication a) {
            return new DocumentApplicationDto(a.getId(), a.getStandardType(), a.getStandardCode(), a.getServiceName(),
                    a.getServiceType(), a.getNumUsers(), a.getNumStaff(), a.getOrigin(),
                    a.getApplicationCyclesCount(), a.getApplicationLevelPct());
        }
    }

    public record SaveDocumentApplicationRequest(
            @NotNull StandardApplication.StandardType standardType,
            @Size(max = 40) String standardCode,
            @Size(max = 255) String serviceName,
            @Size(max = 120) String serviceType,
            Integer numUsers,
            Integer numStaff,
            StandardApplication.Origin origin,
            Integer applicationCyclesCount,
            @DecimalMin("0.0") @DecimalMax("100.0") BigDecimal applicationLevelPct) {
    }

    /** One cycle of the facility's accreditation history (v0.2 register §6). */
    public record HistoryCycleDto(Long cycleId, String cycleCode, Integer sequenceNumber, String activity,
                                  Map<String, List<String>> excellencePointsByQuestionnaire,
                                  List<DocumentApplicationDto> documentApplications) {
    }

    public record FacilityHistoryDto(Long facilityId, String facilityCode, long cyclesInProgramme,
                                     List<HistoryCycleDto> cycles) {
    }
}
