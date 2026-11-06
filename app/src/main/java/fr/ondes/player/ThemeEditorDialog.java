package fr.ondes.player;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import java.util.Locale;

/**
 * Three roles, three appearance modes. A color-picker always edits either the
 * solid color or a selected gradient stop; changes preview live and commit on
 * finger release. The dialog stays open when the player is rebuilt.
 */
public final class ThemeEditorDialog {
    public interface Actions {
        void onChanged();
        void onChooseImage(String role);
        void onReset();
    }
    private static final String[] ROLES = {"accent", "background", "text"};
    private static final String[] ROLE_NAMES = {"Accent", "Fond", "Texte"};
    private static final String[] MODES = {"solid", "gradient", "image"};
    private static final String[] MODE_NAMES = {"Uni", "Dégradé", "Image"};
    private static final int[][] SOLIDS = {
        {0xFFA8F36A,0xFF9B82F5,0xFFFF806A,0xFF69BFFF,0xFFFF88BC,0xFFFFCE73,0xFF5FE3CD,0xFFFFFFFF},
        {0xFF0B0C12,0xFF101B30,0xFF1F1533,0xFF2B151E,0xFF10231E,0xFF232831,0xFF191B28,0xFF0A2729},
        {0xFFF5F8FB,0xFFFFFFFF,0xFFE9E0FF,0xFFFFE9C5,0xFFD5F8D6,0xFFB3E6FF,0xFFFFCDD8,0xFF101820}
    };
    private static final String[] PRESET_NAMES = {
            "Néon", "Océan", "Aurore", "Braise", "Forêt", "Prune"
    };
    private static final int[][] ACCENT_STARTS = {
        {0xFFA8F36A,0xFF49CEFF,0xFFFFB2D9,0xFFFFBE59,0xFF63F3A5,0xFFCF8CFF},
        {0xFF5175FF,0xFF66FFD4,0xFFE98AFF,0xFFFF5D6B,0xFF80ADFF,0xFFFF82B7}
    };
    private static final int[][] TEXT_STARTS = {
        {0xFFFFFFFF,0xFFCAFAFF,0xFFFFE0EF,0xFFFFF1C1,0xFFD5FFE7,0xFFEBD8FF},
        {0xFFA8F36A,0xFF8BC7FF,0xFFECB0FF,0xFFFFBC97,0xFF80E4D2,0xFFB4CAFF}
    };

    private final Activity activity;
    private final Actions actions;
    private final SharedPreferences prefs;
    private Dialog dialog;
    private LinearLayout container, content, controls;
    private ScrollView scroll;
    private FrameLayout preview;
    private ImageView previewImage;
    private TextView previewTitle, previewAccent, previewText;
    private TextView[] targetButtons = new TextView[3];
    private TextView[] modeButtons = new TextView[3];
    private int selectedRole = 0;
    private int selectedStop = 0;
    private DualColorPickerView dualPicker;
    private TextView gradientStopOne, gradientStopTwo, gradientSample;
    private float dragY;

    public ThemeEditorDialog(Activity activity, Actions actions) {
        this.activity = activity;
        this.actions = actions;
        prefs = ThemeStyle.prefs(activity);
    }
    private String role() { return ROLES[selectedRole]; }
    private String mode() { return ThemeStyle.mode(activity, role()); }
    private int d(float n) { return Ui.dp(activity, n); }
    private TextView text(String s, int sp, int color, boolean bold) {
        return Ui.text(activity, s, sp, color, bold);
    }
    private TextView button(String name, boolean selected) {
        TextView v = text(name, 13, selected ? 0xFF101820 : Ui.TEXT, true);
        v.setGravity(Gravity.CENTER);
        v.setMinHeight(d(45));
        Ui.clickable(v, Ui.shape(selected ? Ui.ACCENT : Ui.SURFACE_ALT, 13, activity));
        return v;
    }
    private static int roleContrast(int value) {
        return ((Color.red(value)*299+Color.green(value)*587+Color.blue(value)*114)/1000) > 145
                ? 0xFF101820 : Color.WHITE;
    }

    public void show() {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        container = Ui.vertical(activity);
        container.setPadding(d(17), d(7), d(17), d(18));
        container.setBackground(Ui.gradient(0xFF262D3A, 0xFF141820, 26, activity));

        TextView handle = text("━━", 20, Ui.MUTED, true);
        handle.setGravity(Gravity.CENTER);
        handle.setContentDescription("Glisser vers le bas pour fermer");
        container.addView(handle, Ui.lp(activity, -1, 31));
        handle.setOnTouchListener((v, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                dragY = event.getRawY();
                return true;
            }
            if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                if (event.getRawY() - dragY > d(75)) dismiss();
                return true;
            }
            return true;
        });

        LinearLayout top = Ui.horizontal(activity);
        LinearLayout headings = Ui.vertical(activity);
        headings.addView(text("Mon univers", 22, Ui.TEXT, true));
        TextView summary = text("Choisissez l'apparence de chaque élément", 11, Ui.MUTED, false);
        summary.setPadding(0,d(4),0,0);
        headings.addView(summary);
        top.addView(headings, new LinearLayout.LayoutParams(0,-2,1f));
        TextView close = button("Terminer", false);
        close.setOnClickListener(v -> dismiss());
        LinearLayout.LayoutParams closeParams = Ui.lp(activity, -2, 43);
        closeParams.leftMargin = d(8);
        top.addView(close,closeParams);
        container.addView(top);
        container.addView(Ui.gap(activity,13));

        scroll = new ScrollView(activity);
        scroll.setVerticalScrollBarEnabled(false);
        content = Ui.vertical(activity);
        scroll.addView(content);
        container.addView(scroll, new LinearLayout.LayoutParams(-1,
                Math.min(d(660),(int)(activity.getResources().getDisplayMetrics().heightPixels * .72f))));

        preview = new FrameLayout(activity);
        previewImage = new ImageView(activity);
        previewImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
        previewImage.setAlpha(.24f);
        preview.addView(previewImage,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout previewContents = Ui.vertical(activity);
        previewContents.setPadding(d(15),d(15),d(15),d(15));
        previewTitle = text("onde.",27,Ui.TEXT,true);
        previewContents.addView(previewTitle);
        previewContents.addView(Ui.gap(activity,10));
        LinearLayout sample = Ui.horizontal(activity);
        previewAccent = text("♫",24,Color.BLACK,true);
        previewAccent.setGravity(Gravity.CENTER);
        sample.addView(previewAccent,Ui.lp(activity,44,44));
        previewText = text("Votre bibliothèque musicale",13,Ui.TEXT,true);
        previewText.setPadding(d(11),0,0,0);
        sample.addView(previewText);
        previewContents.addView(sample);
        preview.addView(previewContents,new FrameLayout.LayoutParams(-1,-2));
        content.addView(preview,Ui.lp(activity,-1,-2));
        content.addView(Ui.gap(activity,15));

        content.addView(text("QUE VOULEZ-VOUS PERSONNALISER ?",10,Ui.MUTED,true));
        content.addView(Ui.gap(activity,7));
        LinearLayout targets = Ui.horizontal(activity);
        for(int i=0;i<3;i++) {
            final int at=i;
            targetButtons[i]=button(ROLE_NAMES[i],i==selectedRole);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0,d(46),1f);
            if(i>0)p.leftMargin=d(7);
            targets.addView(targetButtons[i],p);
            targetButtons[i].setOnClickListener(v->{selectedRole=at; selectedStop=0;refresh();});
        }
        content.addView(targets);
        content.addView(Ui.gap(activity,15));
        content.addView(text("STYLE",10,Ui.MUTED,true));
        content.addView(Ui.gap(activity,7));

        LinearLayout modes = Ui.horizontal(activity);
        for(int i=0;i<3;i++) {
            final int at=i;
            modeButtons[i]=button(MODE_NAMES[i],MODES[i].equals(mode()));
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0,d(45),1f);
            if(i>0)p.leftMargin=d(7);
            modes.addView(modeButtons[i],p);
            modeButtons[i].setOnClickListener(v -> setMode(MODES[at]));
        }
        content.addView(modes);
        content.addView(Ui.gap(activity,13));

        controls=Ui.vertical(activity);
        content.addView(controls);
        content.addView(Ui.gap(activity,16));
        TextView reset = text("↻  Rétablir les couleurs d'origine",13,Ui.MUTED,true);
        reset.setGravity(Gravity.CENTER);
        reset.setPadding(d(10),d(14),d(10),d(14));
        Ui.clickable(reset,Ui.shape(Ui.SURFACE,14,activity));
        reset.setOnClickListener(v->{actions.onReset();selectedRole=0;selectedStop=0;refresh();});
        content.addView(reset);
        content.addView(Ui.gap(activity,15));

        dialog.setContentView(container);
        Window window=dialog.getWindow();
        if(window!=null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setDimAmount(.61f);
            window.setGravity(Gravity.BOTTOM);
            window.setNavigationBarColor(0xFF141820);
        }
        dialog.show();
        if(window!=null)window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);
        refresh();
    }

    private void setMode(String next) {
        prefs.edit().putString("theme_"+role()+"_mode",next).apply();
        selectedStop=0;
        actions.onChanged();
        refresh();
    }
    private void saveColor(int color) {
        String key="theme_"+role();
        if("gradient".equals(mode())) key += selectedStop == 0 ? "_start" : "_end";
        prefs.edit().putInt(key,color).apply();
        actions.onChanged();
        refresh();
    }
    private void setGradient(int a, int b, int angle, int intensity) {
        String p="theme_"+role();
        prefs.edit().putString(p+"_mode","gradient")
                .putInt(p+"_start",a).putInt(p+"_end",b)
                .putInt(p+"_angle",angle).putInt(p+"_strength",intensity).apply();
        actions.onChanged();
        refresh();
    }
    public void refreshState() { refresh(); }
    private void refresh() {
        if(dialog==null || !dialog.isShowing() || controls==null)return;
        for(int i=0;i<3;i++){
            setButton(targetButtons[i],i==selectedRole);
            setButton(modeButtons[i],MODES[i].equals(mode()));
        }
        updatePreview();
        controls.removeAllViews();
        if("image".equals(mode())) drawImageControls();
        else if("gradient".equals(mode())) drawGradientControls();
        else drawColorControls();
    }
    private void setButton(TextView view,boolean selected) {
        view.setTextColor(selected ? 0xFF101820 : Ui.TEXT);
        view.setBackground(Ui.shape(selected ? Ui.ACCENT : Ui.SURFACE_ALT,13,activity));
    }

    private void updatePreview() {
        String bgMode=ThemeStyle.mode(activity,"background");
        if("gradient".equals(bgMode)) {
            preview.setBackground(ThemeStyle.surface(activity,"background",18));
            previewImage.setVisibility(View.GONE);
        } else {
            preview.setBackground(Ui.shape(Ui.effectiveBackground(ThemeStyle.solid(activity,"background")),18,activity));
            Bitmap picture="image".equals(bgMode)?ThemeStyle.picture(activity,"background"):null;
            if(picture!=null) {
                previewImage.setImageBitmap(picture);
                previewImage.setVisibility(View.VISIBLE);
            } else previewImage.setVisibility(View.GONE);
        }
        previewTitle.setTextColor(ThemeStyle.solid(activity,"text"));
        previewText.setTextColor(ThemeStyle.solid(activity,"text"));
        ThemeStyle.applyText(previewTitle,"text");
        ThemeStyle.applyText(previewText,"text");
        previewAccent.setBackground(ThemeStyle.surface(activity,"accent",13));
        previewAccent.setTextColor(roleContrast(ThemeStyle.solid(activity,"accent")));
    }

    private void drawImageControls() {
        controls.addView(text("UNE IMAGE POUR "+ROLE_NAMES[selectedRole].toUpperCase(Locale.FRANCE),12,Ui.TEXT,true));
        controls.addView(Ui.gap(activity,9));
        String note=selectedRole==0
                ? "Votre image colore les boutons et les éléments accentués."
                : selectedRole==1
                ? "Votre photo s'affiche doucement derrière la bibliothèque pour garder les titres lisibles."
                : "Votre image remplit les caractères des titres et des libellés principaux.";
        controls.addView(text(note,12,Ui.MUTED,false));
        controls.addView(Ui.gap(activity,12));
        TextView pick=button("▣  Choisir une image",true);
        pick.setOnClickListener(v->actions.onChooseImage(role()));
        controls.addView(pick,Ui.lp(activity,-1,52));
        String saved=ThemeStyle.imageUri(activity,role());
        if(saved!=null && !saved.isEmpty()) {
            controls.addView(Ui.gap(activity,10));
            TextView clear=button("Retirer l'image",false);
            clear.setOnClickListener(v->{
                prefs.edit().remove("theme_"+role()+"_image")
                        .putString("theme_"+role()+"_mode","solid").apply();
                if("background".equals(role()))prefs.edit().remove("theme_wallpaper").apply();
                actions.onChanged();
                refresh();
            });
            controls.addView(clear,Ui.lp(activity,-1,45));
        }
        controls.addView(Ui.gap(activity,8));
        controls.addView(text("Seules les images de votre téléphone sont utilisées. Aucune image n'est envoyée à GitHub.",11,Ui.MUTED,false));
    }

    private int[] presetStart() {
        return selectedRole==0?ACCENT_STARTS[0]:selectedRole==2?TEXT_STARTS[0]:ThemeGradient.STARTS;
    }
    private int[] presetEnd() {
        return selectedRole==0?ACCENT_STARTS[1]:selectedRole==2?TEXT_STARTS[1]:ThemeGradient.ENDS;
    }
    /**
     * A single picker keeps both gradient stops on screen. Never remove this
     * view or rebuild the activity from an ACTION_MOVE/ACTION_UP callback.
     * Only the durable save after finger release refreshes the player.
     */
    private void drawGradientControls() {
        final String editedRole = role();
        controls.addView(text("DÉGRADÉS PRÊTS À UTILISER",11,Ui.MUTED,true));
        controls.addView(Ui.gap(activity,8));
        GridLayout presets = new GridLayout(activity);
        presets.setColumnCount(2);
        int[] starts = presetStart(), ends = presetEnd();
        for (int idx=0;idx<PRESET_NAMES.length;idx++) {
            final int selected=idx;
            TextView tile=text(PRESET_NAMES[idx],13,roleContrast(ThemeGradient.blend(starts[idx],ends[idx],.5f)),true);
            tile.setGravity(Gravity.CENTER_VERTICAL);
            tile.setPadding(d(12),d(8),d(7),d(8));
            tile.setBackground(Ui.gradient(starts[idx],ends[idx],12,activity));
            tile.setContentDescription("Choisir le dégradé "+PRESET_NAMES[idx]);
            tile.setOnClickListener(v->setGradient(starts[selected],ends[selected],315,100));
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.columnSpec=GridLayout.spec(idx%2,1,1f);
            lp.rowSpec=GridLayout.spec(idx/2);
            lp.width=0;
            lp.height=d(52);
            lp.setMargins(d(3),d(3),d(3),d(3));
            presets.addView(tile,lp);
        }
        controls.addView(presets,Ui.lp(activity,-1,-2));
        controls.addView(Ui.gap(activity,15));
        controls.addView(text("MES DEUX COULEURS",11,Ui.MUTED,true));
        controls.addView(Ui.gap(activity,6));
        controls.addView(text("Les repères 1 et 2 se déplacent dans le même carré et sur la barre des teintes. Touchez un repère pour le sélectionner.",12,Ui.MUTED,false));
        controls.addView(Ui.gap(activity,10));

        gradientSample = text("",13,Ui.TEXT,true);
        gradientSample.setContentDescription("Aperçu des deux couleurs du dégradé");
        gradientSample.setBackground(Ui.gradient(
                ThemeStyle.start(activity, editedRole),ThemeStyle.end(activity, editedRole),14,activity));
        controls.addView(gradientSample,Ui.lp(activity,-1,27));
        controls.addView(Ui.gap(activity,10));

        LinearLayout stopRow=Ui.horizontal(activity);
        gradientStopOne=text("1 · Première couleur",12,Color.WHITE,true);
        gradientStopTwo=text("2 · Seconde couleur",12,Color.WHITE,true);
        gradientStopOne.setGravity(Gravity.CENTER);
        gradientStopTwo.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams first=new LinearLayout.LayoutParams(0,d(46),1f);
        first.rightMargin=d(8);
        stopRow.addView(gradientStopOne,first);
        stopRow.addView(gradientStopTwo,new LinearLayout.LayoutParams(0,d(46),1f));
        controls.addView(stopRow);
        controls.addView(Ui.gap(activity,8));

        DualColorPickerView picker=new DualColorPickerView(activity);
        dualPicker=picker;
        picker.setColors(ThemeStyle.start(activity,editedRole),ThemeStyle.end(activity,editedRole));
        picker.setActiveStop(selectedStop);
        gradientStopOne.setOnClickListener(v->{
            selectedStop=0;
            picker.setActiveStop(0);
            updateGradientStops(picker.getColor(0),picker.getColor(1));
        });
        gradientStopTwo.setOnClickListener(v->{
            selectedStop=1;
            picker.setActiveStop(1);
            updateGradientStops(picker.getColor(0),picker.getColor(1));
        });
        picker.setListener((firstColor,secondColor,active,finished)->{
            if (dualPicker!=picker || dialog==null || !dialog.isShowing()) return;
            selectedStop=active;
            // Pure visual feedback, no preference save or tree reconstruction during drag.
            updateGradientStops(firstColor,secondColor);
            if (!finished) return;
            // DualColorPickerView posts the final callback after touch dispatch.
            prefs.edit().putInt("theme_"+editedRole+"_start",firstColor)
                    .putInt("theme_"+editedRole+"_end",secondColor).apply();
            actions.onChanged();
            if(dialog!=null && dialog.isShowing()) updatePreview();
        });
        controls.addView(picker,Ui.lp(activity,-1,265));
        updateGradientStops(picker.getColor(0),picker.getColor(1));

        controls.addView(Ui.gap(activity,8));
        TextView direction=text("Direction · "+ThemeStyle.angle(activity,editedRole)+"°",12,Ui.TEXT,true);
        controls.addView(direction);
        SeekBar angle=new SeekBar(activity);
        angle.setMax(7);
        angle.setProgress(ThemeStyle.angle(activity,editedRole)/45);
        angle.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar bar,int progress,boolean fromUser){
                if(fromUser)direction.setText("Direction · "+(progress*45)+"°");
            }
            public void onStartTrackingTouch(SeekBar bar){}
            public void onStopTrackingTouch(SeekBar bar){
                final int finalAngle=bar.getProgress()*45;
                bar.post(()->{
                    if (dialog==null || !dialog.isShowing()) return;
                    prefs.edit().putInt("theme_"+editedRole+"_angle",finalAngle).apply();
                    actions.onChanged();
                    updatePreview();
                });
            }
        });
        controls.addView(angle,Ui.lp(activity,-1,d(42)));

        TextView intensity=text("Intensité · "+ThemeStyle.strength(activity,editedRole)+" %",12,Ui.TEXT,true);
        controls.addView(intensity);
        SeekBar strength=new SeekBar(activity);
        strength.setMax(100);
        strength.setProgress(ThemeStyle.strength(activity,editedRole));
        strength.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar bar,int progress,boolean fromUser){
                if(fromUser)intensity.setText("Intensité · "+progress+" %");
            }
            public void onStartTrackingTouch(SeekBar bar){}
            public void onStopTrackingTouch(SeekBar bar){
                final int finalStrength=bar.getProgress();
                bar.post(()->{
                    if (dialog==null || !dialog.isShowing()) return;
                    prefs.edit().putInt("theme_"+editedRole+"_strength",finalStrength).apply();
                    actions.onChanged();
                    updatePreview();
                });
            }
        });
        controls.addView(strength,Ui.lp(activity,-1,d(42)));
        controls.addView(text("Astuce : déplace les couleurs directement sur la palette. L'application enregistre le résultat quand tu relâches le doigt.",11,Ui.MUTED,false));
    }

    private void updateGradientStops(int first, int second) {
        if (gradientSample==null || gradientStopOne==null || gradientStopTwo==null) return;
        gradientSample.setBackground(Ui.gradient(first,second,13,activity));
        gradientStopOne.setText((selectedStop==0 ? "✓  " : "")+"1 · Couleur");
        gradientStopTwo.setText((selectedStop==1 ? "✓  " : "")+"2 · Couleur");
        gradientStopOne.setTextColor(roleContrast(first));
        gradientStopTwo.setTextColor(roleContrast(second));
        // Construct these simple solid swatches directly, bypassing theme-aware
        // Ui.shape, so ACTION_MOVE cannot trigger an expensive themed shader.
        GradientDrawable firstBg=new GradientDrawable();
        firstBg.setColor(first);
        firstBg.setCornerRadius(d(11));
        GradientDrawable secondBg=new GradientDrawable();
        secondBg.setColor(second);
        secondBg.setCornerRadius(d(11));
        firstBg.setStroke(d(selectedStop==0 ? 2:1), selectedStop==0 ? Color.WHITE : 0xFF647080);
        secondBg.setStroke(d(selectedStop==1 ? 2:1), selectedStop==1 ? Color.WHITE : 0xFF647080);
        gradientStopOne.setBackground(firstBg);
        gradientStopTwo.setBackground(secondBg);
    }

    private void drawColorControls() {
        boolean gradient="gradient".equals(mode());
        controls.addView(text(gradient?(selectedStop==0?"COULEUR DE DÉPART":"COULEUR D'ARRIVÉE"):
                "CHOISIR UNE COULEUR",11,Ui.MUTED,true));
        controls.addView(Ui.gap(activity,8));
        int color=gradient?(selectedStop==0?ThemeStyle.start(activity,role()):ThemeStyle.end(activity,role())):
                ThemeStyle.solid(activity,role());
        ColorPickerView picker=new ColorPickerView(activity);
        picker.setColor(color);
        TextView label=text("Glissez dans le carré ou la barre arc-en-ciel.",11,Ui.MUTED,false);
        controls.addView(label);
        controls.addView(Ui.gap(activity,5));
        TextView sample=text("",14,Ui.TEXT,true);
        picker.setListener((chosen,finished)->{
            // Only the sample changes while dragging; saving/rebuilding is
            // deferred until after Android has finished delivering ACTION_UP.
            GradientDrawable swatch = new GradientDrawable();
            swatch.setColor(chosen);
            swatch.setCornerRadius(d(10));
            sample.setBackground(swatch);
            if(finished) picker.post(() -> saveColor(chosen));
        });
        controls.addView(picker,Ui.lp(activity,-1,240));
        controls.addView(Ui.gap(activity,7));
        sample.setBackground(Ui.shape(color,10,activity));
        sample.setContentDescription("Couleur sélectionnée");
        controls.addView(sample,Ui.lp(activity,-1,12));
        controls.addView(Ui.gap(activity,11));
        controls.addView(text("COULEURS RAPIDES",11,Ui.MUTED,true));
        controls.addView(Ui.gap(activity,7));
        GridLayout palette=new GridLayout(activity);
        palette.setColumnCount(4);
        int[] choices=SOLIDS[selectedRole];
        for(int i=0;i<choices.length;i++){
            final int c=choices[i];
            TextView cell=text(c==color?"✓":"",19,roleContrast(c),true);
            cell.setGravity(Gravity.CENTER);
            GradientDrawable fill = new GradientDrawable();
            fill.setColor(c);
            fill.setCornerRadius(d(25));
            fill.setStroke(d(c==color?3:1), c==color?Color.WHITE:0xFF6E7C8C);
            cell.setBackground(fill);
            cell.setContentDescription(String.format(Locale.ROOT,"Couleur #%06X",c & 0xFFFFFF));
            cell.setOnClickListener(v->saveColor(c));
            GridLayout.LayoutParams cp=new GridLayout.LayoutParams();
            cp.columnSpec=GridLayout.spec(i%4,1,1f);
            cp.rowSpec=GridLayout.spec(i/4);
            cp.width=0;cp.height=d(51);
            cp.setMargins(d(4),d(4),d(4),d(4));
            palette.addView(cell,cp);
        }
        controls.addView(palette,Ui.lp(activity,-1,-2));
        controls.addView(Ui.gap(activity,7));
        TextView advanced=text("▸ Code HEX (option avancée)",12,Ui.MUTED,false);
        advanced.setPadding(d(7),d(8),d(7),d(8));
        controls.addView(advanced);
        LinearLayout hexRow=Ui.horizontal(activity);
        hexRow.setVisibility(View.GONE);
        EditText field=new EditText(activity);
        field.setSingleLine(true);
        field.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        field.setTextColor(Ui.TEXT);
        field.setHintTextColor(Ui.MUTED);
        field.setTextSize(14);
        field.setText(String.format(Locale.ROOT,"#%06X",color&0xFFFFFF));
        field.setSelectAllOnFocus(true);
        field.setBackground(Ui.outlined(Ui.SURFACE,0xFF556474,10,activity));
        field.setPadding(d(9),d(4),d(5),d(4));
        hexRow.addView(field,new LinearLayout.LayoutParams(0,d(43),1f));
        TextView apply=button("Valider",true);
        apply.setOnClickListener(v->{
            String value=field.getText().toString().trim();
            if(!value.matches("(?i)#[0-9a-f]{6}")){
                field.setError("Format : #A8F36A");
                return;
            }
            saveColor(Color.parseColor(value));
        });
        LinearLayout.LayoutParams hp=Ui.lp(activity,-2,43);
        hp.leftMargin=d(8);
        hexRow.addView(apply,hp);
        controls.addView(hexRow);
        advanced.setOnClickListener(v->{
            boolean open=hexRow.getVisibility()!=View.VISIBLE;
            hexRow.setVisibility(open?View.VISIBLE:View.GONE);
            advanced.setText(open?"▾ Masquer le code HEX":"▸ Code HEX (option avancée)");
        });
    }

    public void dismiss(){ if(dialog!=null) dialog.dismiss(); }
}
