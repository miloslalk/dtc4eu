package eu.dtc4eu.standardapp.repository;

import eu.dtc4eu.standardapp.entity.GppEffectivenessScore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface GppEffectivenessScoreRepository extends JpaRepository<GppEffectivenessScore, Long> {

    Optional<GppEffectivenessScore> findFirstByStandardApplicationIdOrderByIdDesc(Long standardApplicationId);

    /** Bulk delete executing immediately — the rewrite's inserts must not flush before it. */
    @Modifying
    @Query("delete from GppEffectivenessScore g where g.standardApplicationId = :applicationId")
    void deleteByStandardApplicationId(@Param("applicationId") Long applicationId);
}
