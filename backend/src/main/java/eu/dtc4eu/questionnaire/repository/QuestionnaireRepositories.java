package eu.dtc4eu.questionnaire.repository;

import eu.dtc4eu.questionnaire.entity.Questionnaire;
import eu.dtc4eu.questionnaire.entity.QuestionnaireArea;
import eu.dtc4eu.questionnaire.entity.QuestionnaireForm;
import eu.dtc4eu.questionnaire.entity.QuestionnaireItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public final class QuestionnaireRepositories {

    private QuestionnaireRepositories() {
    }

    public interface QuestionnaireRepository extends JpaRepository<Questionnaire, Long> {

        Optional<Questionnaire> findByCode(String code);

        boolean existsByCode(String code);
    }

    public interface QuestionnaireAreaRepository extends JpaRepository<QuestionnaireArea, Long> {

        List<QuestionnaireArea> findByQuestionnaireIdOrderBySortOrderAscIdAsc(Long questionnaireId);

        boolean existsByQuestionnaireIdAndAreaCode(Long questionnaireId, String areaCode);

        long countByQuestionnaireId(Long questionnaireId);
    }

    public interface QuestionnaireItemRepository extends JpaRepository<QuestionnaireItem, Long> {

        List<QuestionnaireItem> findByQuestionnaireIdOrderBySortOrderAscIdAsc(Long questionnaireId);

        boolean existsByQuestionnaireIdAndItemCode(Long questionnaireId, String itemCode);

        long countByQuestionnaireId(Long questionnaireId);
    }

    public interface QuestionnaireFormRepository extends JpaRepository<QuestionnaireForm, Long> {

        List<QuestionnaireForm> findByQuestionnaireIdOrderByIdAsc(Long questionnaireId);

        long countByQuestionnaireId(Long questionnaireId);
    }
}
