package eu.dtc4eu.terna.repository;

import eu.dtc4eu.terna.entity.TernaVisitDate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface TernaVisitDateRepository extends JpaRepository<TernaVisitDate, Long> {

    List<TernaVisitDate> findByTernaIdOrderByVisitOrderAsc(Long ternaId);

    List<TernaVisitDate> findByTernaIdIn(Collection<Long> ternaIds);

    @Modifying
    @Query("delete from TernaVisitDate v where v.ternaId = :ternaId")
    void deleteByTernaId(@Param("ternaId") Long ternaId);
}
