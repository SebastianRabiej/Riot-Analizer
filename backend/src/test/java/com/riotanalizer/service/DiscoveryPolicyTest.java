package com.riotanalizer.service;

import com.riotanalizer.domain.Player;
import com.riotanalizer.domain.TrackingLevel;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiscoveryPolicyTest {

    static Player player(TrackingLevel level, Integer depth) {
        Player p = new Player();
        p.setPuuid("p-" + level + "-" + depth);
        p.setTrackingLevel(level);
        p.setDiscoveryDepth(depth);
        return p;
    }

    @Test
    void depthOneOnlyDiscoversFromTrackedPlayers() {
        DiscoveryPolicy policy = new DiscoveryPolicy(1, 0);
        assertEquals(1, policy.childDepth(player(TrackingLevel.TRACKED, 0)));
        assertEquals(-1, policy.childDepth(player(TrackingLevel.DISCOVERED, 1)));
        assertEquals(-1, policy.childDepth(player(TrackingLevel.NONE, null)));
    }

    @Test
    void unboundedKeepsSpreading() {
        DiscoveryPolicy policy = new DiscoveryPolicy(DiscoveryPolicy.UNBOUNDED, 0);
        assertEquals(2, policy.childDepth(player(TrackingLevel.DISCOVERED, 1)));
        assertEquals(8, policy.childDepth(player(TrackingLevel.DISCOVERED, 7)));
        assertEquals(-1, policy.childDepth(player(TrackingLevel.NONE, null)));
    }

    @Test
    void depthZeroDisablesDiscovery() {
        assertEquals(-1, new DiscoveryPolicy(0, 0).childDepth(player(TrackingLevel.TRACKED, 0)));
    }

    @Test
    void playerCapIsRespected() {
        assertTrue(new DiscoveryPolicy(1, 0).hasRoomFor(1_000_000));
        assertTrue(new DiscoveryPolicy(1, 10).hasRoomFor(9));
        assertFalse(new DiscoveryPolicy(1, 10).hasRoomFor(10));
    }

    @Test
    void applyPromotesNoneAndOnlyMovesDiscoveredCloser() {
        Player none = player(TrackingLevel.NONE, null);
        none.setNextCheckAt(Instant.now());
        assertTrue(DiscoveryPolicy.applyTo(none, 1));
        assertEquals(TrackingLevel.DISCOVERED, none.getTrackingLevel());
        assertEquals(1, none.getDiscoveryDepth());
        assertNull(none.getNextCheckAt());

        Player far = player(TrackingLevel.DISCOVERED, 3);
        assertTrue(DiscoveryPolicy.applyTo(far, 2));
        assertEquals(2, far.getDiscoveryDepth());
        assertFalse(DiscoveryPolicy.applyTo(far, 2));
        assertFalse(DiscoveryPolicy.applyTo(far, 5));

        Player tracked = player(TrackingLevel.TRACKED, 0);
        assertFalse(DiscoveryPolicy.applyTo(tracked, 1));
        assertEquals(TrackingLevel.TRACKED, tracked.getTrackingLevel());
    }

    @Test
    void trackingAndUntrackingSetLevelAndDepth() {
        Player p = player(TrackingLevel.DISCOVERED, 1);
        p.setNextCheckAt(Instant.now());
        p.setTracked(true);
        assertEquals(TrackingLevel.TRACKED, p.getTrackingLevel());
        assertEquals(0, p.getDiscoveryDepth());
        assertNull(p.getNextCheckAt(), "newly tracked players are due immediately");

        p.setTracked(false);
        assertEquals(TrackingLevel.NONE, p.getTrackingLevel());
        assertNull(p.getDiscoveryDepth());
    }
}
