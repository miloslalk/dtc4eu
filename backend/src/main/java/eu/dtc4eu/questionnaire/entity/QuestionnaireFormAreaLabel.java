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

/**
 * Form-specific area header phrasing (e.g. USER form shows "Coming to terms
 * with the illness" for R5). Canonical names stay on questionnaire_areas;
 * display policy per form is open question 12.5 of the v0.2 register.
 */
@Entity
@Table(name = "questionnaire_form_area_labels")
@Getter
@Setter
@NoArgsConstructor
public class QuestionnaireFormAreaLabel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "form_id", nullable = false)
    private Long formId;

    @Column(name = "area_id", nullable = false)
    private Long areaId;

    @Column(name = "display_name", nullable = false, length = 200)
    private String displayName;
}
