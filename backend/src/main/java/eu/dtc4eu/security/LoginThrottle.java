package eu.dtc4eu.security;

import eu.dtc4eu.common.error.TooManyRequestsException;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Deque;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Slows password guessing on the login endpoint: after {@value #MAX_PER_ACCOUNT}
 * failures for one username from one address, or {@value #MAX_PER_ADDRESS} from
 * one address across usernames, within {@link #WINDOW}, further attempts get 429
 * until the oldest failure ages out. Keyed by address as well as username so a
 * stranger cannot lock a real user out. In-memory: one API instance per deployment,
 * and a restart only resets the counters. The address comes from Caddy's
 * X-Forwarded-For (server.forward-headers-strategy=native in the prod profile).
 */
@Component
public class LoginThrottle {

    static final int MAX_PER_ACCOUNT = 5;
    static final int MAX_PER_ADDRESS = 30;
    static final Duration WINDOW = Duration.ofMinutes(15);
    private static final int PRUNE_ABOVE = 10_000;

    private final Map<String, Deque<Instant>> failures = new ConcurrentHashMap<>();
    private final Clock clock;

    public LoginThrottle() {
        this(Clock.systemUTC());
    }

    LoginThrottle(Clock clock) {
        this.clock = clock;
    }

    /** Throws 429 while the username or the address is over its limit. */
    public void check(String username, String address) {
        Instant since = clock.instant().minus(WINDOW);
        if (recent(accountKey(username, address), since) >= MAX_PER_ACCOUNT
                || recent(addressKey(address), since) >= MAX_PER_ADDRESS) {
            throw new TooManyRequestsException("Too many failed sign-in attempts. Try again in "
                    + WINDOW.toMinutes() + " minutes.");
        }
    }

    public void recordFailure(String username, String address) {
        Instant now = clock.instant();
        if (failures.size() > PRUNE_ABOVE) {
            Instant since = now.minus(WINDOW);
            failures.values().removeIf(times -> times.isEmpty() || times.peekLast().isBefore(since));
        }
        failures.computeIfAbsent(accountKey(username, address), k -> new ConcurrentLinkedDeque<>()).addLast(now);
        failures.computeIfAbsent(addressKey(address), k -> new ConcurrentLinkedDeque<>()).addLast(now);
    }

    /** A successful sign-in clears that account's failures (the address count keeps ageing out). */
    public void recordSuccess(String username, String address) {
        failures.remove(accountKey(username, address));
    }

    private int recent(String key, Instant since) {
        Deque<Instant> times = failures.get(key);
        if (times == null) {
            return 0;
        }
        while (!times.isEmpty() && times.peekFirst().isBefore(since)) {
            times.pollFirst();
        }
        return times.size();
    }

    private static String accountKey(String username, String address) {
        return "u:" + (username == null ? "" : username.toLowerCase(Locale.ROOT)) + "|" + address;
    }

    private static String addressKey(String address) {
        return "a:" + address;
    }
}
