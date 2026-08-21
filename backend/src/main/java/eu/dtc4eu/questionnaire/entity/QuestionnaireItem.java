package eu.dtc4eu.questionnaire.entity;

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

/** Single item; LIKERT items feed area means, FREE_TEXT items never do. */
@Entity
@Table(name = "questionnaire_items")
@Getter
@Setter
@NoArgsConstructor
public class QuestionnaireItem {

    public enum ResponseType { LIKERT, FREE_TEXT }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "questionnaire_id", nullable = false)
    private Long questionnaireId;

    @Column(name = "area_id")
    private Long areaId;

    /** Owning form; the coordinate (area, item_letter) is the cross-form aggregation key. */
    @Column(name = "form_id")
    private Long formId;

    @Column(name = "item_letter", length = 1)
    private String itemLetter;

    @Column(name = "item_code", nullable = false, length = 20)
    private String itemCode;

    @Column(name = "item_text", nullable = false)
    private String itemText;

    @Enumerated(EnumType.STRING)
    @Column(name = "response_type", nullable = false, length = 20)
    private ResponseType responseType = ResponseType.LIKERT;

    @Column(name = "sort_order")
    private Integer sortOrder;
}
