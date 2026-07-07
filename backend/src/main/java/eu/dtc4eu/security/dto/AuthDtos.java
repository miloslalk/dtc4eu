package eu.dtc4eu.security.dto;

import jakarta.validation.constraints.NotBlank;

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
    }

    public record UserInfo(Long id, String username, String fullName, String email, String preferredLang,
                           Set<String> roles) {
    }

    /** Data scope of the caller (spec §6); empty lists mean the role is not list-restricted. */
    public record ScopeInfo(List<Long> facilityIds, List<Long> ternaIds) {
    }

    public record CycleInfo(Long id, String code, String status, boolean current) {
    }

    public record MeResponse(UserInfo user, ScopeInfo scope, CycleInfo currentCycle) {
    }
}
