package com.riotanalizer.service;

import org.springframework.stereotype.Component;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Closed until {@link PuuidMigrationRunner} has finished its startup check, so
 * scheduled jobs don't hit Riot with PUUIDs that are about to be re-keyed.
 */
@Component
public class PuuidMigrationGate {

    private static final long MAX_WAIT_MINUTES = 10;

    private final CountDownLatch latch = new CountDownLatch(1);

    public void open() {
        latch.countDown();
    }

    public boolean isOpen() {
        return latch.getCount() == 0;
    }

    /**
     * Block until the startup check is done (or a safety timeout passes).
     * Returns {@code false} only if the thread was interrupted.
     */
    public boolean awaitOpen() {
        try {
            latch.await(MAX_WAIT_MINUTES, TimeUnit.MINUTES);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
