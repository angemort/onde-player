package fr.ondes.player;

/** Deterministic commitment rule, independent from Android's touch recognizers. */
public final class GestureRules {
    private GestureRules() {}
    public static boolean shouldCommit(float translation, float distanceToEdge) {
        return distanceToEdge > 0 && Math.abs(translation) >= distanceToEdge * .96f;
    }
}
