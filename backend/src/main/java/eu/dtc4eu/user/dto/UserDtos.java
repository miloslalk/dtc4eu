package eu.dtc4eu.user.dto;

import eu.dtc4eu.user.entity.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

public final class UserDtos {

    private UserDtos() {
    }

    public record UserDto(Long id, String username, String email, String fullName, String preferredLang,
                          boolean active, Set<String> roles, OffsetDateTime createdAt) {

        public static UserDto of(User user) {
            return new UserDto(user.getId(), user.getUsername(), user.getEmail(), user.getFullName(),
                    user.getPreferredLang(), user.isActive(), user.roleCodes(), user.getCreatedAt());
        }
    }

    /** Detail view including data-scope assignments (spec §6). */
    public record UserDetailDto(Long id, String username, String email, String fullName, String preferredLang,
                                boolean active, Set<String> roles, List<Long> facilityIds, List<Long> ternaIds,
                                OffsetDateTime createdAt) {
    }

    public record CreateUserRequest(
            @NotBlank @Size(min = 3, max = 50) @Pattern(regexp = "[a-zA-Z0-9._-]+",
                    message = "letters, digits, dot, dash and underscore only") String username,
            @Email String email,
            @NotBlank @Size(max = 120) String fullName,
            String preferredLang,
            @NotBlank @Size(min = 8, max = 100) String password,
            @NotEmpty Set<String> roles,
            List<Long> facilityIds,
            List<Long> ternaIds) {
    }

    public record UpdateUserRequest(
            @Email String email,
            @NotBlank @Size(max = 120) String fullName,
            String preferredLang,
            @Size(min = 8, max = 100) String password,
            Boolean active,
            @NotEmpty Set<String> roles,
            List<Long> facilityIds,
            List<Long> ternaIds) {
    }
}
