package eu.dtc4eu.cycle.repository;

import eu.dtc4eu.cycle.entity.AnnualCycle;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AnnualCycleRepository extends JpaRepository<AnnualCycle, Long> {

    Optional<AnnualCycle> findByCurrentTrue();

    Optional<AnnualCycle> findByCode(String code);

    boolean existsByCode(String code);
}
