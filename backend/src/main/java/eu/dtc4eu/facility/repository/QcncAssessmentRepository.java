package eu.dtc4eu.facility.repository;

import eu.dtc4eu.facility.entity.QcncAssessment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface QcncAssessmentRepository extends JpaRepository<QcncAssessment, Long> {

    Optional<QcncAssessment> findByFacilityIdAndAnnualCycleId(Long facilityId, Long annualCycleId);
}
