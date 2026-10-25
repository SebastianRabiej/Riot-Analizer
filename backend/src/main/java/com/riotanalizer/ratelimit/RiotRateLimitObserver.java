package com.riotanalizer.ratelimit;

import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Riot's own view of our rate limits, read from the headers of every response
 * ({@code X-App-Rate-Limit: 20:1,100:120} and
 * {@code X-App-Rate-Limit-Count: 3:1,47:120}), plus a record of 429s.
 *
 * <p>{@link RiotRateLimiter} keeps its own count; this is the cross-check. The
 * two can differ when another process uses the same key or right after a
 * restart (the limiter starts empty, Riot does not).
 */
@Component
public class RiotRateLimitObserver {

    /** One of Riot's app-level windows as of the last response. */
    public record HeaderWindow(int windowSec, int count, int limit) {
    }

    /** Last 429 Riot sent: when, Retry-After, and X-Rate-Limit-Type (application / method / service). */
    public record RateLimited(Instant at, Integer retryAfterSec, String type) {
    }

    public record Snapshot(Instant observedAt, List<HeaderWindow> appWindows, long rateLimitedCount,
                           RateLimited lastRateLimited) {
    }

    private volatile Instant observedAt;
    private volatile List<HeaderWindow> appWindows = List.of();
    private volatile RateLimited lastRateLimited;
    private final AtomicLong rateLimitedCount = new AtomicLong();

    /** Called for every Riot response (see RestClientConfig). Never throws. */
    public void record(int status, HttpHeaders headers) {
        try {
            List<HeaderWindow> windows = parse(headers.getFirst("X-App-Rate-Limit"),
                    headers.getFirst("X-App-Rate-Limit-Count"));
            if (!windows.isEmpty()) {
                appWindows = windows;
                observedAt = Instant.now();
            }
            if (status == 429) {
                rateLimitedCount.incrementAndGet();
                lastRateLimited = new RateLimited(Instant.now(), intOrNull(headers.getFirst(HttpHeaders.RETRY_AFTER)),
                        headers.getFirst("X-Rate-Limit-Type"));
            }
        } catch (RuntimeException ignored) {
            // Observability only: a malformed header must never break a Riot call.
        }
    }

    public Optional<Snapshot> snapshot() {
        if (observedAt == null && lastRateLimited == null) {
            return Optional.empty();
        }
        return Optional.of(new Snapshot(observedAt, appWindows, rateLimitedCount.get(), lastRateLimited));
    }

    /** "20:1,100:120" + "3:1,47:120" → [(1s, 3/20), (120s, 47/100)]. */
    static List<HeaderWindow> parse(String limits, String counts) {
        if (limits == null || counts == null) {
            return List.of();
        }
        Map<Integer, Integer> limitBySec = pairs(limits);
        Map<Integer, Integer> countBySec = pairs(counts);
        List<HeaderWindow> out = new ArrayList<>();
        limitBySec.forEach((sec, limit) -> out.add(new HeaderWindow(sec, countBySec.getOrDefault(sec, 0), limit)));
        out.sort((a, b) -> Integer.compare(a.windowSec(), b.windowSec()));
        return List.copyOf(out);
    }

    /** "20:1,100:120" → {1: 20, 120: 100} (value:seconds pairs). */
    private static Map<Integer, Integer> pairs(String header) {
        Map<Integer, Integer> map = new LinkedHashMap<>();
        for (String part : header.split(",")) {
            String[] kv = part.trim().split(":");
            if (kv.length == 2) {
                Integer value = intOrNull(kv[0]);
                Integer sec = intOrNull(kv[1]);
                if (value != null && sec != null) {
                    map.put(sec, value);
                }
            }
        }
        return map;
    }

    private static Integer intOrNull(String s) {
        if (s == null) {
            return null;
        }
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
