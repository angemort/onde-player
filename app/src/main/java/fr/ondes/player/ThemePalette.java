package fr.ondes.player;

/**
 * Pure color math for every secondary, pressed, selected and outline token.
 * The Android UI obtains the source colors from the user's three main roles,
 * including the midpoint of gradient stops. No separate user inputs required.
 */
public final class ThemePalette {
    private ThemePalette() {}

    public static final class Colors {
        public final int surface, surfaceAlt, surfaceHover, surfacePressed;
        public final int muted, outline, divider, selection;
        public final int accentHover, accentPressed, accentSecondary, accentSubtle, accentOutline;
        public final int onAccent, ripple, rippleOnAccent, secondaryAccent;
        public Colors(int surface, int surfaceAlt, int surfaceHover, int surfacePressed,
                      int muted, int outline, int divider, int selection,
                      int accentHover, int accentPressed, int accentSecondary,
                      int accentSubtle, int accentOutline, int onAccent,
                      int ripple, int rippleOnAccent, int secondaryAccent) {
            this.surface=surface; this.surfaceAlt=surfaceAlt;
            this.surfaceHover=surfaceHover; this.surfacePressed=surfacePressed;
            this.muted=muted; this.outline=outline; this.divider=divider; this.selection=selection;
            this.accentHover=accentHover; this.accentPressed=accentPressed;
            this.accentSecondary=accentSecondary; this.accentSubtle=accentSubtle;
            this.accentOutline=accentOutline; this.onAccent=onAccent;
            this.ripple=ripple; this.rippleOnAccent=rippleOnAccent;
            this.secondaryAccent=secondaryAccent;
        }
    }

    public static int mix(int a, int b, float amount) {
        float t = Math.max(0f, Math.min(1f, amount));
        int r = Math.round(((a >>> 16) & 255) * (1f-t) + ((b >>> 16) & 255) * t);
        int g = Math.round(((a >>> 8) & 255) * (1f-t) + ((b >>> 8) & 255) * t);
        int bl = Math.round((a & 255) * (1f-t) + (b & 255) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    private static double linear(int c) {
        double v=c/255.0;
        return v <= .04045 ? v/12.92 : Math.pow((v+.055)/1.055,2.4);
    }
    public static double luminance(int c) {
        return .2126 * linear((c>>>16)&255)
                + .7152 * linear((c>>>8)&255) + .0722 * linear(c&255);
    }
    public static double contrast(int a,int b) {
        double x=luminance(a),y=luminance(b);
        return (Math.max(x,y)+.05)/(Math.min(x,y)+.05);
    }
    public static int readableOn(int bg) {
        return contrast(bg,0xFF101820) >= contrast(bg,0xFFFFFFFF)
                ? 0xFF101820 : 0xFFFFFFFF;
    }
    private static int ensureContrast(int desired, int bg, double minContrast) {
        int result=desired;
        if(contrast(result,bg)>=minContrast)return result;
        int end=readableOn(bg);
        for(int p=1;p<=20;p++){
            result=mix(desired,end,p/20f);
            if(contrast(result,bg)>=minContrast)return result;
        }
        return end;
    }

    public static Colors derive(int accent,int background,int text) {
        int bg=background|0xFF000000;
        int ac=accent|0xFF000000;
        int ink=text|0xFF000000;
        boolean dark=luminance(bg)<.4;

        // Surfaces remain linked to the chosen background; they do not acquire
        // a random hue from a deliberately colorful title text.
        int lift=dark?0xFFFFFFFF:0xFF000000;
        int surface=mix(bg,lift,dark?.08f:.035f);
        int surfaceAlt=mix(bg,lift,dark?.14f:.075f);

        int accentDirection=luminance(ac)>.45?0xFF080E14:0xFFFFFFFF;
        int hover=mix(ac,accentDirection,.13f);
        int pressed=mix(ac,accentDirection,.24f);
        int secondary=mix(bg,ac,dark?.26f:.16f);
        int subtle=mix(bg,ac,dark?.14f:.09f);
        int select=mix(surface,ac,dark?.23f:.15f);
        int surfHover=mix(surface,ac,.095f);
        int surfPressed=mix(surface,ac,.19f);
        int outline=ensureContrast(mix(surface,ink,.26f),surface,1.6);
        int divider=mix(surface,ink,.15f);
        int muted=ensureContrast(mix(bg,ink,.65f),bg,3.0);
        int secondaryAccent=ensureContrast(mix(ac,ink,.25f),bg,3.0);
        int onAccent=readableOn(ac);
        // The pressed/focused ripple honors the palette rather than using a
        // hardcoded white flash on every custom theme.
        int ripple=0x44000000|(ac&0x00FFFFFF);
        int rippleOnAccent=0x44000000|(onAccent&0x00FFFFFF);

        return new Colors(surface,surfaceAlt,surfHover,surfPressed,muted,outline,divider,
                select,hover,pressed,secondary,subtle,mix(bg,ac,.55f),
                onAccent,ripple,rippleOnAccent,secondaryAccent);
    }
}
