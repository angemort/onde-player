package fr.ondes.player;

/** Pure scroll gates: collapse once while dragging, expand only when idle at top. */
public final class HeaderScrollRules {
    private HeaderScrollRules() {}
    public static boolean shouldCollapse(boolean alreadyCompact, boolean alreadyQueued, int firstVisible) {
        return !alreadyCompact && !alreadyQueued && firstVisible >= 2;
    }
    public static boolean shouldExpand(boolean compact, boolean idle, int firstVisible, int firstTop,
                                       int topTolerancePx) {
        return compact && idle && firstVisible == 0 && firstTop >= -topTolerancePx;
    }
    /** Compatibility with prior hysteresis tests. */
    public static boolean shouldCompact(boolean wasCompact, int firstVisible, int firstTop,
                                        int topTolerancePx, int secondRowThresholdPx) {
        if (wasCompact) return firstVisible != 0 || firstTop < -topTolerancePx;
        return firstVisible > 1 || (firstVisible == 1 && firstTop < -secondRowThresholdPx);
    }
}
