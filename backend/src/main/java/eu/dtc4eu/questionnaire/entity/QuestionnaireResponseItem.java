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

/** Raw item-level answer (Likert value or free text) — never overwritten after submit. */
@Entity
@Table(name = "questionnaire_response_items")
@Getter
@Setter
@NoArgsConstructor
public class QuestionnaireResponseItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "response_id", nullable = false)
    private Long responseId;

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Column(name = "numeric_value")
    private Integer numericValue;

    @Column(name = "text_value")
    private String textValue;
}
