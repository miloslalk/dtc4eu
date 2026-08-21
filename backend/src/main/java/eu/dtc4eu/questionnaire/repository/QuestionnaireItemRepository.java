package eu.dtc4eu.questionnaire.repository;

import eu.dtc4eu.questionnaire.entity.QuestionnaireItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QuestionnaireItemRepository extends JpaRepository<QuestionnaireItem, Long> {

    List<QuestionnaireItem> findByQuestionnaireIdOrderBySortOrderAscIdAsc(Long questionnaireId);

    List<QuestionnaireItem> findByFormIdOrderBySortOrderAscIdAsc(Long formId);

    boolean existsByQuestionnaireIdAndItemCode(Long questionnaireId, String itemCode);

    boolean existsByFormIdAndAreaIdAndItemLetter(Long formId, Long areaId, String itemLetter);

    long countByQuestionnaireId(Long questionnaireId);

    long countByFormIdAndResponseType(Long formId, QuestionnaireItem.ResponseType responseType);
}
