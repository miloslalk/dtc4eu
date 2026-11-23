package eu.dtc4eu.assignment.repository;

import eu.dtc4eu.assignment.entity.UserProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserProfileRepository extends JpaRepository<UserProfile, Long> {

    Optional<UserProfile> findByUserIdAndAnnualCycleId(Long userId, Long annualCycleId);
}
