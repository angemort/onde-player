package fr.ondes.player;

/** Pure playback-row state; row identity is the media-store ID, never its position. */
public final class PlayingTrackRules {
    public static final int OTHER = 0;
    public static final int PLAYING = 1;
    public static final int PAUSED = 2;
    private PlayingTrackRules() {}

    public static int state(long rowId, boolean hasCurrent, long currentId, boolean isPlaying) {
        if (!hasCurrent || rowId != currentId) return OTHER;
        return isPlaying ? PLAYING : PAUSED;
    }
}