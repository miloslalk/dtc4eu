package eu.dtc4eu.qualityaudit.repository;

import eu.dtc4eu.qualityaudit.entity.AuditEvaluator;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface AuditEvaluatorRepository extends JpaRepository<AuditEvaluator, Long> {

    List<AuditEvaluator> findByAuditId(Long auditId);

    List<AuditEvaluator> findByAuditIdIn(Collection<Long> auditIds);

    /** Bulk delete executing immediately — the rewrite's inserts must not flush before it. */
    @Modifying
    @Query("delete from AuditEvaluator x where x.auditId = :auditId")
    void deleteByAuditId(@Param("auditId") Long auditId);
}
