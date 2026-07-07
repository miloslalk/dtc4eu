package eu.dtc4eu.facility.repository;

import eu.dtc4eu.facility.entity.Facility;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface FacilityRepository extends JpaRepository<Facility, Long>, JpaSpecificationExecutor<Facility> {

    boolean existsByFacilityCode(String facilityCode);
}
