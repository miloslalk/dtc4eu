package eu.dtc4eu.questionnaire.dto;

import eu.dtc4eu.facility.entity.Facility;
import eu.dtc4eu.questionnaire.entity.Questionnaire;
import eu.dtc4eu.questionnaire.entity.QuestionnaireArea;
import eu.dtc4eu.questionnaire.entity.QuestionnaireForm;
import eu.dtc4eu.questionnaire.entity.QuestionnaireItem;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class QuestionnaireDtos {

    private QuestionnaireDtos() {
    }

    public record QuestionnaireRowDto(Long id, String code, String name, int scaleMin, int scaleMax,
                                      boolean active, long areaCount, long itemCount, long formCount) {
    }

    public record AreaDto(Long id, String areaCode, String name, Integer sortOrder) {

        public static AreaDto of(QuestionnaireArea area) {
            return new AreaDto(area.getId(), area.getAreaCode(), area.getName(), area.getSortOrder());
        }
    }

    public record ItemDto(Long id, Long areaId, String itemCode, String itemText,
                          QuestionnaireItem.ResponseType responseType, Integer sortOrder) {

        public static ItemDto of(QuestionnaireItem item) {
            return new ItemDto(item.getId(), item.getAreaId(), item.getItemCode(), item.getItemText(),
                    item.getResponseType(), item.getSortOrder());
        }
    }

    public record FormDto(Long id, QuestionnaireForm.AdministrationMode administrationMode,
                          QuestionnaireForm.StakeholderCategory stakeholderCategory,
                          Facility.FacilityType facilityType, String name) {

        public static FormDto of(QuestionnaireForm form) {
            return new FormDto(form.getId(), form.getAdministrationMode(), form.getStakeholderCategory(),
                    form.getFacilityType(), form.getName());
        }
    }

    /** Full definition — also consumed by the response-filling container (T5). */
    public record QuestionnaireDetailDto(Long id, String code, String name, int scaleMin, int scaleMax,
                                         boolean active, List<AreaDto> areas, List<ItemDto> items,
                                         List<FormDto> forms) {
    }

    public record CreateQuestionnaireRequest(
            @NotBlank @Size(max = 30) String code,
            @NotBlank @Size(max = 150) String name,
            @Min(0) @Max(10) Integer scaleMin,
            @Min(1) @Max(10) Integer scaleMax) {
    }

    public record UpdateQuestionnaireRequest(
            @NotBlank @Size(max = 150) String name,
            @Min(0) @Max(10) Integer scaleMin,
            @Min(1) @Max(10) Integer scaleMax,
            Boolean active) {
    }

    public record SaveAreaRequest(@NotBlank @Size(max = 20) String areaCode,
                                  @NotBlank @Size(max = 200) String name,
                                  Integer sortOrder) {
    }

    public record SaveItemRequest(Long areaId,
                                  @NotBlank @Size(max = 20) String itemCode,
                                  @NotBlank String itemText,
                                  @NotNull QuestionnaireItem.ResponseType responseType,
                                  Integer sortOrder) {
    }

    public record SaveFormRequest(@NotNull QuestionnaireForm.AdministrationMode administrationMode,
                                  QuestionnaireForm.StakeholderCategory stakeholderCategory,
                                  Facility.FacilityType facilityType,
                                  @Size(max = 150) String name) {
    }
}
