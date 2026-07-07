package eu.dtc4eu.questionnaire.repository;

import eu.dtc4eu.questionnaire.entity.QuestionnaireResponse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface QuestionnaireResponseRepository
        extends JpaRepository<QuestionnaireResponse, Long>, JpaSpecificationExecutor<QuestionnaireResponse> {
}
