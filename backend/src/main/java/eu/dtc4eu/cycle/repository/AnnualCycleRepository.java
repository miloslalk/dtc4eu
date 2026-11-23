package eu.dtc4eu.cycle.repository;

import eu.dtc4eu.cycle.entity.AnnualCycle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface AnnualCycleRepository extends JpaRepository<AnnualCycle, Long> {

    /** Deterministic fallback when no cycle is selected explicitly: the most recently started OPEN cycle. */
    Optional<AnnualCycle> findFirstByStatusOrderByStartDateDescIdDesc(AnnualCycle.Status status);

    Optional<AnnualCycle> findByCode(String code);

    boolean existsByCode(String code);

    /** Re-derives every cycle's ordinal from its start date (V15), so back-filled cycles slot in correctly. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "update annual_cycles c set sequence_number = ranked.n from (select id, row_number()"
            + " over (order by start_date, code) as n from annual_cycles) ranked where ranked.id = c.id",
            nativeQuery = true)
    void renumberByStartDate();

    /** Only one landing default at a time (unique partial index, V16). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "update annual_cycles set is_default = false where is_default and id <> :id", nativeQuery = true)
    void clearDefaultExcept(@org.springframework.data.repository.query.Param("id") Long id);
}
