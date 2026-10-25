package com.riotanalizer.service;

import com.riotanalizer.domain.Player;
import com.riotanalizer.domain.TrackingLevel;

/**
 * Pure rules for which players discover new players, and at what depth.
 *
 * <p>Depth counts hops from an explicitly TRACKED player (depth 0). With
 * {@code maxDepth = 1} only the people met in tracked players' matches are
 * discovered; their own matches discover nobody. Raising it (or setting it to
 * -1, unbounded) needs no other change: the scheduler already serves lower
 * depths first, so discovery spreads breadth-first from the tracked players.
 */
public record DiscoveryPolicy(int maxDepth, int maxPlayers) {

    public static final int UNBOUNDED = -1;

    /**
     * Depth to give players met in {@code source}'s match, or -1 if that match
     * must not discover anyone (source not tracked/discovered, or too deep).
     */
    public int childDepth(Player source) {
        if (source == null) {
            return -1;
        }
        TrackingLevel level = source.getTrackingLevel();
        int sourceDepth;
        if (level == TrackingLevel.TRACKED) {
            sourceDepth = 0;
        } else if (level == TrackingLevel.DISCOVERED && source.getDiscoveryDepth() != null) {
            sourceDepth = source.getDiscoveryDepth();
        } else {
            return -1;
        }
        int child = sourceDepth + 1;
        return allowsDepth(child) ? child : -1;
    }

    public boolean allowsDepth(int depth) {
        if (maxDepth == 0) {
            return false;
        }
        return maxDepth < 0 || depth <= maxDepth;
    }

    /** Whether one more DISCOVERED player may be added given how many already exist. */
    public boolean hasRoomFor(long discoveredCount) {
        return maxPlayers <= 0 || discoveredCount < maxPlayers;
    }

    /**
     * Apply discovery to a participant's existing player row (or a fresh one).
     * Returns true if the row changed and must be saved. TRACKED rows are never
     * touched; NONE rows are promoted; DISCOVERED rows only ever get shallower.
     */
    public static boolean applyTo(Player participant, int depth) {
        switch (participant.getTrackingLevel()) {
            case TRACKED:
                return false;
            case DISCOVERED:
                Integer current = participant.getDiscoveryDepth();
                if (current != null && current <= depth) {
                    return false;
                }
                participant.setDiscoveryDepth(depth);
                return true;
            case NONE:
            default:
                participant.setTrackingLevel(TrackingLevel.DISCOVERED);
                participant.setDiscoveryDepth(depth);
                participant.setNextCheckAt(null);
                return true;
        }
    }
}
