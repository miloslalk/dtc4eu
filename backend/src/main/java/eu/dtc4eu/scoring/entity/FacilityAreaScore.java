package eu.dtc4eu.scoring.entity;

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

/** Derived Quant-SAWA row: one area per facility, cycle and questionnaire. */
@Entity
@Table(name = "facility_area_scores")
@Getter
@Setter
@NoArgsConstructor
public class FacilityAreaScore {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "facility_id", nullable = false)
    private Long facilityId;

    @Column(name = "annual_cycle_id", nullable = false)
    private Long annualCycleId;

    @Column(name = "questionnaire_id", nullable = false)
    private Long questionnaireId;

    @Column(name = "area_id", nullable = false)
    private Long areaId;

    @Column(name = "user_mean")
    private BigDecimal userMean;

    @Column(name = "family_mean")
    private BigDecimal familyMean;

    @Column(name = "staff_mean")
    private BigDecimal staffMean;

    @Column(name = "manager_mean")
    private BigDecimal managerMean;

    @Column(name = "aver_i")
    private BigDecimal averI;

    @Column(name = "com_value")
    private BigDecimal comValue;

    @Column(name = "del_value")
    private BigDecimal delValue;

    @Column(name = "final_score")
    private BigDecimal finalScore;

    @Enumerated(EnumType.STRING)
    @Column(length = 12)
    private Classification classification;

    @Column(name = "computed_at")
    private OffsetDateTime computedAt;
}
