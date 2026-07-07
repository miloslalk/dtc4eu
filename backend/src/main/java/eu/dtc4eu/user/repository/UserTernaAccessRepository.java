package eu.dtc4eu.user.repository;

import eu.dtc4eu.user.entity.UserTernaAccess;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UserTernaAccessRepository extends JpaRepository<UserTernaAccess, UserTernaAccess.Key> {

    @Query("select a.ternaId from UserTernaAccess a where a.userId = :userId")
    List<Long> findTernaIdsByUserId(@Param("userId") Long userId);

    void deleteByUserId(Long userId);
}
