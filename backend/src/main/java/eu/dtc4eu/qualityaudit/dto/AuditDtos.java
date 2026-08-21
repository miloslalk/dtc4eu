package eu.dtc4eu.qualityaudit.dto;

import eu.dtc4eu.qualityaudit.entity.Audit;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public final class AuditDtos {

    private AuditDtos() {
    }

    public record PersonDto(Long id, String fullName) {
    }

    public record AccreditedItemDto(Long itemId, String itemCode, String label, BigDecimal excellencePoints) {
    }

    public record AccreditedAreaDto(Long areaId, String areaCode, String name) {
    }

    public record AuditDto(Long id, String auditCode, Audit.AuditType auditType, Integer renewalNumber,
                           Long facilityId, String facilityCode, String facilityName,
                           List<PersonDto> coordinators, List<PersonDto> evaluators,
                           List<String> accreditedProcesses,
                           List<AccreditedAreaDto> accreditedAreas,
                           List<AccreditedItemDto> accreditedItems) {
    }

    public record SaveAuditRequest(
            @NotBlank @Size(max = 20) String auditCode,
            @NotNull Long facilityId,
            @NotNull Audit.AuditType auditType,
            Integer renewalNumber,
            @NotNull List<Long> coordinatorIds,
            @NotNull List<Long> evaluatorIds) {
    }

    public record SaveOutcomesRequest(
            @NotNull List<@Size(max = 40) String> processCodes,
            @NotNull List<Long> areaIds,
            @NotNull @Valid List<OutcomeItem> items) {

        public record OutcomeItem(@NotNull Long itemId,
                                  @DecimalMin("0.0") @DecimalMax("4.0") BigDecimal excellencePoints) {
        }
    }
}
