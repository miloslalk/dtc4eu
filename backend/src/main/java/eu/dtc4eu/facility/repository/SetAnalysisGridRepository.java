package eu.dtc4eu.facility.repository;

import eu.dtc4eu.facility.entity.SetAnalysisGrid;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SetAnalysisGridRepository extends JpaRepository<SetAnalysisGrid, Long> {

    List<SetAnalysisGrid> findByFacilityIdAndAnnualCycleIdOrderByGridVariantAsc(Long facilityId, Long annualCycleId);

    Optional<SetAnalysisGrid> findByFacilityIdAndAnnualCycleIdAndGridVariant(Long facilityId, Long annualCycleId,
                                                                             String gridVariant);
}
