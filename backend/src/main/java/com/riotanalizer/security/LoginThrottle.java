package com.riotanalizer.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;

/**
 * Brute-force guard for the single admin account: once {@code maxFailedLogins}
 * failures happen inside a {@code lockoutMinutes} sliding window, every login
 * attempt is refused (HTTP 429) until the oldest failure ages out. A successful
 * login clears the counter.
 *
 * <p>Deliberately global rather than per-IP: the app sits behind proxies where
 * client IPs are spoofable via X-Forwarded-For, and there is only one account.
 */
@Component
public class LoginThrottle {

    private final int maxFailures;
    private final Duration window;
    private final Clock clock;
    private final Deque<Instant> failures = new ArrayDeque<>();

    @Autowired
    public LoginThrottle(AdminProperties props) {
        this(props.maxFailedLogins(), Duration.ofMinutes(props.lockoutMinutes()), Clock.systemUTC());
    }

    LoginThrottle(int maxFailures, Duration window, Clock clock) {
        this.maxFailures = maxFailures;
        this.window = window;
        this.clock = clock;
    }

    /**
     * Atomically checks the window and, if allowed, counts this attempt as a
     * failure up front (cleared again by {@link #recordSuccess()}), so parallel
     * requests cannot race past the limit.
     *
     * @return empty when the attempt may proceed, otherwise how long to wait
     */
    public synchronized Optional<Duration> tryAttempt() {
        Instant now = clock.instant();
        prune(now);
        if (failures.size() >= maxFailures) {
            Duration wait = Duration.between(now, failures.peekFirst().plus(window));
            return Optional.of(wait.isNegative() ? Duration.ZERO : wait);
        }
        failures.addLast(now);
        return Optional.empty();
    }

    public synchronized void recordSuccess() {
        failures.clear();
    }

    private void prune(Instant now) {
        Instant cutoff = now.minus(window);
        while (!failures.isEmpty() && !failures.peekFirst().isAfter(cutoff)) {
            failures.pollFirst();
        }
    }
}
