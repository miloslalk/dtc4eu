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

/** Likert anchor label for one scale value (DTCRO: 1 Not at all … 4 Very much). */
@Entity
@Table(name = "questionnaire_scale_anchors")
@Getter
@Setter
@NoArgsConstructor
public class QuestionnaireScaleAnchor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "questionnaire_id", nullable = false)
    private Long questionnaireId;

    @Column(nullable = false)
    private Integer value;

    @Column(nullable = false, length = 200)
    private String label;
}
