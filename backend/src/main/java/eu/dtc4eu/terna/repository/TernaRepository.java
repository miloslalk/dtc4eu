package eu.dtc4eu.terna.repository;

import eu.dtc4eu.terna.entity.Terna;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface TernaRepository extends JpaRepository<Terna, Long>, JpaSpecificationExecutor<Terna> {

    Optional<Terna> findByTernaCodeAndAnnualCycleId(String ternaCode, Long annualCycleId);
}
