package fr.ondes.player;

/** Pure lifecycle policy: media playback must remain a foreground service
 * while the next title buffers or an interruption is known to be temporary. */
public final class PlaybackContinuityRules {
    private PlaybackContinuityRules() {}
    public static boolean keepForeground(boolean playing, boolean loading, boolean autoStart,
                                         boolean resumeAfterFocus, boolean restoringPlayback) {
        return playing || (loading && autoStart) || resumeAfterFocus || restoringPlayback;
    }
    public static boolean wantsPlayback(boolean playing, boolean loading, boolean autoStart,
                                        boolean resumeAfterFocus, boolean restoringPlayback) {
        return keepForeground(playing, loading, autoStart, resumeAfterFocus, restoringPlayback);
    }
}
