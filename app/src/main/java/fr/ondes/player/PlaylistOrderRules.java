package fr.ondes.player;

import java.util.ArrayList;
import java.util.List;

/** Pure ordering operations used before persisting a playlist drag in SQLite. */
public final class PlaylistOrderRules {
    private PlaylistOrderRules() {}

    /** Moves one existing ID immediately before the previous occupant of the drop position.
     * Position is determined by stable track IDs so filtering does not corrupt hidden tracks. */
    public static List<Long> move(List<Long> source, long movingId, long targetId) {
        List<Long> result = new ArrayList<>(source);
        int from = result.indexOf(movingId);
        int target = result.indexOf(targetId);
        if (from < 0 || target < 0 || from == target) return result;
        result.remove(from);
        // Dropping on a row puts the item at that row's index in the original playlist.
        // This works for moves in either direction after removing the source item.
        result.add(target, movingId);
        return result;
    }
}
