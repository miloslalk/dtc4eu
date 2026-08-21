package eu.dtc4eu.cycle.dto;

import eu.dtc4eu.cycle.entity.AnnualCycle;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDate;

public final class CycleDtos {

    private CycleDtos() {
    }

    public record CycleDto(Long id, String code, LocalDate startDate, LocalDate endDate, String status,
                           Integer sequenceNumber) {

        public static CycleDto of(AnnualCycle cycle) {
            return new CycleDto(cycle.getId(), cycle.getCode(), cycle.getStartDate(), cycle.getEndDate(),
                    cycle.getStatus().name(), cycle.getSequenceNumber());
        }
    }

    public record CreateCycleRequest(
            @NotBlank @Pattern(regexp = "\\d{4}-\\d{4}", message = "must look like 2026-2027") String code,
            LocalDate startDate,
            LocalDate endDate,
            Integer sequenceNumber) {
    }

    public record UpdateCycleRequest(LocalDate startDate, LocalDate endDate, AnnualCycle.Status status,
                                     Integer sequenceNumber) {
    }
}
