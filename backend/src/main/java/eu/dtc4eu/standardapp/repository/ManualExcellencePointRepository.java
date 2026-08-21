package eu.dtc4eu.standardapp.repository;

import eu.dtc4eu.standardapp.entity.ManualExcellencePoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ManualExcellencePointRepository extends JpaRepository<ManualExcellencePoint, Long> {

    List<ManualExcellencePoint> findByFacilityIdOrderByAnnualCycleIdAscQuestionnaireCodeAscLabelAsc(Long facilityId);

    List<ManualExcellencePoint> findByFacilityIdAndAnnualCycleIdAndQuestionnaireCodeOrderByLabelAsc(
            Long facilityId, Long annualCycleId, String questionnaireCode);

    /** Bulk delete executing immediately — the rewrite's inserts must not flush before it. */
    @Modifying
    @Query("delete from ManualExcellencePoint p where p.facilityId = :facilityId"
            + " and p.annualCycleId = :cycleId and p.questionnaireCode = :code")
    void deleteByFacilityIdAndAnnualCycleIdAndQuestionnaireCode(@Param("facilityId") Long facilityId,
                                                                @Param("cycleId") Long annualCycleId,
                                                                @Param("code") String questionnaireCode);
}
