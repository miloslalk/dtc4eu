package eu.dtc4eu.facility.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Per-cycle participation of a facility (Excel: 'Activity'); unique per (facility, cycle). */
@Entity
@Table(name = "facility_cycle_participation")
@Getter
@Setter
@NoArgsConstructor
public class FacilityCycleParticipation {

    public enum Activity { VISITING_TERNA, QUALITY_AUDIT, TRAINING_PAUSE }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "facility_id", nullable = false)
    private Long facilityId;

    @Column(name = "annual_cycle_id", nullable = false)
    private Long annualCycleId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Activity activity;

    @Column(name = "internal_facilitator_id")
    private Long internalFacilitatorId;

    /* Expert-feedback activity checklist: TRUE = OK, FALSE = NO, NULL = unset. */

    @Column(name = "sag_done")
    private Boolean sagDone;

    @Column(name = "qcnc_done")
    private Boolean qcncDone;

    @Column(name = "dtcro_long_form_done")
    private Boolean dtcroLongFormDone;

    @Column(name = "foundation_meeting_done")
    private Boolean foundationMeetingDone;

    @Column(name = "final_forum_done")
    private Boolean finalForumDone;
}
