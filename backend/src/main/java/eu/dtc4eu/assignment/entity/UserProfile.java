package eu.dtc4eu.assignment.entity;

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
 * Per-cycle respondent profile (spec CORE-04, V1 user_profiles): confirmed at
 * each login and evaluated by the assignment rules. Fields map onto registry
 * entities — the category onto facility_members, the phase onto the facility's
 * participation activity — and never carry personal names.
 */
@Entity
@Table(name = "user_profiles")
@Getter
@Setter
@NoArgsConstructor
public class UserProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "annual_cycle_id")
    private Long annualCycleId;

    private Integer age;

    @Column(name = "role_category", length = 30)
    private String roleCategory;

    @Column(name = "terna_id")
    private Long ternaId;

    @Column(name = "project_phase", length = 30)
    private String projectPhase;

    @Column(length = 80)
    private String country;

    @Column(length = 120)
    private String city;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private OffsetDateTime updatedAt;
}
