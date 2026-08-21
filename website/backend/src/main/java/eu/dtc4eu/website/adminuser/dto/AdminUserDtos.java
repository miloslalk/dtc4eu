package eu.dtc4eu.website.adminuser.dto;

import eu.dtc4eu.website.adminuser.entity.AdminUser;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

public final class AdminUserDtos {

    private AdminUserDtos() {
    }

    public record AdminUserDto(Long id, String username, String fullName, AdminUser.Role role, boolean active,
                               OffsetDateTime createdAt) {

        public static AdminUserDto of(AdminUser user) {
            return new AdminUserDto(user.getId(), user.getUsername(), user.getFullName(), user.getRole(),
                    user.isActive(), user.getCreatedAt());
        }
    }

    public record CreateRequest(
            @NotBlank @Size(max = 60) String username,
            @NotBlank @Size(min = 8, max = 100) String password,
            @NotBlank @Size(max = 120) String fullName,
            @NotNull AdminUser.Role role) {
    }

    public record UpdateRequest(
            @NotBlank @Size(max = 120) String fullName,
            @NotNull AdminUser.Role role,
            @NotNull Boolean active,
            @Size(min = 8, max = 100) String password) {
    }
}
