package eu.dtc4eu.questionnaire.repository;

import eu.dtc4eu.questionnaire.entity.QuestionnaireScaleAnchor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QuestionnaireScaleAnchorRepository extends JpaRepository<QuestionnaireScaleAnchor, Long> {

    List<QuestionnaireScaleAnchor> findByQuestionnaireIdOrderByValue(Long questionnaireId);
}
