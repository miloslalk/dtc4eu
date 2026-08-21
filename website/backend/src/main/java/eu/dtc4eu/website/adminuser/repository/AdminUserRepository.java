package eu.dtc4eu.website.adminuser.repository;

import eu.dtc4eu.website.adminuser.entity.AdminUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AdminUserRepository extends JpaRepository<AdminUser, Long> {

    Optional<AdminUser> findByUsername(String username);

    boolean existsByUsername(String username);

    long countByRoleAndActiveTrue(AdminUser.Role role);
}
