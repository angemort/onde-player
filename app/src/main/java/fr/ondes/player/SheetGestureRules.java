package fr.ondes.player;

/** Pure decision logic for vertical bottom-sheet gestures. No Android dependencies. */
public final class SheetGestureRules {
    public static final int NONE = 0;
    public static final int COLLAPSE = 1;
    public static final int DISMISS = 2;
    public static final int EXPAND = 3;
    private SheetGestureRules() {}

    /** Positive distance/velocity = downward swipe; negative = upward swipe. */
    public static int resolve(boolean collapsed, float distancePx, float sheetHeightPx,
                              float unitPx, float velocityPxPerSecond) {
        float shortSwipe = Math.max(unitPx * 48f, 1f);
        if (collapsed) {
            if (distancePx >= shortSwipe || (distancePx > unitPx * 15f && velocityPxPerSecond > 900f))
                return DISMISS;
            if (distancePx <= -shortSwipe || (distancePx < -unitPx * 15f && velocityPxPerSecond < -900f))
                return EXPAND;
            return NONE;
        }
        if (distancePx >= Math.max(unitPx * 170f, sheetHeightPx * .46f)
                || (distancePx > shortSwipe && velocityPxPerSecond > 1900f)) return DISMISS;
        if (distancePx >= shortSwipe || (distancePx > unitPx * 20f && velocityPxPerSecond > 900f))
            return COLLAPSE;
        return NONE;
    }
}
