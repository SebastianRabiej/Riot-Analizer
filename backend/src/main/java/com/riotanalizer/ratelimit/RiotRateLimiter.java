package com.riotanalizer.ratelimit;

import com.riotanalizer.config.RiotProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.Iterator;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Shared, application-wide, <em>priority-aware</em> rate limiter for every
 * outbound Riot call. Enforces two sliding windows (dev/personal key default:
 * 20 requests / 1 s and 100 requests / 120 s).
 *
 * <p>Callers queue up ordered by {@link FetchPriority} and then arrival; only
 * the head of the queue may take a permit, so whenever a permit frees up it
 * goes to the most important waiter: a user's refresh beats a match analysis,
 * which beats tracked-player history, which beats discovered-player history.
 *
 * <p>Background priorities additionally leave part of the long window free
 * (the "reserve"), so a user-facing request arriving while a backfill is
 * running usually gets its first permits immediately instead of waiting for
 * the two-minute window to roll over.
 */
@Component
public class RiotRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RiotRateLimiter.class);

    private final Window shortWindow;
    private final Window longWindow;
    /** Long-window permits each priority must leave unused, indexed by ordinal. */
    private final int[] longReserve = new int[FetchPriority.values().length];

    private final ReentrantLock lock = new ReentrantLock();
    private final Condition changed = lock.newCondition();
    private final TreeSet<Waiter> queue = new TreeSet<>(
            Comparator.comparingInt((Waiter w) -> w.priority.ordinal()).thenComparingLong(w -> w.seq));
    private long nextSeq;

    @Autowired
    public RiotRateLimiter(RiotProperties props) {
        this(props.getRateLimitShortRequests(), props.getRateLimitShortWindowMs(),
                props.getRateLimitLongRequests(), props.getRateLimitLongWindowMs(),
                props.getRateLimitTrackedReserve(), props.getRateLimitDiscoveredReserve());
    }

    public RiotRateLimiter(int shortLimit, long shortWindowMs, int longLimit, long longWindowMs,
                           int trackedReserve, int discoveredReserve) {
        this.shortWindow = new Window(Math.max(1, shortLimit), shortWindowMs);
        this.longWindow = new Window(Math.max(1, longLimit), longWindowMs);
        this.longReserve[FetchPriority.TRACKED_HISTORY.ordinal()] = clampReserve(trackedReserve, longLimit);
        // Discovered work always leaves at least as much room as tracked work.
        this.longReserve[FetchPriority.DISCOVERED_HISTORY.ordinal()] =
                Math.max(clampReserve(discoveredReserve, longLimit), longReserve[FetchPriority.TRACKED_HISTORY.ordinal()]);
    }

    private static int clampReserve(int reserve, int limit) {
        return Math.max(0, Math.min(reserve, Math.max(0, limit - 1)));
    }

    /** Acquire a permit at the current thread's {@link FetchContext} priority. */
    public void acquire() {
        acquire(FetchContext.current());
    }

    /**
     * Blocks until this caller is the highest-priority waiter and a permit is
     * free in both windows (minus the reserve for background priorities), then
     * records the call. Interruptible.
     */
    public void acquire(FetchPriority priority) {
        lock.lock();
        Waiter me = new Waiter(priority, nextSeq++);
        queue.add(me);
        // A new head (higher priority than whoever was first) must get a chance
        // to run now; everybody else just re-checks and goes back to waiting.
        changed.signalAll();
        try {
            while (true) {
                if (queue.first() == me) {
                    long now = System.currentTimeMillis();
                    int longLimit = longWindow.limit - longReserve[priority.ordinal()];
                    long wait = Math.max(shortWindow.millisUntilFree(now, shortWindow.limit),
                            longWindow.millisUntilFree(now, longLimit));
                    if (wait <= 0) {
                        shortWindow.record(now);
                        longWindow.record(now);
                        return;
                    }
                    log.debug("rate-limit: {} waiting {} ms for a permit ({} queued)", priority, wait, queue.size());
                    changed.await(wait, TimeUnit.MILLISECONDS);
                } else {
                    changed.await();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for a Riot rate-limit permit", e);
        } finally {
            queue.remove(me);
            changed.signalAll();
            lock.unlock();
        }
    }

    /** Number of callers currently waiting at {@code priority} or higher (for logs/tests). */
    public int waitingAtOrAbove(FetchPriority priority) {
        lock.lock();
        try {
            int n = 0;
            for (Waiter w : queue) {
                if (w.priority.ordinal() <= priority.ordinal()) {
                    n++;
                }
            }
            return n;
        } finally {
            lock.unlock();
        }
    }

    private record Waiter(FetchPriority priority, long seq) {
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

        /**
         * 0 if a call may proceed now while keeping at most {@code effectiveLimit}
         * calls in the window, otherwise the ms until enough old calls expire.
         */
        long millisUntilFree(long now, int effectiveLimit) {
            int allowed = Math.max(1, effectiveLimit);
            long cutoff = now - windowMs;
            while (!hits.isEmpty() && hits.peekFirst() <= cutoff) {
                hits.pollFirst();
            }
            if (hits.size() < allowed) {
                return 0L;
            }
            // The call at this index has to expire before the window is below `allowed`.
            int index = hits.size() - allowed;
            Iterator<Long> it = hits.iterator();
            long at = 0L;
            for (int i = 0; i <= index; i++) {
                at = it.next();
            }
            return Math.max(1L, at + windowMs - now);
        }

        void record(long now) {
            hits.addLast(now);
        }
    }
}
