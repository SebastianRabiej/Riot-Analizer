package com.riotanalizer.ratelimit;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RiotRateLimitObserverTest {

    @Test
    void parsesRiotAppRateLimitHeaders() {
        List<RiotRateLimitObserver.HeaderWindow> windows =
                RiotRateLimitObserver.parse("100:120,20:1", "47:120, 3:1");
        assertEquals(List.of(
                new RiotRateLimitObserver.HeaderWindow(1, 3, 20),
                new RiotRateLimitObserver.HeaderWindow(120, 47, 100)), windows);
    }

    @Test
    void missingOrMalformedHeadersAreIgnored() {
        assertTrue(RiotRateLimitObserver.parse(null, "3:1").isEmpty());
        assertEquals(List.of(new RiotRateLimitObserver.HeaderWindow(120, 0, 100)),
                RiotRateLimitObserver.parse("100:120,garbage", "x:y"));
    }
}
