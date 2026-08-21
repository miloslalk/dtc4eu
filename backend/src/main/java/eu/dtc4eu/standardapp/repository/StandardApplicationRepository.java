package eu.dtc4eu.standardapp.repository;

import eu.dtc4eu.standardapp.entity.StandardApplication;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StandardApplicationRepository extends JpaRepository<StandardApplication, Long> {

    List<StandardApplication> findByFacilityIdAndAnnualCycleIdOrderByStandardTypeAscStandardCodeAsc(
            Long facilityId, Long annualCycleId);

    /** Cross-cycle view for the accreditation-history screen. */
    List<StandardApplication> findByFacilityId(Long facilityId);

    boolean existsByFacilityIdAndAnnualCycleIdAndStandardTypeAndStandardCode(
            Long facilityId, Long annualCycleId, StandardApplication.StandardType standardType, String standardCode);
}
