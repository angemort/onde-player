import fr.ondes.player.ThemePalette;
public final class ThemePaletteTest {
    private static void check(boolean value,String message) {
        if(!value)throw new AssertionError(message);
    }
    public static void main(String[] args){
        int dark=0xFF101218,light=0xFFF1EAD7;
        ThemePalette.Colors purple=ThemePalette.derive(0xFFA46AFF,dark,0xFFF6F6FF);
        ThemePalette.Colors cyan=ThemePalette.derive(0xFF38DDDF,dark,0xFFF6F6FF);
        check(purple.accentHover!=cyan.accentHover,"hover follows accent");
        check(purple.accentSecondary!=cyan.accentSecondary,"secondary follows accent");
        check(purple.accentSubtle!=cyan.accentSubtle,"subtle selected background follows accent");
        check(purple.selection!=cyan.selection,"selected rows change with accent");
        check(purple.surface!=purple.surfaceAlt,"surface depth");
        check(purple.surface!=purple.surfaceHover,"surface hover");
        check(purple.surface!=purple.surfacePressed,"surface pressed");
        check(ThemePalette.contrast(purple.muted,dark)>=3.0,"muted text contrast dark");
        check(ThemePalette.contrast(purple.onAccent,0xFFA46AFF)>=4.5,"button text contrast dark");
        ThemePalette.Colors sunny=ThemePalette.derive(0xFFFFDC5B,light,0xFF222631);
        check(ThemePalette.contrast(sunny.muted,light)>=3.0,"muted text contrast light");
        check(ThemePalette.contrast(sunny.onAccent,0xFFFFDC5B)>=4.5,"button text contrast light");
        ThemePalette.Colors mid=ThemePalette.derive(ThemePalette.mix(0xFF55D7FF,0xFFAB55F9,.5f),
                dark,0xFFFAFAFA);
        check(mid.accentSecondary!=cyan.accentSecondary,"gradient midpoint affects palette");
        check(ThemePalette.contrast(sunny.secondaryAccent,light)>=3.0,"subtle accent legible");
        check((purple.ripple>>>24)!=0,"ripple opacity");
        System.out.println("ThemePalette: OK");
    }
}
