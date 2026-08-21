package eu.dtc4eu.facility.repository;

import eu.dtc4eu.facility.entity.QcncAssessment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface QcncAssessmentRepository extends JpaRepository<QcncAssessment, Long> {

    List<QcncAssessment> findByFacilityIdAndAnnualCycleId(Long facilityId, Long annualCycleId);

    Optional<QcncAssessment> findByFacilityIdAndAnnualCycleIdAndAdministrationType(
            Long facilityId, Long annualCycleId, QcncAssessment.AdministrationType administrationType);
}
