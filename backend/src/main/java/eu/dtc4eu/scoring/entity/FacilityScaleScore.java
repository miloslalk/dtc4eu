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

/**
 * Point metric (V1 table, extended by V8 with item_letter): strength/weakness
 * points and Excellence Points per facility, scale and cycle. An item-level
 * point is a coordinate (area_id + item_letter); area-level rows leave the
 * letter NULL. QUAL_* rows will come from QCNC; EXCELLENCE_POINT rows for
 * VIVACOM history may be entered manually (register 12.4).
 */
@Entity
@Table(name = "facility_scale_scores")
@Getter
@Setter
@NoArgsConstructor
public class FacilityScaleScore {

    public enum MetricType {
        EXCELLENCE_POINT, QUAL_STRENGTH, QUANT_STRENGTH, QUAL_WEAKNESS, QUANT_WEAKNESS, BENCHMARK_AREA
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "facility_id", nullable = false)
    private Long facilityId;

    @Column(name = "annual_cycle_id", nullable = false)
    private Long annualCycleId;

    @Column(name = "questionnaire_id", nullable = false)
    private Long questionnaireId;

    @Enumerated(EnumType.STRING)
    @Column(name = "metric_type", nullable = false, length = 30)
    private MetricType metricType;

    @Column(name = "area_id")
    private Long areaId;

    @Column(name = "item_id")
    private Long itemId;

    @Column(name = "item_letter", length = 1)
    private String itemLetter;

    @Column
    private BigDecimal value;

    @Column(name = "computed_at")
    private OffsetDateTime computedAt;
}
