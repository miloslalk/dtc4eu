package eu.dtc4eu.questionnaire.repository;

import eu.dtc4eu.questionnaire.entity.QuestionnaireForm;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QuestionnaireFormRepository extends JpaRepository<QuestionnaireForm, Long> {

    List<QuestionnaireForm> findByQuestionnaireIdOrderByIdAsc(Long questionnaireId);

    long countByQuestionnaireId(Long questionnaireId);
}
