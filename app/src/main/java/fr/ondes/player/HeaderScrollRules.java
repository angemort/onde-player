package fr.ondes.player;

/** Pure hysteresis logic; only called after the list becomes idle. */
public final class HeaderScrollRules {
    private HeaderScrollRules() {}
    public static boolean shouldCompact(boolean wasCompact, int firstVisible, int firstTop,
                                        int topTolerancePx, int secondRowThresholdPx) {
        if (wasCompact) return firstVisible != 0 || firstTop < -topTolerancePx;
        return firstVisible > 1 || (firstVisible == 1 && firstTop < -secondRowThresholdPx);
    }
}
