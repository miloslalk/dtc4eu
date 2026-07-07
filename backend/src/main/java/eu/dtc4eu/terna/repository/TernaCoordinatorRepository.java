package eu.dtc4eu.terna.repository;

import eu.dtc4eu.terna.entity.TernaCoordinator;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface TernaCoordinatorRepository extends JpaRepository<TernaCoordinator, Long> {

    List<TernaCoordinator> findByTernaId(Long ternaId);

    List<TernaCoordinator> findByTernaIdIn(Collection<Long> ternaIds);

    @Modifying
    @Query("delete from TernaCoordinator tc where tc.ternaId = :ternaId")
    void deleteByTernaId(@Param("ternaId") Long ternaId);
}
