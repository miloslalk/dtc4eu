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

/** Facility overall score for one questionnaire and cycle: mean of the nine area finals. */
@Entity
@Table(name = "facility_questionnaire_scores")
@Getter
@Setter
@NoArgsConstructor
public class FacilityQuestionnaireScore {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "facility_id", nullable = false)
    private Long facilityId;

    @Column(name = "annual_cycle_id", nullable = false)
    private Long annualCycleId;

    @Column(name = "questionnaire_id", nullable = false)
    private Long questionnaireId;

    @Column(name = "overall_score")
    private BigDecimal overallScore;

    @Enumerated(EnumType.STRING)
    @Column(length = 12)
    private Classification classification;

    @Column(name = "computed_at")
    private OffsetDateTime computedAt;
}
