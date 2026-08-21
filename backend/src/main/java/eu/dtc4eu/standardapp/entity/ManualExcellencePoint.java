package eu.dtc4eu.standardapp.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * Manually entered Excellence Points for questionnaires the engine does not
 * compute (register §12.4): historical cycles and ViVaCom, whose labels
 * (e.g. "4.16") do not fit the DTCRO area×letter grid. Stored verbatim.
 */
@Entity
@Table(name = "manual_excellence_points")
@Getter
@Setter
@NoArgsConstructor
public class ManualExcellencePoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "facility_id", nullable = false)
    private Long facilityId;

    @Column(name = "annual_cycle_id", nullable = false)
    private Long annualCycleId;

    @Column(name = "questionnaire_code", nullable = false, length = 30)
    private String questionnaireCode;

    @Column(nullable = false, length = 20)
    private String label;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;
}
