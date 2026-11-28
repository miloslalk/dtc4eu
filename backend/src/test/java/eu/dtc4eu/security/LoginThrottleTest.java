package eu.dtc4eu.security;

import eu.dtc4eu.common.error.TooManyRequestsException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoginThrottleTest {

    private Instant now = Instant.parse("2026-11-28T10:00:00Z");
    private final LoginThrottle throttle = new LoginThrottle(new Clock() {
        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    });

    private void fail(String username, String address, int times) {
        for (int i = 0; i < times; i++) {
            throttle.check(username, address);
            throttle.recordFailure(username, address);
        }
    }

    @Test
    void anAccountIsThrottledPerAddressAfterRepeatedFailures() {
        fail("Admin", "203.0.113.7", LoginThrottle.MAX_PER_ACCOUNT);
        assertThatThrownBy(() -> throttle.check("admin", "203.0.113.7"))
                .isInstanceOf(TooManyRequestsException.class);
        // a stranger's failures do not lock the real user out from their own address
        assertThatCode(() -> throttle.check("admin", "198.51.100.1")).doesNotThrowAnyException();

        now = now.plus(LoginThrottle.WINDOW).plusSeconds(1);
        assertThatCode(() -> throttle.check("admin", "203.0.113.7")).doesNotThrowAnyException();
    }

    @Test
    void anAddressIsThrottledAcrossUsernames() {
        for (int i = 0; i < LoginThrottle.MAX_PER_ADDRESS; i++) {
            fail("user" + i, "203.0.113.9", 1);
        }
        assertThatThrownBy(() -> throttle.check("someone-else", "203.0.113.9"))
                .isInstanceOf(TooManyRequestsException.class);
    }

    @Test
    void aSuccessfulSignInClearsTheAccountsFailures() {
        fail("editor", "203.0.113.7", LoginThrottle.MAX_PER_ACCOUNT - 1);
        throttle.recordSuccess("editor", "203.0.113.7");
        fail("editor", "203.0.113.7", LoginThrottle.MAX_PER_ACCOUNT - 1);
        assertThatCode(() -> throttle.check("editor", "203.0.113.7")).doesNotThrowAnyException();
    }
}
