package eu.dtc4eu.questionnaire.repository;

import eu.dtc4eu.questionnaire.entity.QuestionnaireForm;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QuestionnaireFormRepository extends JpaRepository<QuestionnaireForm, Long> {

    List<QuestionnaireForm> findByQuestionnaireIdOrderBySortOrderAscIdAsc(Long questionnaireId);

    boolean existsByQuestionnaireIdAndFormCode(Long questionnaireId, String formCode);

    long countByQuestionnaireId(Long questionnaireId);
}
