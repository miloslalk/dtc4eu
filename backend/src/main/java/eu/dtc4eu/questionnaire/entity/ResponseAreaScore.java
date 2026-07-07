package eu.dtc4eu.questionnaire.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** Derived area mean — computed by the scoring engine, never entered manually. */
@Entity
@Table(name = "response_area_scores")
@Getter
@Setter
@NoArgsConstructor
public class ResponseAreaScore {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "response_id", nullable = false)
    private Long responseId;

    @Column(name = "area_id", nullable = false)
    private Long areaId;

    @Column(name = "mean_value", precision = 6, scale = 3)
    private BigDecimal meanValue;

    @Column(name = "computed_at", nullable = false)
    private OffsetDateTime computedAt;
}
