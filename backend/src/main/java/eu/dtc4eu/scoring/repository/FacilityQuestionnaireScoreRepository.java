package eu.dtc4eu.scoring.repository;

import eu.dtc4eu.scoring.entity.FacilityQuestionnaireScore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface FacilityQuestionnaireScoreRepository extends JpaRepository<FacilityQuestionnaireScore, Long> {

    Optional<FacilityQuestionnaireScore> findByFacilityIdAndAnnualCycleIdAndQuestionnaireId(Long facilityId,
                                                                                            Long annualCycleId,
                                                                                            Long questionnaireId);

    /** Bulk delete executing immediately — the rewrite's inserts must not flush before it. */
    @Modifying
    @Query("delete from FacilityQuestionnaireScore s where s.facilityId = :facilityId"
            + " and s.annualCycleId = :cycleId and s.questionnaireId = :questionnaireId")
    void deleteByFacilityIdAndAnnualCycleIdAndQuestionnaireId(@Param("facilityId") Long facilityId,
                                                              @Param("cycleId") Long annualCycleId,
                                                              @Param("questionnaireId") Long questionnaireId);

    /** The questionnaire's TAB.5 band limits [critical max, efficacy min] (V15); native to stay off questionnaire entities. */
    @Query(value = "select band_critical_max, band_efficacy_min from questionnaires where id = :id", nativeQuery = true)
    java.util.List<Object[]> findBands(@Param("id") Long questionnaireId);

    /** The questionnaire's scale [min, max]; answers outside it (ViVaCom 8, 9; V18) are not scored. Native like findBands. */
    @Query(value = "select scale_min, scale_max from questionnaires where id = :id", nativeQuery = true)
    java.util.List<Object[]> findScale(@Param("id") Long questionnaireId);
}
