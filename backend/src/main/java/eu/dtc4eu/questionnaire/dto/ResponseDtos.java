package eu.dtc4eu.questionnaire.dto;

import eu.dtc4eu.questionnaire.entity.QuestionnaireForm;
import eu.dtc4eu.questionnaire.entity.QuestionnaireResponse;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public final class ResponseDtos {

    private ResponseDtos() {
    }

    public record ResponseRowDto(Long id, Long questionnaireId, String questionnaireCode, Long facilityId,
                                 String facilityCode, String subjectCode,
                                 QuestionnaireResponse.RespondentCategory respondentCategory,
                                 QuestionnaireResponse.Status status, OffsetDateTime submittedAt,
                                 OffsetDateTime createdAt) {
    }

    public record ResponseItemDto(Long itemId, Integer numericValue, String textValue) {
    }

    public record ResponseDetailDto(Long id, Long questionnaireId, String questionnaireCode, Long formId,
                                    Long facilityId, String facilityCode, String subjectCode,
                                    QuestionnaireResponse.RespondentCategory respondentCategory,
                                    QuestionnaireForm.AdministrationMode administrationMode,
                                    QuestionnaireResponse.Status status, OffsetDateTime submittedAt,
                                    Map<String, Object> profileSnapshot,
                                    List<ResponseItemDto> items, long answeredLikert, long totalLikert) {
    }

    /**
     * formId is required when the questionnaire defines forms (the six DTCRO
     * forms); respondent category and administration mode are then derived
     * from the form. respondentCategory is only read for form-less questionnaires.
     */
    public record CreateResponseRequest(
            @NotNull Long questionnaireId,
            Long formId,
            Long facilityId,
            @NotBlank @Size(max = 40) String subjectCode,
            QuestionnaireResponse.RespondentCategory respondentCategory,
            QuestionnaireForm.AdministrationMode administrationMode,
            Map<String, Object> profileSnapshot) {
    }

    public record UpdateResponseRequest(
            @NotBlank @Size(max = 40) String subjectCode,
            Long facilityId,
            QuestionnaireResponse.RespondentCategory respondentCategory,
            QuestionnaireForm.AdministrationMode administrationMode,
            Map<String, Object> profileSnapshot) {
    }

    public record ItemAnswer(@NotNull Long itemId, Integer numericValue, String textValue) {
    }

    /** Autosave payload — partial batches allowed; answers upsert per item. */
    public record SaveItemsRequest(@NotEmpty List<ItemAnswer> items) {
    }

    public record SaveItemsResult(long answeredLikert, long totalLikert,
                                  QuestionnaireResponse.Status status) {
    }

    public record AreaScoreDto(Long areaId, String areaCode, String areaName, BigDecimal meanValue,
                               OffsetDateTime computedAt) {
    }
}
