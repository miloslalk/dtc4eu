package eu.dtc4eu.assignment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Module 5-bis assignment rule (spec COL-01): when the respondent's profile and
 * context satisfy the AND/OR condition tree, the target form is assigned. Rules
 * are data managed from the admin UI, never hardcoded.
 */
@Entity
@Table(name = "assignment_rules")
@Getter
@Setter
@NoArgsConstructor
public class AssignmentRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String name;

    private String description;

    /** Condition tree, see {@code RuleNode}. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> conditions;

    @Column(name = "target_questionnaire_id")
    private Long targetQuestionnaireId;

    @Column(name = "target_form_id", nullable = false)
    private Long targetFormId;

    @Column(nullable = false)
    private int priority;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "valid_from")
    private LocalDate validFrom;

    @Column(name = "valid_to")
    private LocalDate validTo;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private OffsetDateTime updatedAt;

    /** Active and inside its validity window on the given day (open-ended bounds allowed). */
    public boolean appliesOn(LocalDate day) {
        return active
                && (validFrom == null || !day.isBefore(validFrom))
                && (validTo == null || !day.isAfter(validTo));
    }
}
