package eu.dtc4eu.terna.dto;

import eu.dtc4eu.terna.entity.Terna;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public final class TernaDtos {

    private TernaDtos() {
    }

    public record TernaFacilityDto(Long facilityId, String facilityCode, String facilityName,
                                   Long internalFacilitatorId, String internalFacilitatorName, Integer position) {
    }

    public record CoordinatorDto(Long userId, String fullName) {
    }

    public record VisitDateDto(Integer visitOrder, LocalDate visitDate) {
    }

    public record TernaDto(Long id, String ternaCode, Terna.TernaType ternaType,
                           LocalDate foundingMeetingDate, LocalDate finalForumDate,
                           List<TernaFacilityDto> facilities, List<CoordinatorDto> coordinators,
                           List<VisitDateDto> visitDates) {
    }

    /** Row shape for list screens (aggregates flattened). */
    public record TernaRowDto(Long id, String ternaCode, Terna.TernaType ternaType,
                              LocalDate foundingMeetingDate, LocalDate finalForumDate,
                              List<String> facilityCodes, List<String> coordinatorNames) {
    }

    public record SaveTernaRequest(
            @NotBlank @Size(max = 20) String ternaCode,
            @NotNull Terna.TernaType ternaType,
            @NotNull @Size(min = 3, max = 3, message = "a terna links exactly 3 facilities") List<Long> facilityIds,
            @NotNull List<Long> coordinatorIds,
            LocalDate foundingMeetingDate,
            LocalDate finalForumDate,
            @Size(max = 3) List<VisitDateDto> visitDates) {
    }
}
