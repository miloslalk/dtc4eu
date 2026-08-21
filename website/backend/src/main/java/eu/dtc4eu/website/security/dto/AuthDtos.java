package eu.dtc4eu.website.security.dto;

import eu.dtc4eu.website.adminuser.entity.AdminUser;
import jakarta.validation.constraints.NotBlank;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {
    }

    public record RefreshRequest(@NotBlank String refreshToken) {
    }

    public record TokenResponse(String accessToken, String refreshToken) {
    }

    public record MeResponse(Long id, String username, String fullName, AdminUser.Role role) {

        public static MeResponse of(AdminUser user) {
            return new MeResponse(user.getId(), user.getUsername(), user.getFullName(), user.getRole());
        }
    }
}
