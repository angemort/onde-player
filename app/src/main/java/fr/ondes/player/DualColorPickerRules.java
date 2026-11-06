package fr.ondes.player;

/** Pure nearest-handle selection for the dual-color gradient picker. */
public final class DualColorPickerRules {
    private DualColorPickerRules() {}
    public static int choose(int active, float firstDistanceSquared, float secondDistanceSquared,
                             float hitRadiusSquared, float tieToleranceSquared) {
        int current = active == 1 ? 1 : 0;
        if (firstDistanceSquared > hitRadiusSquared && secondDistanceSquared > hitRadiusSquared)
            return current;
        if (Math.abs(firstDistanceSquared-secondDistanceSquared) <= tieToleranceSquared)
            return current;
        return firstDistanceSquared < secondDistanceSquared ? 0 : 1;
    }
}
