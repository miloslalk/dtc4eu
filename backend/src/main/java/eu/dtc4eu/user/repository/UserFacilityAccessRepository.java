package eu.dtc4eu.user.repository;

import eu.dtc4eu.user.entity.UserFacilityAccess;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UserFacilityAccessRepository extends JpaRepository<UserFacilityAccess, UserFacilityAccess.Key> {

    @Query("select a.facilityId from UserFacilityAccess a where a.userId = :userId")
    List<Long> findFacilityIdsByUserId(@Param("userId") Long userId);

    /** Bulk delete executes immediately — safe to re-insert the same keys in one transaction. */
    @Modifying
    @Query("delete from UserFacilityAccess a where a.userId = :userId")
    void deleteByUserId(@Param("userId") Long userId);
}
