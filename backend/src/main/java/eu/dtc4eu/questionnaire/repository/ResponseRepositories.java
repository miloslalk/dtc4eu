package eu.dtc4eu.questionnaire.repository;

import eu.dtc4eu.questionnaire.entity.QuestionnaireResponse;
import eu.dtc4eu.questionnaire.entity.QuestionnaireResponseItem;
import eu.dtc4eu.questionnaire.entity.ResponseAreaScore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public final class ResponseRepositories {

    private ResponseRepositories() {
    }

    public interface QuestionnaireResponseRepository
            extends JpaRepository<QuestionnaireResponse, Long>, JpaSpecificationExecutor<QuestionnaireResponse> {
    }

    public interface QuestionnaireResponseItemRepository extends JpaRepository<QuestionnaireResponseItem, Long> {

        List<QuestionnaireResponseItem> findByResponseId(Long responseId);

        Optional<QuestionnaireResponseItem> findByResponseIdAndItemId(Long responseId, Long itemId);

        long countByResponseIdAndNumericValueNotNull(Long responseId);
    }

    public interface ResponseAreaScoreRepository extends JpaRepository<ResponseAreaScore, Long> {

        List<ResponseAreaScore> findByResponseId(Long responseId);

        /** Immediate bulk delete — the scoring engine recomputes idempotently (delete + insert). */
        @Modifying
        @Query("delete from ResponseAreaScore s where s.responseId = :responseId")
        void deleteByResponseId(@Param("responseId") Long responseId);
    }
}
