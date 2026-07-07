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

/** Thematic area of a questionnaire (DTCRO has 9); area means are the computed scores. */
@Entity
@Table(name = "questionnaire_areas")
@Getter
@Setter
@NoArgsConstructor
public class QuestionnaireArea {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "questionnaire_id", nullable = false)
    private Long questionnaireId;

    @Column(name = "area_code", nullable = false, length = 20)
    private String areaCode;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "sort_order")
    private Integer sortOrder;
}
