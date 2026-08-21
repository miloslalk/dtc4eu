package eu.dtc4eu.scoring.repository;

import eu.dtc4eu.scoring.entity.FacilityAreaScore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface FacilityAreaScoreRepository extends JpaRepository<FacilityAreaScore, Long> {

    List<FacilityAreaScore> findByFacilityIdAndAnnualCycleIdAndQuestionnaireId(Long facilityId, Long annualCycleId,
                                                                               Long questionnaireId);

    /** Bulk delete executing immediately — the rewrite's inserts must not flush before it. */
    @Modifying
    @Query("delete from FacilityAreaScore s where s.facilityId = :facilityId"
            + " and s.annualCycleId = :cycleId and s.questionnaireId = :questionnaireId")
    void deleteByFacilityIdAndAnnualCycleIdAndQuestionnaireId(@Param("facilityId") Long facilityId,
                                                              @Param("cycleId") Long annualCycleId,
                                                              @Param("questionnaireId") Long questionnaireId);

    List<FacilityAreaScore> findByFacilityIdInAndAnnualCycleIdAndQuestionnaireId(Collection<Long> facilityIds,
                                                                                 Long annualCycleId,
                                                                                 Long questionnaireId);
}
