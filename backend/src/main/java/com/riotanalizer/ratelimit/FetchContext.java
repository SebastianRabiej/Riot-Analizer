package com.riotanalizer.ratelimit;

import java.util.function.Supplier;

/**
 * Carries the {@link FetchPriority} of the current thread's work down to
 * {@link RiotRateLimiter}, so services deep in the call chain don't need a
 * priority parameter. Code that never sets one (HTTP request threads) runs at
 * {@link FetchPriority#REFRESH}: if nobody marked it as background work, a user
 * is probably waiting for it.
 */
public final class FetchContext {

    private static final ThreadLocal<FetchPriority> CURRENT = new ThreadLocal<>();

    private FetchContext() {
    }

    public static FetchPriority current() {
        FetchPriority p = CURRENT.get();
        return p == null ? FetchPriority.REFRESH : p;
    }

    /** Run {@code work} with {@code priority}, restoring the previous priority afterwards. */
    public static <T> T call(FetchPriority priority, Supplier<T> work) {
        FetchPriority previous = CURRENT.get();
        CURRENT.set(priority);
        try {
            return work.get();
        } finally {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }

    public static void run(FetchPriority priority, Runnable work) {
        call(priority, () -> {
            work.run();
            return null;
        });
    }
}
