import fr.ondes.player.PlayingTrackRules;

public final class PlayingTrackRulesTest {
    private static void check(boolean ok,String label) {
        if(!ok) throw new AssertionError(label);
    }
    public static void main(String[] args) {
        check(PlayingTrackRules.state(7,false,7,false)==PlayingTrackRules.OTHER,"no session");
        check(PlayingTrackRules.state(7,true,8,true)==PlayingTrackRules.OTHER,"another track");
        check(PlayingTrackRules.state(7,true,7,true)==PlayingTrackRules.PLAYING,"playing");
        check(PlayingTrackRules.state(7,true,7,false)==PlayingTrackRules.PAUSED,"paused");
        check(PlayingTrackRules.state(8,true,7,false)==PlayingTrackRules.OTHER,"no stale row");
        check(PlayingTrackRules.state(Long.MIN_VALUE,false,Long.MIN_VALUE,true)
                ==PlayingTrackRules.OTHER,"no sentinel collision");
        System.out.println("PlayingTrackRules: OK");
    }
}