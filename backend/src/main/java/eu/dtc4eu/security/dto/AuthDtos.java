package eu.dtc4eu.security.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Set;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {
    }

    public record RefreshRequest(@NotBlank String refreshToken) {
    }

    public record TokenResponse(String accessToken, String refreshToken, UserInfo user) {

        public TokenResponse withUser(UserInfo next) {
            return new TokenResponse(accessToken, refreshToken, next);
        }
    }

    /** mustChangePassword: the client shows only the change-password screen until it is cleared (X4). */
    public record UserInfo(Long id, String username, String fullName, String email, String preferredLang,
                           Set<String> roles, boolean mustChangePassword) {

        public UserInfo withMustChangePassword(boolean flag) {
            return new UserInfo(id, username, fullName, email, preferredLang, roles, flag);
        }
    }

    public record ChangePasswordRequest(@NotBlank String currentPassword,
                                        @NotBlank @Size(min = 10, max = 100) String newPassword) {
    }

    /** Data scope of the caller (spec §6); empty lists mean the role is not list-restricted. */
    public record ScopeInfo(List<Long> facilityIds, List<Long> ternaIds) {
    }

    public record CycleInfo(Long id, String code, String status, Integer sequenceNumber) {
    }

    /** defaultCycle: the most recently started OPEN cycle — a selector default only; cycles overlap (v0.2 §8). */
    public record MeResponse(UserInfo user, ScopeInfo scope, CycleInfo defaultCycle) {

        public MeResponse withUser(UserInfo next) {
            return new MeResponse(next, scope, defaultCycle);
        }
    }
}
