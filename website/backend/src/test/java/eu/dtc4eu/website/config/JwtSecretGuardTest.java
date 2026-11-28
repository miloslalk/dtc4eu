package eu.dtc4eu.website.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtSecretGuardTest {

    @Test
    void placeholderSecretsAreRefused() {
        for (String placeholder : new String[] {"change-me-openssl-rand-base64-48",
                "dtc4eu-local-dev-secret-key-CHANGE-ME-0123456789", null}) {
            assertThatThrownBy(() -> JwtSecretGuard.requireUsable(placeholder))
                    .isInstanceOf(IllegalStateException.class);
        }
        assertThatCode(() -> JwtSecretGuard.requireUsable("a-value-generated-on-the-server-with-openssl"))
                .doesNotThrowAnyException();
    }
}
