package eu.dtc4eu.security;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Reads the "must change password" flag (V16, FIX-LIST X4). Kept apart from
 * {@link PasswordPolicyService} so the JWT filter does not depend on the
 * password encoder that SecurityConfig defines (bean cycle).
 */
@Component
@RequiredArgsConstructor
public class PasswordChangeGate {

    private final NamedParameterJdbcTemplate jdbc;

    public boolean mustChangePassword(Long userId) {
        List<Boolean> flags = jdbc.queryForList("select must_change_password from users where id = :id",
                new MapSqlParameterSource("id", userId), Boolean.class);
        return !flags.isEmpty() && Boolean.TRUE.equals(flags.get(0));
    }
}
