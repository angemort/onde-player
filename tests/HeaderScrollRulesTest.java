import fr.ondes.player.HeaderScrollRules;
public final class HeaderScrollRulesTest {
    private static void check(boolean truth) { if (!truth) throw new AssertionError("Header jitter hysteresis"); }
    public static void main(String[] args) {
        check(!HeaderScrollRules.shouldCompact(false,0,0,4,36));
        check(!HeaderScrollRules.shouldCompact(false,1,-15,4,36));
        check(HeaderScrollRules.shouldCompact(false,2,0,4,36));
        check(HeaderScrollRules.shouldCompact(false,1,-60,4,36));
        check(HeaderScrollRules.shouldCompact(true,1,0,4,36));
        check(HeaderScrollRules.shouldCompact(true,0,-5,4,36));
        check(!HeaderScrollRules.shouldCompact(true,0,0,4,36));
        System.out.println("HeaderScrollRules: OK");
    }
}
