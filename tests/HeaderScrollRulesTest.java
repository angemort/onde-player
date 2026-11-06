import fr.ondes.player.HeaderScrollRules;
public final class HeaderScrollRulesTest {
    private static void check(boolean truth) { if (!truth) throw new AssertionError("Header scroll jitter"); }
    public static void main(String[] args) {
        check(!HeaderScrollRules.shouldCompact(false,0,0,4,36));
        check(!HeaderScrollRules.shouldCompact(false,1,-15,4,36));
        check(HeaderScrollRules.shouldCompact(false,2,0,4,36));
        check(HeaderScrollRules.shouldCompact(false,1,-60,4,36));
        check(HeaderScrollRules.shouldCompact(true,1,0,4,36));
        check(HeaderScrollRules.shouldCompact(true,0,-5,4,36));
        check(!HeaderScrollRules.shouldCompact(true,0,0,4,36));

        // The chrome disappears with the second row, not after finger release.
        check(!HeaderScrollRules.shouldCollapse(false,false,0));
        check(!HeaderScrollRules.shouldCollapse(false,false,1));
        check(HeaderScrollRules.shouldCollapse(false,false,2));
        check(!HeaderScrollRules.shouldCollapse(true,false,4));
        check(!HeaderScrollRules.shouldCollapse(false,true,4));
        // Holding a finger at the top must never trigger re-expansion.
        check(!HeaderScrollRules.shouldExpand(true,false,0,0,5));
        check(!HeaderScrollRules.shouldExpand(true,true,1,0,5));
        check(!HeaderScrollRules.shouldExpand(true,true,0,-20,5));
        check(HeaderScrollRules.shouldExpand(true,true,0,-2,5));
        check(!HeaderScrollRules.shouldExpand(false,true,0,0,5));
        System.out.println("HeaderScrollRules: OK");
    }
}
