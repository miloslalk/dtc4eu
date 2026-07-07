package eu.dtc4eu.questionnaire.repository;

import eu.dtc4eu.questionnaire.entity.QuestionnaireArea;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QuestionnaireAreaRepository extends JpaRepository<QuestionnaireArea, Long> {

    List<QuestionnaireArea> findByQuestionnaireIdOrderBySortOrderAscIdAsc(Long questionnaireId);

    boolean existsByQuestionnaireIdAndAreaCode(Long questionnaireId, String areaCode);

    long countByQuestionnaireId(Long questionnaireId);
}
