import android.net.Uri;
import fr.ondes.player.Track;
import java.util.HashSet;
public final class TrackMediaTest {
    public static void main(String[] args) {
        HashSet<Long> keys = new HashSet<>();
        for (long id = 1; id <= 250000; id++) {
            if (!keys.add(Track.videoKey(id))) throw new AssertionError("Duplicate video key " + id);
            if (Track.videoKey(id) >= -1_000_000_000L) throw new AssertionError("Not in negative range");
            if (Track.videoKey(id) == -2 || Track.videoKey(id) == -1) throw new AssertionError("Reserved key");
        }
        Track music = new Track(37L, 8L, "", "", "", 66000, 1, 7, Uri.parse("content://audio/37"));
        Track video = new Track(Track.videoKey(37), Track.videoKey(0), "Vidéo", null, null, 123000, 2, 0, Uri.parse("content://video/37"), true);
        if (music.isVideo || !video.isVideo) throw new AssertionError("Wrong media type");
        if (!video.album.equals("Vidéos")) throw new AssertionError("Wrong video grouping");
        if (!Track.time(125000).equals("2:05")) throw new AssertionError("Wrong time formatter");
        System.out.println("TrackMediaTest OK: 250000 IDs, metadata, time");
    }
}
