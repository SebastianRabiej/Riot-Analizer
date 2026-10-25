package com.riotanalizer.security;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoginThrottleTest {

    /** Minimal mutable clock for stepping time forward. */
    private static final class TestClock extends Clock {
        Instant now = Instant.parse("2026-10-25T12:00:00Z");

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    @Test
    void blocksAfterMaxFailuresUntilWindowPasses() {
        TestClock clock = new TestClock();
        LoginThrottle throttle = new LoginThrottle(3, Duration.ofMinutes(15), clock);

        for (int i = 0; i < 3; i++) {
            assertTrue(throttle.tryAttempt().isEmpty(), "attempt " + i + " allowed");
            clock.now = clock.now.plusSeconds(60);
        }
        // 4th attempt: blocked until the first failure (t=0) is 15 min old -> 12 min left.
        assertEquals(Duration.ofMinutes(12), throttle.tryAttempt().orElseThrow());

        clock.now = clock.now.plus(Duration.ofMinutes(12));
        assertTrue(throttle.tryAttempt().isEmpty(), "oldest failure aged out");
    }

    @Test
    void successClearsCounter() {
        TestClock clock = new TestClock();
        LoginThrottle throttle = new LoginThrottle(2, Duration.ofMinutes(15), clock);

        throttle.tryAttempt();
        throttle.tryAttempt();
        assertTrue(throttle.tryAttempt().isPresent());

        throttle.recordSuccess();
        assertTrue(throttle.tryAttempt().isEmpty());
    }
}
