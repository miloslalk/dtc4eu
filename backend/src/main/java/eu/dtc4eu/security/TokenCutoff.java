package eu.dtc4eu.security;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Date;

/**
 * Per-user cut-off for the stateless refresh tokens (V19 {@code users.tokens_valid_after}):
 * a password change moves it to "now", so refresh tokens issued before it are refused
 * and every other session ends when its short-lived access token expires (JWT_ACCESS_TTL,
 * 15 min). JWT {@code iat} has whole-second precision, so the cut-off is truncated to the
 * second and compared strictly.
 */
public final class TokenCutoff {

    private TokenCutoff() {
    }

    /** The cut-off value to store when a password changes. */
    public static OffsetDateTime now() {
        return OffsetDateTime.now().truncatedTo(ChronoUnit.SECONDS);
    }

    public static boolean accepts(OffsetDateTime validAfter, Date issuedAt) {
        if (validAfter == null) {
            return true;
        }
        return issuedAt != null && !issuedAt.toInstant().isBefore(validAfter.toInstant());
    }
}
