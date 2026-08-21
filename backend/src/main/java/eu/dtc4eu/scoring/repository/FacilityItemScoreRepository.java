package eu.dtc4eu.scoring.repository;

import eu.dtc4eu.scoring.entity.FacilityItemScore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface FacilityItemScoreRepository extends JpaRepository<FacilityItemScore, Long> {

    List<FacilityItemScore> findByFacilityIdAndAnnualCycleIdAndQuestionnaireId(Long facilityId, Long annualCycleId,
                                                                               Long questionnaireId);

    /** Bulk delete executing immediately — the rewrite's inserts must not flush before it. */
    @Modifying
    @Query("delete from FacilityItemScore s where s.facilityId = :facilityId"
            + " and s.annualCycleId = :cycleId and s.questionnaireId = :questionnaireId")
    void deleteByFacilityIdAndAnnualCycleIdAndQuestionnaireId(@Param("facilityId") Long facilityId,
                                                              @Param("cycleId") Long annualCycleId,
                                                              @Param("questionnaireId") Long questionnaireId);

    List<FacilityItemScore> findByFacilityIdInAndAnnualCycleIdAndQuestionnaireId(Collection<Long> facilityIds,
                                                                                 Long annualCycleId,
                                                                                 Long questionnaireId);
}
