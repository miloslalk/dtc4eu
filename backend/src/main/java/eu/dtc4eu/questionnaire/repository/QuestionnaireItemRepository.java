package eu.dtc4eu.questionnaire.repository;

import eu.dtc4eu.questionnaire.entity.QuestionnaireItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QuestionnaireItemRepository extends JpaRepository<QuestionnaireItem, Long> {

    List<QuestionnaireItem> findByQuestionnaireIdOrderBySortOrderAscIdAsc(Long questionnaireId);

    boolean existsByQuestionnaireIdAndItemCode(Long questionnaireId, String itemCode);

    long countByQuestionnaireId(Long questionnaireId);
}
