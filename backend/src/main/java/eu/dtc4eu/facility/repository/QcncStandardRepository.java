package eu.dtc4eu.facility.repository;

import eu.dtc4eu.facility.entity.QcncStandard;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QcncStandardRepository extends JpaRepository<QcncStandard, Long> {

    List<QcncStandard> findAllByOrderBySortOrderAsc();
}
