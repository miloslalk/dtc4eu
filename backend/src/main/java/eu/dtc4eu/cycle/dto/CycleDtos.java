package eu.dtc4eu.cycle.dto;

import eu.dtc4eu.cycle.entity.AnnualCycle;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDate;

public final class CycleDtos {

    private CycleDtos() {
    }

    /** {@code sequenceNumber} is derived: the cycle's position by start date (never typed in). */
    /** {@code isDefault}: the admin-chosen landing cycle for first visits (optional). */
    public record CycleDto(Long id, String code, LocalDate startDate, LocalDate endDate, String status,
                           Integer sequenceNumber, boolean isDefault) {

        public static CycleDto of(AnnualCycle cycle) {
            return new CycleDto(cycle.getId(), cycle.getCode(), cycle.getStartDate(), cycle.getEndDate(),
                    cycle.getStatus().name(), cycle.getSequenceNumber(), cycle.isDefaultCycle());
        }
    }

    public record CreateCycleRequest(
            @NotBlank @Pattern(regexp = "\\d{4}-\\d{4}", message = "must look like 2026-2027") String code,
            LocalDate startDate,
            LocalDate endDate) {
    }

    /** isDefault: true marks this cycle as the landing default (clearing any other); false clears it. */
    public record UpdateCycleRequest(LocalDate startDate, LocalDate endDate, AnnualCycle.Status status,
                                     Boolean isDefault) {
    }
}
