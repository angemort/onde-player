import fr.ondes.player.SheetGestureRules;

/** Runnable with plain JDK 17: no Android SDK necessary. */
public final class SheetGestureRulesTest {
    private static int count;
    private static void check(String label, int actual, int expected) {
        if (actual != expected) throw new AssertionError(label + " expected=" + expected + " actual=" + actual);
        count++;
    }
    public static void main(String[] args) {
        final float density = 2f;
        final float height = 1400f;
        check("a tap does nothing", SheetGestureRules.resolve(false, 0f, height, density, 0f), SheetGestureRules.NONE);
        check("short nudge does nothing", SheetGestureRules.resolve(false, 55f, height, density, 200f), SheetGestureRules.NONE);
        check("up drag expanded belongs to scroll", SheetGestureRules.resolve(false, -220f, height, density, -1200f), SheetGestureRules.NONE);
        check("moderate pull collapses", SheetGestureRules.resolve(false, 180f, height, density, 400f), SheetGestureRules.COLLAPSE);
        check("long pull dismisses", SheetGestureRules.resolve(false, 670f, height, density, 650f), SheetGestureRules.DISMISS);
        check("fast pull dismisses", SheetGestureRules.resolve(false, 150f, height, density, 2400f), SheetGestureRules.DISMISS);
        check("fast short pull collapses", SheetGestureRules.resolve(false, 60f, height, density, 1100f), SheetGestureRules.COLLAPSE);
        check("collapsed small movement stays", SheetGestureRules.resolve(true, 25f, 210f, density, 280f), SheetGestureRules.NONE);
        check("collapsed downward closes", SheetGestureRules.resolve(true, 120f, 210f, density, 350f), SheetGestureRules.DISMISS);
        check("collapsed upward expands", SheetGestureRules.resolve(true, -110f, 210f, density, -400f), SheetGestureRules.EXPAND);
        check("collapsed short fast downward closes", SheetGestureRules.resolve(true, 35f, 210f, density, 1200f), SheetGestureRules.DISMISS);
        System.out.println(count + " bottom-sheet gesture checks passed");
    }
}
