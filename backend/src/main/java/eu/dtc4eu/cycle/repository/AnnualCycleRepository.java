package eu.dtc4eu.cycle.repository;

import eu.dtc4eu.cycle.entity.AnnualCycle;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AnnualCycleRepository extends JpaRepository<AnnualCycle, Long> {

    /** Deterministic fallback when no cycle is selected explicitly: the most recently started OPEN cycle. */
    Optional<AnnualCycle> findFirstByStatusOrderByStartDateDescIdDesc(AnnualCycle.Status status);

    Optional<AnnualCycle> findByCode(String code);

    boolean existsByCode(String code);
}
