package eu.dtc4eu.security;

import eu.dtc4eu.config.AppProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Production refuses to start with a placeholder JWT signing secret (the
 * {@code change-me-…} value of deploy/.env.example or the local dev default):
 * anyone who has read the repository could otherwise sign their own tokens.
 */
@Component
@Profile("prod")
public class JwtSecretGuard {

    public JwtSecretGuard(AppProperties appProperties) {
        requireUsable(appProperties.jwt().secret());
    }

    static void requireUsable(String secret) {
        if (secret == null || secret.toLowerCase(Locale.ROOT).contains("change-me")) {
            throw new IllegalStateException("JWT_SECRET is a published placeholder: set a random value "
                    + "(openssl rand -base64 48) in deploy/.env");
        }
    }
}
