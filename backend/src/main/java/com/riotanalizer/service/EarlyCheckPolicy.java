package com.riotanalizer.service;

import com.riotanalizer.config.RiotProperties;
import com.riotanalizer.domain.TrackingLevel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Rules for <em>early</em> checks: using spare Riot budget to look at players
 * before their regular {@code next_check_at}.
 *
 * <p>{@link CheckCadence} decides how often a player is worth a check when the
 * budget is scarce. When the budget is mostly idle, waiting 15 minutes for a
 * player who might have just finished a game wastes freshness for nothing, so
 * the scheduler pulls players forward — soonest-scheduled first — as long as:
 * <ul>
 *   <li>at least {@code minFreeRatio} of the long rate-limit window is unused
 *       (stricter for DISCOVERED than for TRACKED), and</li>
 *   <li>the player's own {@code early_check_at} has passed: at least
 *       {@code minGap} since their last check, and never during an error
 *       back-off.</li>
 * </ul>
 * As soon as other work fills the window, early checks stop by themselves and
 * only the regular cadence remains.
 */
@Component
public class EarlyCheckPolicy {

    private final boolean enabled;
    private final Duration trackedMinGap;
    private final Duration discoveredMinGap;
    private final double trackedMinFreeRatio;
    private final double discoveredMinFreeRatio;

    @Autowired
    public EarlyCheckPolicy(RiotProperties props) {
        this(props.isEarlyCheckEnabled(),
                Duration.ofMillis(props.getEarlyCheckTrackedMinGapMs()),
                Duration.ofMillis(props.getEarlyCheckDiscoveredMinGapMs()),
                props.getEarlyCheckTrackedMinFreeRatio(),
                props.getEarlyCheckDiscoveredMinFreeRatio());
    }

    public EarlyCheckPolicy(boolean enabled, Duration trackedMinGap, Duration discoveredMinGap,
                            double trackedMinFreeRatio, double discoveredMinFreeRatio) {
        this.enabled = enabled;
        this.trackedMinGap = nonNegative(trackedMinGap);
        this.discoveredMinGap = nonNegative(discoveredMinGap);
        this.trackedMinFreeRatio = clampRatio(trackedMinFreeRatio);
        // Discovered work never gets more of the budget than tracked work.
        this.discoveredMinFreeRatio = Math.max(clampRatio(discoveredMinFreeRatio), this.trackedMinFreeRatio);
    }

    public boolean enabled() {
        return enabled;
    }

    /** Minimum time between two checks of one player when checking early. */
    public Duration minGap(TrackingLevel level) {
        return level == TrackingLevel.TRACKED ? trackedMinGap : discoveredMinGap;
    }

    /** Long-window permits that must be unused before a player of {@code level} may be checked early. */
    public int minFree(TrackingLevel level, int longLimit) {
        double ratio = level == TrackingLevel.TRACKED ? trackedMinFreeRatio : discoveredMinFreeRatio;
        return Math.max(1, (int) Math.ceil(ratio * Math.max(1, longLimit)));
    }

    /** Whether the current long-window usage leaves room for an early check at {@code level}. */
    public boolean budgetAllows(TrackingLevel level, int longUsed, int longLimit) {
        return enabled && longLimit - longUsed >= minFree(level, longLimit);
    }

    /** {@code early_check_at} after a successful check that finished at {@code checkedAt}. */
    public Instant earlyCheckAt(TrackingLevel level, Instant checkedAt) {
        return checkedAt.plus(minGap(level));
    }

    private static Duration nonNegative(Duration d) {
        return d == null || d.isNegative() ? Duration.ZERO : d;
    }

    private static double clampRatio(double r) {
        return Double.isNaN(r) ? 1.0 : Math.max(0.0, Math.min(1.0, r));
    }
}
