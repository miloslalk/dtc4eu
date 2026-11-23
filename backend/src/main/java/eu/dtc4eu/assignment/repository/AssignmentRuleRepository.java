package eu.dtc4eu.assignment.repository;

import eu.dtc4eu.assignment.entity.AssignmentRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AssignmentRuleRepository extends JpaRepository<AssignmentRule, Long> {

    List<AssignmentRule> findAllByOrderByPriorityDescIdAsc();
}
