import fr.ondes.player.GestureRules;
public final class GestureRulesTest {
    public static void main(String[] args) {
        check(!GestureRules.shouldCommit(0, 300), "tap must not commit");
        check(!GestureRules.shouldCommit(140, 300), "short right swipe must cancel");
        check(!GestureRules.shouldCommit(-220, 300), "short left swipe must cancel");
        check(!GestureRules.shouldCommit(0, 300), "return to center must cancel");
        check(GestureRules.shouldCommit(290, 300), "near right edge must commit");
        check(GestureRules.shouldCommit(-290, 300), "near left edge must commit");
        check(!GestureRules.shouldCommit(0, 0), "zero-size row must cancel");
        System.out.println("7 gesture regression checks OK");
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
