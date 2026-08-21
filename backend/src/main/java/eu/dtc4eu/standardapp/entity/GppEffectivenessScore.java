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

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Module 6 — GPP effectiveness snapshot (V1 table, V9-renamed): links a GPP
 * document application to its corresponding questionnaire item and copies the
 * effectiveness levels from the facility scoring tables at snapshot time.
 */
@Entity
@Table(name = "gpp_effectiveness_scores")
@Getter
@Setter
@NoArgsConstructor
public class GppEffectivenessScore {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "standard_application_id", nullable = false)
    private Long standardApplicationId;

    @Column(name = "questionnaire_item_id")
    private Long questionnaireItemId;

    @Column(name = "individual_effectiveness")
    private BigDecimal individualEffectiveness;

    @Column(name = "mean_item_effectiveness")
    private BigDecimal meanItemEffectiveness;

    @Column(name = "mean_area_effectiveness")
    private BigDecimal meanAreaEffectiveness;

    @Column(name = "mean_quest_effectiveness")
    private BigDecimal meanQuestEffectiveness;

    @Column(name = "areas_with_score")
    private Integer areasWithScore;

    @Column(name = "areas_total")
    private Integer areasTotal;

    @Column(name = "computed_at", insertable = false, updatable = false)
    private OffsetDateTime computedAt;
}
