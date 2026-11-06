import fr.ondes.player.DualColorPickerRules;
public final class DualColorPickerRulesTest {
    private static void expect(boolean ok, String name) {
        if (!ok) throw new AssertionError(name);
    }
    public static void main(String[] args) {
        expect(DualColorPickerRules.choose(0,5,350,100,2)==0,"first handle");
        expect(DualColorPickerRules.choose(0,350,5,100,2)==1,"second handle");
        expect(DualColorPickerRules.choose(1,400,500,100,2)==1,"free palette retains active stop");
        expect(DualColorPickerRules.choose(0,30,32,100,5)==0,"overlapping handles use active 0");
        expect(DualColorPickerRules.choose(1,30,32,100,5)==1,"overlapping handles use active 1");
        expect(DualColorPickerRules.choose(1,4,60,100,3)==0,"switch to first handle");
        expect(DualColorPickerRules.choose(0,60,4,100,3)==1,"switch to second handle");
        System.out.println("DualColorPickerRules: OK");
    }
}
