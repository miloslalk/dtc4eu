package eu.dtc4eu.scoring.entity;

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

/**
 * Benchmark Strength Area of a terna (register §3.4): per area, the best final
 * area score among the terna's three facilities. Ties break by terna position
 * (documented micro-open-question for the client).
 */
@Entity
@Table(name = "terna_benchmark_areas")
@Getter
@Setter
@NoArgsConstructor
public class TernaBenchmarkArea {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "terna_id", nullable = false)
    private Long ternaId;

    @Column(name = "questionnaire_id", nullable = false)
    private Long questionnaireId;

    @Column(name = "area_id", nullable = false)
    private Long areaId;

    @Column(name = "best_facility_id")
    private Long bestFacilityId;

    @Column(name = "best_score")
    private BigDecimal bestScore;

    @Column(name = "computed_at")
    private OffsetDateTime computedAt;
}
