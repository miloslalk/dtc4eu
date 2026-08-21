package eu.dtc4eu.questionnaire.repository;

import eu.dtc4eu.questionnaire.entity.QuestionnaireFormAreaLabel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QuestionnaireFormAreaLabelRepository extends JpaRepository<QuestionnaireFormAreaLabel, Long> {

    List<QuestionnaireFormAreaLabel> findByFormIdIn(List<Long> formIds);
}
