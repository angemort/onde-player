package fr.ondes.player;
import java.util.Arrays;
public class AlphabetRulesTest {
    static void assertEquals(Object a, Object b) {
        if (!a.equals(b)) throw new AssertionError(a + " != " + b);
    }
    public static void main(String[] args) {
        assertEquals('E', AlphabetRules.group("Été"));
        assertEquals('A', AlphabetRules.group("À demain"));
        assertEquals('#', AlphabetRules.group("1999"));
        assertEquals('Z', AlphabetRules.group("Zèbre"));
        int[] positions = AlphabetRules.firstPositions(Arrays.asList("1999","À l'aube","Énergie","Zénith"));
        assertEquals(2, AlphabetRules.nearestPosition(positions,'B'));
        assertEquals(1, AlphabetRules.nearestPosition(positions,'A'));
        assertEquals(3, AlphabetRules.nearestPosition(positions,'Z'));
        System.out.println("AlphabetRules OK");
    }
}
