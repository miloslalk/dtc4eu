package eu.dtc4eu.qualityaudit.entity;

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

import java.time.OffsetDateTime;

/**
 * Module 3 quality audit (V1 §5): one audited facility per audit, typed
 * Simple / BQ / CGQ, coded A1, A2… per cycle.
 */
@Entity
@Table(name = "audits")
@Getter
@Setter
@NoArgsConstructor
public class Audit {

    public enum AuditType {SIMPLE, BQ, CGQ}

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "audit_code", nullable = false, length = 20)
    private String auditCode;

    @Column(name = "annual_cycle_id", nullable = false)
    private Long annualCycleId;

    @Column(name = "facility_id", nullable = false)
    private Long facilityId;

    @Enumerated(EnumType.STRING)
    @Column(name = "audit_type", nullable = false, length = 10)
    private AuditType auditType;

    @Column(name = "renewal_number")
    private Integer renewalNumber;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private OffsetDateTime updatedAt;
}
