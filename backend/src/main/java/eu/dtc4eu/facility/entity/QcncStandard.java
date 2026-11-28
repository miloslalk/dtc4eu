package eu.dtc4eu.facility.entity;

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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Map;

/**
 * QCNC catalogue entry (V11 seed, verbatim from "BRUSCHETTA C N-C Quest"
 * 2026): 5 clusters Q1–Q5, organizational-structural vs community-functional
 * standards; the mandatory Q1 block (1D,1E,1K,1O,1P,1Q,1R) per the expert
 * annotation, joined by 1A–1C in V18, so every Q1 standard now counts.
 */
@Entity
@Table(name = "qcnc_standards")
@Getter
@Setter
@NoArgsConstructor
public class QcncStandard {

    public enum StandardType {ORGANIZATIONAL_STRUCTURAL, COMMUNITY_FUNCTIONAL}

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cluster_code", nullable = false, length = 2)
    private String clusterCode;

    @Column(name = "letter_code", nullable = false, length = 4)
    private String letterCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "standard_type", nullable = false, length = 30)
    private StandardType standardType;

    @Column(name = "is_mandatory", nullable = false)
    private boolean mandatory;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "setting_parameters", columnDefinition = "jsonb")
    private Map<String, Object> settingParameters;

    @Column
    private String description;

    @Column(name = "sort_order")
    private Integer sortOrder;
}
