package eu.dtc4eu.questionnaire.repository;

import eu.dtc4eu.questionnaire.entity.ResponseAreaScore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ResponseAreaScoreRepository extends JpaRepository<ResponseAreaScore, Long> {

    List<ResponseAreaScore> findByResponseId(Long responseId);

    /** Immediate bulk delete — the scoring engine recomputes idempotently (delete + insert). */
    @Modifying
    @Query("delete from ResponseAreaScore s where s.responseId = :responseId")
    void deleteByResponseId(@Param("responseId") Long responseId);
}
