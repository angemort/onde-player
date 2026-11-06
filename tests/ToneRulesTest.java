import fr.ondes.player.ToneRules;
import java.util.Arrays;

public final class ToneRulesTest {
    private static void equal(Object actual, Object expected, String caseName) {
        if (!actual.equals(expected)) throw new AssertionError(caseName + ": " + actual + " != " + expected);
    }
    public static void main(String[] args) {
        int[] typical = {60, 230, 910, 3600, 14000};
        equal(Arrays.toString(ToneRules.bands(typical, 0)), "[0, 1]", "bass bands");
        equal(Arrays.toString(ToneRules.bands(typical, 1)), "[3, 4]", "treble bands");
        equal(Arrays.toString(ToneRules.bands(new int[]{800, 1200}, 0)), "[0]", "bass fallback");
        equal(Arrays.toString(ToneRules.bands(new int[]{800, 1200}, 1)), "[1]", "treble fallback");
        equal(Arrays.toString(ToneRules.bands(new int[]{500}, 1)), "[]", "single band unsupported");
        equal(ToneRules.gain((short)-1500, (short)1500, 0), (short)-1500, "minimum gain");
        equal(ToneRules.gain((short)-1500, (short)1500, 50), (short)0, "neutral gain");
        equal(ToneRules.gain((short)-1500, (short)1500, 100), (short)1500, "maximum gain");
        equal(ToneRules.progress((short)-1500, (short)1500, -1500), 0, "min progress");
        equal(ToneRules.progress((short)-1500, (short)1500, 0), 50, "neutral progress");
        equal(ToneRules.progress((short)-1500, (short)1500, 1500), 100, "max progress");
        equal(ToneRules.gain((short)-1200, (short)900, 100), (short)900, "asymmetric max");
        System.out.println("ToneRulesTest: 12 checks OK");
    }
}
