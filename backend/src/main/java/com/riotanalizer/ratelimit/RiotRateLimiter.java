package com.riotanalizer.ratelimit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Shared, application-wide rate limiter enforcing the Riot dev-key limits:
 * 20 requests / 1 second and 100 requests / 120 seconds, applied across ALL
 * outbound Riot HTTP calls. A permit must be acquired from BOTH sliding
 * windows before any request is made.
 *
 * <p>Implemented with the JDK only (two sliding-window request logs) to avoid
 * an external dependency. {@link #acquire()} is {@code synchronized}, so it
 * both serialises access to the windows and naturally paces concurrent callers.
 */
@Component
public class RiotRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RiotRateLimiter.class);

    private final Window shortWindow = new Window(20, 1_000L);
    private final Window longWindow = new Window(100, 120_000L);

    /**
     * Blocks until a permit is available in both windows, then records the call
     * against each. Interruptible.
     */
    public synchronized void acquire() {
        while (true) {
            long now = System.currentTimeMillis();
            long wait = Math.max(shortWindow.millisUntilFree(now), longWindow.millisUntilFree(now));
            if (wait <= 0) {
                shortWindow.record(now);
                longWindow.record(now);
                return;
            }
            log.debug("rate-limit: waiting {} ms for a permit (20/s & 100/2min)", wait);
            try {
                Thread.sleep(wait);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for a Riot rate-limit permit", e);
            }
        }
    }

    /** A single sliding window: at most {@code limit} calls per {@code windowMs}. */
    private static final class Window {
        private final int limit;
        private final long windowMs;
        private final Deque<Long> hits = new ArrayDeque<>();

        Window(int limit, long windowMs) {
            this.limit = limit;
            this.windowMs = windowMs;
        }

        /** 0 if a call may proceed now, otherwise the ms to wait for a slot to free up. */
        long millisUntilFree(long now) {
            long cutoff = now - windowMs;
            while (!hits.isEmpty() && hits.peekFirst() <= cutoff) {
                hits.pollFirst();
            }
            if (hits.size() < limit) {
                return 0L;
            }
            long oldest = hits.peekFirst();
            return Math.max(1L, oldest + windowMs - now);
        }

        void record(long now) {
            hits.addLast(now);
        }
    }
}
