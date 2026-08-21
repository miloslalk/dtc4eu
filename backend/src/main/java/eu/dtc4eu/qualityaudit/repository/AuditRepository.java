package eu.dtc4eu.qualityaudit.repository;

import eu.dtc4eu.qualityaudit.entity.Audit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface AuditRepository extends JpaRepository<Audit, Long> {

    List<Audit> findByAnnualCycleIdOrderByAuditCodeAsc(Long annualCycleId);

    List<Audit> findByAnnualCycleIdAndFacilityIdInOrderByAuditCodeAsc(Long annualCycleId,
                                                                      Collection<Long> facilityIds);

    List<Audit> findByAnnualCycleIdAndFacilityIdOrderByAuditCodeAsc(Long annualCycleId, Long facilityId);

    boolean existsByAuditCodeAndAnnualCycleId(String auditCode, Long annualCycleId);
}
