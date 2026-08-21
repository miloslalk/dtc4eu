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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * QCNC assessment across five quality areas (Excel: Q1..Q5); one per
 * (facility, cycle). Area meanings are an open input (spec §9), so answers
 * live in a free-form JSON payload keyed Q1..Q5.
 */
@Entity
@Table(name = "qcnc_assessments")
@Getter
@Setter
@NoArgsConstructor
public class QcncAssessment {

    public enum AdministrationType {INTERNAL_FACILITATOR, TERNA_COORDINATOR_OWN, TERNA_COORDINATOR_DELEGATION}

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "facility_id", nullable = false)
    private Long facilityId;

    @Column(name = "annual_cycle_id", nullable = false)
    private Long annualCycleId;

    @Enumerated(EnumType.STRING)
    @Column(name = "administration_type", nullable = false, length = 40)
    private AdministrationType administrationType = AdministrationType.INTERNAL_FACILITATOR;

    @Column(name = "administered_by")
    private Long administeredBy;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> payload;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private OffsetDateTime updatedAt;
}
