package eu.dtc4eu.facility.repository;

import eu.dtc4eu.facility.entity.FacilityMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FacilityMemberRepository extends JpaRepository<FacilityMember, Long> {

    List<FacilityMember> findByFacilityIdAndAnnualCycleIdOrderByCategoryAscMemberCodeAsc(Long facilityId,
                                                                                         Long annualCycleId);

    Optional<FacilityMember> findByMemberCodeAndAnnualCycleId(String memberCode, Long annualCycleId);

    long countByFacilityIdAndAnnualCycleIdAndCategory(Long facilityId, Long annualCycleId,
                                                      FacilityMember.Category category);
}
