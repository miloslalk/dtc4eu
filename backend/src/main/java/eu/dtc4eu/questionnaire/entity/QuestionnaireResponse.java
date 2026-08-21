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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * One administration of a questionnaire to one pseudonymous subject in one
 * cycle (Module 4). Raw answers live in questionnaire_response_items and are
 * immutable after submit (golden rule: never overwrite raw data).
 */
@Entity
@Table(name = "questionnaire_responses")
@Getter
@Setter
@NoArgsConstructor
public class QuestionnaireResponse {

    public enum RespondentCategory {
        USER, FAMILY, STAFF, MANAGER, LONG_FORM, DELEGATION
    }

    public enum Status { DRAFT, SUBMITTED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "questionnaire_id", nullable = false)
    private Long questionnaireId;

    @Column(name = "form_id")
    private Long formId;

    @Column(name = "annual_cycle_id", nullable = false)
    private Long annualCycleId;

    @Column(name = "facility_id")
    private Long facilityId;

    @Column(name = "subject_code", nullable = false, length = 40)
    private String subjectCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "respondent_category", nullable = false, length = 30)
    private RespondentCategory respondentCategory;

    @Enumerated(EnumType.STRING)
    @Column(name = "administration_mode", length = 40)
    private QuestionnaireForm.AdministrationMode administrationMode;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "profile_snapshot", columnDefinition = "jsonb")
    private Map<String, Object> profileSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.DRAFT;

    @Column(name = "submitted_at")
    private OffsetDateTime submittedAt;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private OffsetDateTime updatedAt;

    public boolean isSubmitted() {
        return status == Status.SUBMITTED;
    }
}
