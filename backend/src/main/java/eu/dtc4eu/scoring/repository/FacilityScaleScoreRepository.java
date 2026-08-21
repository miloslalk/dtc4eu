package eu.dtc4eu.scoring.repository;

import eu.dtc4eu.scoring.entity.FacilityScaleScore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface FacilityScaleScoreRepository extends JpaRepository<FacilityScaleScore, Long> {

    List<FacilityScaleScore> findByFacilityIdAndAnnualCycleIdAndQuestionnaireIdAndMetricTypeIn(
            Long facilityId, Long annualCycleId, Long questionnaireId,
            Collection<FacilityScaleScore.MetricType> metricTypes);

    List<FacilityScaleScore> findByFacilityIdAndAnnualCycleIdAndMetricType(
            Long facilityId, Long annualCycleId, FacilityScaleScore.MetricType metricType);

    List<FacilityScaleScore> findByFacilityIdAndMetricType(Long facilityId, FacilityScaleScore.MetricType metricType);

    /** Bulk delete executing immediately — the rewrite's inserts must not flush before it. */
    @Modifying
    @Query("delete from FacilityScaleScore s where s.facilityId = :facilityId"
            + " and s.annualCycleId = :cycleId and s.questionnaireId = :questionnaireId"
            + " and s.metricType in :metricTypes")
    void deleteByFacilityIdAndAnnualCycleIdAndQuestionnaireIdAndMetricTypeIn(
            @Param("facilityId") Long facilityId, @Param("cycleId") Long annualCycleId,
            @Param("questionnaireId") Long questionnaireId,
            @Param("metricTypes") Collection<FacilityScaleScore.MetricType> metricTypes);
}
