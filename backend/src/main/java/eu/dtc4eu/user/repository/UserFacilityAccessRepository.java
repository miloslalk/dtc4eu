package eu.dtc4eu.user.repository;

import eu.dtc4eu.user.entity.UserFacilityAccess;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UserFacilityAccessRepository extends JpaRepository<UserFacilityAccess, UserFacilityAccess.Key> {

    @Query("select a.facilityId from UserFacilityAccess a where a.userId = :userId")
    List<Long> findFacilityIdsByUserId(@Param("userId") Long userId);

    void deleteByUserId(Long userId);
}
