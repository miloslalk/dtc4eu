package eu.dtc4eu.questionnaire.repository;

import eu.dtc4eu.questionnaire.entity.Questionnaire;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface QuestionnaireRepository extends JpaRepository<Questionnaire, Long> {

    Optional<Questionnaire> findByCode(String code);

    boolean existsByCode(String code);

    /** Answers accepted outside the scale but not scored (ViVaCom 8, 9; V18). */
    @Query("select a.value from QuestionnaireScaleAnchor a where a.questionnaireId = :id and a.countsInScore = false"
            + " order by a.value")
    List<Integer> findNonScoringValues(@Param("id") Long questionnaireId);
}
