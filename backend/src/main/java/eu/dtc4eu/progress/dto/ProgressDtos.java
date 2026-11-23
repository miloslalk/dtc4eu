package eu.dtc4eu.progress.dto;

import java.util.List;

/** Per-facility completion of the cycle's instruments (Simone's feedback #5: task view per role). */
public final class ProgressDtos {

    private ProgressDtos() {
    }

    public enum Task {
        GAS_SET, QCNC_INTERNAL_FACILITATOR, DTCRO_LONG_FORM,
        QCNC_TERNA_COORDINATOR_OWN, QCNC_TERNA_COORDINATOR_DELEGATION, DTCRO_DELEGATION,
        QUALITY_DOCUMENTS, PARTICIPATION_CHECKLIST
    }

    public enum TaskStatus { NOT_STARTED, IN_PROGRESS, DONE }

    /** {@code count} is the number of records behind the status (e.g. submitted delegation forms, OK checklist rows). */
    public record TaskDto(Task task, TaskStatus status, int count) {
    }

    public record FacilityProgressDto(Long facilityId, String facilityCode, String facilityName,
                                      Long ternaId, String ternaCode, String internalFacilitatorName,
                                      List<TaskDto> tasks) {
    }
}
