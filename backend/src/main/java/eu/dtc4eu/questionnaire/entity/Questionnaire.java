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

/** Scale definition (DTCRO, MHSRO, ViVaCom); Likert bounds are per questionnaire. */
@Entity
@Table(name = "questionnaires")
@Getter
@Setter
@NoArgsConstructor
public class Questionnaire {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String code;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(name = "scale_min", nullable = false)
    private int scaleMin = 1;

    @Column(name = "scale_max", nullable = false)
    private int scaleMax = 4;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    /** TAB.5 band table on this questionnaire's scale (V15); both null until the client supplies it. */
    @Column(name = "band_critical_max", precision = 5, scale = 2)
    private BigDecimal bandCriticalMax;

    @Column(name = "band_efficacy_min", precision = 5, scale = 2)
    private BigDecimal bandEfficacyMin;
}
