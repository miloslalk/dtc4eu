package eu.dtc4eu.assignment.dto;

import eu.dtc4eu.assignment.entity.AssignmentRule;
import eu.dtc4eu.assignment.service.RuleConditions;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public final class AssignmentDtos {

    private AssignmentDtos() {
    }

    public record RuleDto(Long id, String name, String description, Map<String, Object> conditions,
                          Long targetQuestionnaireId, Long targetFormId, String targetFormCode,
                          String targetFormName, int priority, boolean active,
                          LocalDate validFrom, LocalDate validTo, OffsetDateTime updatedAt) {

        public static RuleDto of(AssignmentRule rule, String formCode, String formName) {
            return new RuleDto(rule.getId(), rule.getName(), rule.getDescription(), rule.getConditions(),
                    rule.getTargetQuestionnaireId(), rule.getTargetFormId(), formCode, formName,
                    rule.getPriority(), rule.isActive(), rule.getValidFrom(), rule.getValidTo(), rule.getUpdatedAt());
        }
    }

    public record SaveRuleRequest(@NotBlank @Size(max = 150) String name,
                                  @Size(max = 2000) String description,
                                  @NotNull Map<String, Object> conditions,
                                  @NotNull Long targetFormId,
                                  int priority,
                                  Boolean active,
                                  LocalDate validFrom,
                                  LocalDate validTo) {
    }

    /** Rule test tool (spec COL-02): a sample profile/context in, the forms it would receive out. */
    public record RuleTestRequest(@NotNull Map<RuleConditions.Field, List<String>> context, LocalDate onDate) {
    }

    public record AssignedFormDto(Long formId, Long questionnaireId, String questionnaireCode, String questionnaireName,
                                  String formCode, String formName, String administrationMode,
                                  Long ruleId, String ruleName) {
    }

    public record RuleTestResult(List<AssignedFormDto> forms) {
    }

    public record FacilityContextDto(Long facilityId, String facilityCode, String setting, String subtype,
                                     String country, String activity) {
    }

    /** The respondent's profile for the selected cycle plus the registry context derived from their account. */
    public record ProfileDto(Long cycleId, Integer age, String roleCategory, Long ternaId, String projectPhase,
                             String country, String city, OffsetDateTime updatedAt,
                             List<FacilityContextDto> facilities) {
    }

    public record SaveProfileRequest(@Min(0) @Max(120) Integer age,
                                     @Pattern(regexp = "USER|FAMILY|STAFF|MANAGER") String roleCategory,
                                     Long ternaId,
                                     @Pattern(regexp = "VISITING_TERNA|QUALITY_AUDIT|TRAINING_PAUSE")
                                     String projectPhase,
                                     @Size(max = 80) String country,
                                     @Size(max = 120) String city) {
    }

    public enum TaskStatus { NOT_STARTED, DRAFT, SUBMITTED }

    public record TaskDto(AssignedFormDto form, TaskStatus status, Long responseId) {
    }

    /** "Your questionnaires" (spec COL-03): only the assigned forms, with the evaluated context for transparency. */
    public record MyAssignmentsDto(boolean profileComplete, Map<RuleConditions.Field, List<String>> context,
                                   List<TaskDto> tasks) {
    }
}
