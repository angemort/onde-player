package fr.ondes.player;

/** Direction and hysteresis for Android header motions; independent of the UI. */
public final class HeaderMotionRules {
    public static final int NONE = -1;
    public static final int SHOW = 0;
    public static final int HIDE = 1;
    private HeaderMotionRules() {}

    public static float accumulate(float current, float delta, float cap) {
        // On a real reversal abandon the previous direction; tiny finger
        // fluctuations must not repeatedly toggle the chrome.
        if ((current > 0 && delta < 0) || (current < 0 && delta > 0)) {
            if (Math.abs(delta) > 2f) current = 0f;
        }
        return Math.max(-cap, Math.min(cap, current + delta));
    }
    public static int intent(float gestureDistance, float threshold) {
        if (gestureDistance >= threshold) return SHOW;
        if (gestureDistance <= -threshold) return HIDE;
        return NONE;
    }
}
