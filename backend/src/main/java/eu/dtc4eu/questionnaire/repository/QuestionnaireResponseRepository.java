package eu.dtc4eu.questionnaire.repository;

import eu.dtc4eu.questionnaire.entity.QuestionnaireResponse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface QuestionnaireResponseRepository
        extends JpaRepository<QuestionnaireResponse, Long>, JpaSpecificationExecutor<QuestionnaireResponse> {

    List<QuestionnaireResponse> findByFacilityIdAndAnnualCycleIdAndQuestionnaireIdAndStatus(
            Long facilityId, Long annualCycleId, Long questionnaireId, QuestionnaireResponse.Status status);

    long countByFacilityIdAndAnnualCycleIdAndQuestionnaireIdAndRespondentCategoryAndStatus(
            Long facilityId, Long annualCycleId, Long questionnaireId,
            QuestionnaireResponse.RespondentCategory respondentCategory, QuestionnaireResponse.Status status);

    long countByFacilityIdAndAnnualCycleIdAndRespondentCategoryAndStatus(
            Long facilityId, Long annualCycleId,
            QuestionnaireResponse.RespondentCategory respondentCategory, QuestionnaireResponse.Status status);
}
