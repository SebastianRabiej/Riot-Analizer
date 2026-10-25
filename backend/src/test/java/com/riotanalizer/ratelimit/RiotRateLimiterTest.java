package com.riotanalizer.ratelimit;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RiotRateLimiterTest {

    @Test
    void freedPermitGoesToTheHighestPriorityWaiter() throws Exception {
        // 1 call per 150 ms: every waiter has to queue.
        RiotRateLimiter limiter = new RiotRateLimiter(1, 150, 1000, 60_000, 0, 0);
        limiter.acquire(FetchPriority.REFRESH); // use up the current permit

        List<FetchPriority> granted = Collections.synchronizedList(new ArrayList<>());
        List<Thread> threads = new ArrayList<>();
        // Queue them lowest priority first, so arrival order is the opposite of the expected grant order.
        FetchPriority[] arrival = {FetchPriority.DISCOVERED_HISTORY, FetchPriority.TRACKED_HISTORY,
                FetchPriority.ANALYSIS, FetchPriority.REFRESH};
        for (int i = 0; i < arrival.length; i++) {
            FetchPriority p = arrival[i];
            Thread t = new Thread(() -> {
                limiter.acquire(p);
                granted.add(p);
            });
            t.start();
            threads.add(t);
            waitUntil(() -> limiter.waitingAtOrAbove(FetchPriority.DISCOVERED_HISTORY) == threads.size());
        }
        for (Thread t : threads) {
            t.join(5_000);
        }

        assertEquals(List.of(FetchPriority.REFRESH, FetchPriority.ANALYSIS,
                FetchPriority.TRACKED_HISTORY, FetchPriority.DISCOVERED_HISTORY), granted);
    }

    @Test
    void samePriorityIsFirstComeFirstServed() throws Exception {
        RiotRateLimiter limiter = new RiotRateLimiter(1, 100, 1000, 60_000, 0, 0);
        limiter.acquire(FetchPriority.REFRESH);

        List<Integer> granted = Collections.synchronizedList(new ArrayList<>());
        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            int id = i;
            Thread t = new Thread(() -> {
                limiter.acquire(FetchPriority.TRACKED_HISTORY);
                granted.add(id);
            });
            t.start();
            threads.add(t);
            waitUntil(() -> limiter.waitingAtOrAbove(FetchPriority.DISCOVERED_HISTORY) == threads.size());
        }
        for (Thread t : threads) {
            t.join(5_000);
        }
        assertEquals(List.of(0, 1, 2), granted);
    }

    @Test
    void backgroundWorkLeavesTheReserveFreeForUsers() throws Exception {
        // Long window: 4 calls per minute; discovered work must leave 2 of them free.
        RiotRateLimiter limiter = new RiotRateLimiter(100, 1_000, 4, 60_000, 1, 2);
        limiter.acquire(FetchPriority.DISCOVERED_HISTORY);
        limiter.acquire(FetchPriority.DISCOVERED_HISTORY);

        CountDownLatch third = new CountDownLatch(1);
        Thread background = new Thread(() -> {
            try {
                limiter.acquire(FetchPriority.DISCOVERED_HISTORY);
                third.countDown();
            } catch (IllegalStateException interrupted) {
                // expected when the test ends
            }
        });
        background.setDaemon(true);
        background.start();
        assertFalse(third.await(300, TimeUnit.MILLISECONDS), "discovered work must not eat into the reserve");

        // A user request still gets a permit right away, even with background work queued.
        long started = System.currentTimeMillis();
        limiter.acquire(FetchPriority.REFRESH);
        assertTrue(System.currentTimeMillis() - started < 200, "user request should not wait");
        background.interrupt();
    }

    private static void waitUntil(java.util.function.BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5_000;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline) {
                throw new AssertionError("timed out waiting for condition");
            }
            Thread.sleep(5);
        }
    }
}
