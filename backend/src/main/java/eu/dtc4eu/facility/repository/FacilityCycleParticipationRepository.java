package eu.dtc4eu.facility.repository;

import eu.dtc4eu.facility.entity.FacilityCycleParticipation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface FacilityCycleParticipationRepository extends JpaRepository<FacilityCycleParticipation, Long> {

    Optional<FacilityCycleParticipation> findByFacilityIdAndAnnualCycleId(Long facilityId, Long annualCycleId);

    List<FacilityCycleParticipation> findByAnnualCycleIdAndFacilityIdIn(Long annualCycleId,
                                                                        Collection<Long> facilityIds);

    /** Cross-cycle view for the accreditation-history screen (v0.2 register §6). */
    List<FacilityCycleParticipation> findByFacilityId(Long facilityId);

    /** Grants an assigned Internal Facilitator access to the facility (native: the access table belongs to user/). */
    @Modifying
    @Query(value = "insert into user_facility_access (user_id, facility_id) values (:userId, :facilityId)"
            + " on conflict do nothing", nativeQuery = true)
    void grantFacilityAccess(@Param("userId") Long userId, @Param("facilityId") Long facilityId);

    @Modifying
    @Query(value = "delete from user_facility_access where user_id = :userId and facility_id = :facilityId",
            nativeQuery = true)
    void revokeFacilityAccess(@Param("userId") Long userId, @Param("facilityId") Long facilityId);
}
