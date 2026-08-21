package eu.dtc4eu.qualityaudit.repository;

import eu.dtc4eu.qualityaudit.entity.Audit;
import eu.dtc4eu.qualityaudit.entity.AuditCoordinator;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface AuditCoordinatorRepository extends JpaRepository<AuditCoordinator, Long> {

    List<AuditCoordinator> findByAuditId(Long auditId);

    List<AuditCoordinator> findByAuditIdIn(Collection<Long> auditIds);

    /** Cross-audit check used when revoking auto-granted facility scope. */
    @Query("select count(c) from AuditCoordinator c, Audit a where c.auditId = a.id"
            + " and c.coordinatorId = :userId and a.facilityId = :facilityId")
    long countForFacility(@Param("userId") Long userId, @Param("facilityId") Long facilityId);

    /** Bulk delete executing immediately — the rewrite's inserts must not flush before it. */
    @Modifying
    @Query("delete from AuditCoordinator x where x.auditId = :auditId")
    void deleteByAuditId(@Param("auditId") Long auditId);
}
