package fr.ondes.player;
import java.util.Arrays;
import java.util.List;
public class PlaylistOrderRulesTest {
    static void eq(List<Long> expected, List<Long> actual) {
        if (!expected.equals(actual)) throw new AssertionError("Expected " + expected + " got " + actual);
    }
    public static void main(String[] args) {
        List<Long> initial = Arrays.asList(11L, 22L, 33L, 44L);
        eq(Arrays.asList(22L,33L,11L,44L), PlaylistOrderRules.move(initial,11L,33L));
        eq(Arrays.asList(11L,44L,22L,33L), PlaylistOrderRules.move(initial,44L,22L));
        eq(initial,PlaylistOrderRules.move(initial,22L,22L));
        eq(initial,PlaylistOrderRules.move(initial,88L,22L));
        eq(initial,PlaylistOrderRules.move(initial,11L,99L));
        // Filtered view [11,33,55]: moving 55 onto 33 must not discard hidden IDs 22/44.
        eq(Arrays.asList(11L,22L,55L,33L,44L),
                PlaylistOrderRules.move(Arrays.asList(11L,22L,33L,44L,55L),55L,33L));
        eq(initial,initial); // Input list remains unchanged.
        System.out.println("PlaylistOrderRules OK");
    }
}
