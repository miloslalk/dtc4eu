package eu.dtc4eu.terna.repository;

import eu.dtc4eu.terna.entity.Terna;
import eu.dtc4eu.terna.entity.TernaCoordinator;
import eu.dtc4eu.terna.entity.TernaFacility;
import eu.dtc4eu.terna.entity.TernaVisitDate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public final class TernaRepositories {

    private TernaRepositories() {
    }

    public interface TernaRepository extends JpaRepository<Terna, Long>, JpaSpecificationExecutor<Terna> {

        Optional<Terna> findByTernaCodeAndAnnualCycleId(String ternaCode, Long annualCycleId);
    }

    public interface TernaFacilityRepository extends JpaRepository<TernaFacility, Long> {

        List<TernaFacility> findByTernaIdOrderByPositionAsc(Long ternaId);

        List<TernaFacility> findByTernaIdIn(Collection<Long> ternaIds);

        /** Member rows of ternas in one cycle that involve a given facility (one-terna-per-cycle rule). */
        List<TernaFacility> findByFacilityIdInAndTernaIdIn(Collection<Long> facilityIds, Collection<Long> ternaIds);

        @Modifying
        @Query("delete from TernaFacility tf where tf.ternaId = :ternaId")
        void deleteByTernaId(@Param("ternaId") Long ternaId);
    }

    public interface TernaCoordinatorRepository extends JpaRepository<TernaCoordinator, Long> {

        List<TernaCoordinator> findByTernaId(Long ternaId);

        List<TernaCoordinator> findByTernaIdIn(Collection<Long> ternaIds);

        @Modifying
        @Query("delete from TernaCoordinator tc where tc.ternaId = :ternaId")
        void deleteByTernaId(@Param("ternaId") Long ternaId);
    }

    public interface TernaVisitDateRepository extends JpaRepository<TernaVisitDate, Long> {

        List<TernaVisitDate> findByTernaIdOrderByVisitOrderAsc(Long ternaId);

        List<TernaVisitDate> findByTernaIdIn(Collection<Long> ternaIds);

        @Modifying
        @Query("delete from TernaVisitDate v where v.ternaId = :ternaId")
        void deleteByTernaId(@Param("ternaId") Long ternaId);
    }
}
