package fr.ondes.player;

public final class SearchRulesTest {
    private static void match(boolean expected, String q, String title, String artist, String album) {
        boolean actual = SearchRules.matches(q, title, artist, album);
        if (actual != expected) throw new AssertionError("query=" + q + ", expected=" + expected);
    }
    public static void main(String[] args) {
        match(true, "", "Track", "Artist", "Album");
        match(true, "éTé", "L'été", "Beyoncé", "Live");
        match(true, "beyonce live", "Halo", "Beyoncé", "Live");
        match(true, "  HALO   beYonCe ", "Halo", "Beyoncé", "Live");
        match(false, "beyonce remix", "Halo", "Beyoncé", "Live");
        match(true, "clip", "Clip de concert", "Auteur", "Vidéos");
        match(false, "rock", "Jazz", "Auteur", "Album");
        System.out.println("SearchRulesTest: 7 scénarios réussis");
    }
}
