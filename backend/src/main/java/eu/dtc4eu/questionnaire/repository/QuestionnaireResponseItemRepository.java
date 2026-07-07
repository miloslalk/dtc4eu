package eu.dtc4eu.questionnaire.repository;

import eu.dtc4eu.questionnaire.entity.QuestionnaireResponseItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface QuestionnaireResponseItemRepository extends JpaRepository<QuestionnaireResponseItem, Long> {

    List<QuestionnaireResponseItem> findByResponseId(Long responseId);

    Optional<QuestionnaireResponseItem> findByResponseIdAndItemId(Long responseId, Long itemId);

    long countByResponseIdAndNumericValueNotNull(Long responseId);
}
