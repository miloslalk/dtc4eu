package eu.dtc4eu.facility.repository;

import eu.dtc4eu.facility.entity.FacilityMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FacilityMemberRepository extends JpaRepository<FacilityMember, Long> {

    List<FacilityMember> findByFacilityIdAndAnnualCycleIdOrderByCategoryAscMemberCodeAsc(Long facilityId,
                                                                                         Long annualCycleId);

    /** Every registration of one pseudonymous code in a cycle, across facilities. */
    List<FacilityMember> findByAnnualCycleIdAndMemberCodeIgnoreCase(Long annualCycleId, String memberCode);

    long countByFacilityIdAndAnnualCycleIdAndCategory(Long facilityId, Long annualCycleId,
                                                      FacilityMember.Category category);
}
