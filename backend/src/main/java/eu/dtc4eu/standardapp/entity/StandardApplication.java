package eu.dtc4eu.standardapp.entity;

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

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Application of a quality document by a facility in one annual cycle
 * (Modules 6–8 table, activated by the v0.2 register §6). Document types are
 * GPP / CGS / EEN (register §1 naming reversal); document codes follow
 * "{TYPE} CT{n}-{suffix}" (e.g. "GPP CT4-LC") — the suffix taxonomy is open
 * question 12.1, so the code stays free-form. The application level (0–100 %)
 * is assessed EVERY cycle by the Terna or Audit Coordinator.
 */
@Entity
@Table(name = "standard_applications")
@Getter
@Setter
@NoArgsConstructor
public class StandardApplication {

    public enum StandardType { GPP, CGS, EEN }

    public enum Origin { DEVELOPED, IMPLEMENTED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "facility_id", nullable = false)
    private Long facilityId;

    @Column(name = "annual_cycle_id", nullable = false)
    private Long annualCycleId;

    @Enumerated(EnumType.STRING)
    @Column(name = "standard_type", nullable = false, length = 10)
    private StandardType standardType;

    @Column(name = "standard_code", length = 40)
    private String standardCode;

    @Column(name = "service_name", length = 255)
    private String serviceName;

    @Column(name = "service_type", length = 120)
    private String serviceType;

    @Column(name = "num_users")
    private Integer numUsers;

    @Column(name = "num_staff")
    private Integer numStaff;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Origin origin;

    @Column(name = "application_cycles_count")
    private Integer applicationCyclesCount;

    @Column(name = "application_level_pct")
    private BigDecimal applicationLevelPct;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private OffsetDateTime updatedAt;
}
