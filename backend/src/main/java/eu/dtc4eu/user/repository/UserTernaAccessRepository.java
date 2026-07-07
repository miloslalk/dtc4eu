package eu.dtc4eu.user.repository;

import eu.dtc4eu.user.entity.UserTernaAccess;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UserTernaAccessRepository extends JpaRepository<UserTernaAccess, UserTernaAccess.Key> {

    @Query("select a.ternaId from UserTernaAccess a where a.userId = :userId")
    List<Long> findTernaIdsByUserId(@Param("userId") Long userId);

    /** Facilities belonging to the user's assigned ternas (coordinator visibility, spec §6). */
    @Query(value = """
            select distinct tf.facility_id
            from terna_facilities tf
            join user_terna_access uta on uta.terna_id = tf.terna_id
            where uta.user_id = :userId
            """, nativeQuery = true)
    List<Long> findCoordinatorFacilityIds(@Param("userId") Long userId);

    /** Bulk delete executes immediately — safe to re-insert the same keys in one transaction. */
    @Modifying
    @Query("delete from UserTernaAccess a where a.userId = :userId")
    void deleteByUserId(@Param("userId") Long userId);
}
