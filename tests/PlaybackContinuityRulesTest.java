import fr.ondes.player.PlaybackContinuityRules;
public final class PlaybackContinuityRulesTest {
    private static void assertCondition(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    private static boolean keep(boolean p,boolean l,boolean a,boolean f,boolean r) {
        return PlaybackContinuityRules.keepForeground(p,l,a,f,r);
    }
    public static void main(String[] args) {
        assertCondition(keep(true,false,false,false,false),"playing protected");
        assertCondition(keep(false,true,true,false,false),"next track loading protected");
        assertCondition(keep(false,false,false,true,false),"temporary focus loss protected");
        assertCondition(keep(false,true,false,false,true),"process restoration protected");
        assertCondition(!keep(false,true,false,false,false),"manual seek/paused load not protected");
        assertCondition(!keep(false,false,false,false,false),"explicit pause releases foreground");
        System.out.println("PlaybackContinuityRules: OK");
    }
}
