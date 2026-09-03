package com.riotanalizer.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QueueAdviceMathTest {

    @Test
    void shrinkPullsThinBucketsTowardOverall() {
        // 0 games -> exactly the overall win rate
        assertEquals(50.0, QueueAdviceMath.shrink(0, 90.0, 50.0, 5.0), 1e-9);
        // 5 games at 60% with k=5 toward 50% -> (5*60 + 5*50)/10 = 55
        assertEquals(55.0, QueueAdviceMath.shrink(5, 60.0, 50.0, 5.0), 1e-9);
    }

    @Test
    void weightedAverageBlendsBuckets() {
        double v = QueueAdviceMath.weightedAverage(
                new double[] { 55, 45, 50 }, new double[] { 0.5, 0.3, 0.2 });
        assertEquals(51.0, v, 1e-9); // 27.5 + 13.5 + 10
        assertEquals(0.0, QueueAdviceMath.weightedAverage(new double[] { 1 }, new double[] { 0 }), 1e-9);
    }

    @Test
    void nextSessionPositionContinuesOrResets() {
        long gap = 30 * 60 * 1000L;
        // 10 minutes after last game's end, was game 2 -> next is game 3
        assertEquals(3, QueueAdviceMath.nextSessionPosition(1_000_000L, 1_000_000L + 10 * 60_000L, 2, gap));
        // 2 hours later -> fresh session
        assertEquals(1, QueueAdviceMath.nextSessionPosition(1_000_000L, 1_000_000L + 120 * 60_000L, 2, gap));
        // no prior game
        assertEquals(1, QueueAdviceMath.nextSessionPosition(0, 0, 0, gap));
    }

    @Test
    void signalReflectsPredictionAndFlags() {
        assertEquals("GO", QueueAdviceMath.signal(55.0, 0, 1));
        assertEquals("CAUTION", QueueAdviceMath.signal(55.0, -2, 1)); // active losing streak
        assertEquals("CAUTION", QueueAdviceMath.signal(48.0, 0, 1));
        assertEquals("STOP", QueueAdviceMath.signal(40.0, 0, 1));
        assertEquals("STOP", QueueAdviceMath.signal(55.0, -3, 4)); // deep session + skid
    }
}
