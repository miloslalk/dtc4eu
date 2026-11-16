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

/** One standard's Conformity/Non-Conformity mark within one administration. */
@Entity
@Table(name = "qcnc_standard_results")
@Getter
@Setter
@NoArgsConstructor
public class QcncStandardResult {

    public enum Conformity {C, NC}

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "assessment_id", nullable = false)
    private Long assessmentId;

    @Column(name = "standard_id", nullable = false)
    private Long standardId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 2)
    private Conformity conformity;

    /** Expert-feedback 1–4 score; the board derives the TAB.5 classification from the administrations' mean. */
    @Column
    private Integer score;

    @Column
    private String note;
}
