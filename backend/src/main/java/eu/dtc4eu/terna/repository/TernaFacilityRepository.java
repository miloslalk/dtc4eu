package eu.dtc4eu.terna.repository;

import eu.dtc4eu.terna.entity.TernaFacility;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface TernaFacilityRepository extends JpaRepository<TernaFacility, Long> {

    List<TernaFacility> findByTernaIdOrderByPositionAsc(Long ternaId);

    List<TernaFacility> findByTernaIdIn(Collection<Long> ternaIds);

    /** Member rows of ternas in one cycle that involve a given facility (one-terna-per-cycle rule). */
    List<TernaFacility> findByFacilityIdInAndTernaIdIn(Collection<Long> facilityIds, Collection<Long> ternaIds);

    @Modifying
    @Query("delete from TernaFacility tf where tf.ternaId = :ternaId")
    void deleteByTernaId(@Param("ternaId") Long ternaId);
}
