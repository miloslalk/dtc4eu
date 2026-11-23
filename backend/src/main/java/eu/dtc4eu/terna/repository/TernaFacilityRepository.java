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

    List<TernaFacility> findByFacilityId(Long facilityId);

    List<TernaFacility> findByTernaIdIn(Collection<Long> ternaIds);

    /** Member rows of ternas in one cycle that involve a given facility (one-terna-per-cycle rule). */
    List<TernaFacility> findByFacilityIdInAndTernaIdIn(Collection<Long> facilityIds, Collection<Long> ternaIds);

    /**
     * Codes of the given facilities whose setting differs from {@code setting}
     * (spec §3.3: a terna links facilities of its own setting only). Native SQL
     * keeps the terna domain free of a Java dependency on the facility entity.
     */
    @Query(value = "select f.facility_code from facilities f where f.id in (:facilityIds)"
            + " and f.facility_type <> :setting order by f.facility_code", nativeQuery = true)
    List<String> findFacilityCodesOutsideSetting(@Param("facilityIds") Collection<Long> facilityIds,
                                                 @Param("setting") String setting);

    @Modifying
    @Query("delete from TernaFacility tf where tf.ternaId = :ternaId")
    void deleteByTernaId(@Param("ternaId") Long ternaId);
}
