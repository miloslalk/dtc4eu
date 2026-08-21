package eu.dtc4eu.facility.repository;

import eu.dtc4eu.facility.entity.QcncStandardResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface QcncStandardResultRepository extends JpaRepository<QcncStandardResult, Long> {

    List<QcncStandardResult> findByAssessmentIdIn(Collection<Long> assessmentIds);

    /** Bulk delete executing immediately — the rewrite's inserts must not flush before it. */
    @Modifying
    @Query("delete from QcncStandardResult r where r.assessmentId = :assessmentId")
    void deleteByAssessmentId(@Param("assessmentId") Long assessmentId);
}
