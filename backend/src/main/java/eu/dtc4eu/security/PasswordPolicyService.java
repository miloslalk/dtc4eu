package eu.dtc4eu.security;

import eu.dtc4eu.audit.AuditService;
import eu.dtc4eu.common.error.BadRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Self-service password change and the "must change password" gate (FIX-LIST X4).
 * Plain SQL on {@code users} keeps the security slice from adding Java edges to
 * the user slice (the security↔user cycle is frozen in the ArchUnit store).
 */
@Service
@RequiredArgsConstructor
public class PasswordPolicyService {

    /** The development seed (V2); refused as a new password everywhere. */
    static final String SEED_ADMIN_PASSWORD = "Dtc4eu!Admin2026";

    private final NamedParameterJdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    @Transactional
    public void changePassword(AppUserDetails principal, String currentPassword, String newPassword) {
        String hash = jdbc.queryForObject("select password_hash from users where id = :id",
                new MapSqlParameterSource("id", principal.getId()), String.class);
        if (hash == null || !passwordEncoder.matches(currentPassword, hash)) {
            auditService.failure(principal.getId(), "CHANGE_PASSWORD", "user", principal.getId(),
                    java.util.Map.of("reason", "wrong current password"));
            throw new BadRequestException("The current password is not correct");
        }
        if (newPassword.equals(currentPassword)) {
            throw new BadRequestException("The new password must differ from the current one");
        }
        if (newPassword.equals(SEED_ADMIN_PASSWORD) || newPassword.toLowerCase().contains(principal.getUsername()
                .toLowerCase())) {
            throw new BadRequestException("Choose a password that is not published and does not contain your username");
        }
        jdbc.update("update users set password_hash = :hash, must_change_password = false where id = :id",
                new MapSqlParameterSource("hash", passwordEncoder.encode(newPassword))
                        .addValue("id", principal.getId()));
        auditService.success(principal.getId(), "CHANGE_PASSWORD", "user", principal.getId());
    }

    /** Flags the seeded admin while its published seed password still works; true if flagged. */
    @Transactional
    public boolean flagSeedAdminIfUnrotated() {
        List<String> hashes = jdbc.queryForList(
                "select password_hash from users where username = 'admin' and not must_change_password",
                new MapSqlParameterSource(), String.class);
        if (hashes.isEmpty() || !passwordEncoder.matches(SEED_ADMIN_PASSWORD, hashes.get(0))) {
            return false;
        }
        jdbc.update("update users set must_change_password = true where username = 'admin'",
                new MapSqlParameterSource());
        return true;
    }

    /**
     * While the seeded admin still accepts the published seed password, replaces it
     * with the deployment's bootstrap password and requires a change at next login,
     * so nobody who read the repository can claim the account first. Refuses (throws)
     * when no usable bootstrap password is configured. True if replaced.
     */
    @Transactional
    public boolean replaceSeedAdminPasswordIfUnrotated(String bootstrapPassword) {
        List<String> hashes = jdbc.queryForList("select password_hash from users where username = 'admin'",
                new MapSqlParameterSource(), String.class);
        if (hashes.isEmpty() || !passwordEncoder.matches(SEED_ADMIN_PASSWORD, hashes.get(0))) {
            return false;
        }
        if (bootstrapPassword == null || bootstrapPassword.isBlank()
                || bootstrapPassword.equals(SEED_ADMIN_PASSWORD)) {
            throw new IllegalStateException("The seeded admin still uses the published password: set "
                    + "SEED_ADMIN_PASSWORD to a private bootstrap password (see deploy/DEPLOY.md)");
        }
        jdbc.update("update users set password_hash = :hash, must_change_password = true where username = 'admin'",
                new MapSqlParameterSource("hash", passwordEncoder.encode(bootstrapPassword)));
        return true;
    }
}
