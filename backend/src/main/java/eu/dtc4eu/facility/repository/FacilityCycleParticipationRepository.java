package eu.dtc4eu.facility.repository;

import eu.dtc4eu.facility.entity.FacilityCycleParticipation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface FacilityCycleParticipationRepository extends JpaRepository<FacilityCycleParticipation, Long> {

    Optional<FacilityCycleParticipation> findByFacilityIdAndAnnualCycleId(Long facilityId, Long annualCycleId);

    List<FacilityCycleParticipation> findByAnnualCycleIdAndFacilityIdIn(Long annualCycleId,
                                                                        Collection<Long> facilityIds);
}
