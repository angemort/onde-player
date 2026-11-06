package fr.ondes.player;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
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
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.Locale;

/** A persistent, self-contained theme editor: choosing a swatch never dismisses the sheet. */
public final class ThemeEditorDialog {
    public interface Actions {
        void onColor(String preferenceKey, int color);
        void onPickWallpaper();
        void onClearWallpaper();
        void onReset();
    }

    private static final int[] ACCENTS = {
            0xFFA8F36A, 0xFF9B82F5, 0xFFFF806A, 0xFF69BFFF,
            0xFFFF88BC, 0xFFFFCE73, 0xFF5FE3CD
    };
    private static final String[] ACCENT_NAMES = {
            "Vert Onde", "Violet", "Corail", "Bleu", "Rose", "Or", "Turquoise"
    };
    private static final int[] BACKGROUNDS = {
            0xFF0B0C12, 0xFF101B30, 0xFF1F1533,
            0xFF2B151E, 0xFF10231E, 0xFF232831
    };
    private static final String[] BACKGROUND_NAMES = {
            "Noir Onde", "Bleu nuit", "Violet", "Bordeaux", "Forêt", "Ardoise"
    };
    private static final int[] TEXT_COLORS = {
            0xFFF5F8FB, 0xFFFFFFFF, 0xFFE9E0FF, 0xFFFFE9C5,
            0xFFD5F8D6, 0xFFB3E6FF, 0xFFFFCDD8, 0xFF101820
    };
    private static final String[] TEXT_COLOR_NAMES = {
            "Blanc Onde", "Blanc", "Lavande", "Crème",
            "Menthe", "Ciel", "Rosé", "Anthracite"
    };

    private final Activity activity;
    private final Actions actions;
    private final SharedPreferences prefs;
    private Dialog dialog;
    // 0: accent, 1: background, 2: main text.
    private int colorTab = 0;
    private GridLayout swatches;
    private FrameLayout preview;
    private TextView previewTitle, previewCaption, previewChip;
    private TextView accentButton, backgroundButton, textColorButton, imageButton;
    private EditText hexField;
    private TextView selectedLabel;
    private ColorPickerView colorPicker;
    private TextView sampleCircle;
    private TextView pickerHint;
    private LinearLayout hexRow;
    private TextView advancedHexButton;
    private boolean hexAdvancedOpen;
    private LinearLayout body;
    private ScrollView scrollView;
    private float downY;
    private boolean collapsed;
    private TextView applyHexButton;

    public ThemeEditorDialog(Activity activity, Actions actions) {
        this.activity = activity;
        this.actions = actions;
        this.prefs = activity.getSharedPreferences("onde_settings", Context.MODE_PRIVATE);
    }

    private int d(float dp) { return Ui.dp(activity, dp); }
    private String colorKey(int tab) {
        return tab == 0 ? "theme_accent" : tab == 1 ? "theme_background" : "theme_text";
    }
    private int currentColor(int tab) {
        return prefs.getInt(colorKey(tab),
                tab == 0 ? ACCENTS[0] : tab == 1 ? BACKGROUNDS[0] : 0xFFF5F8FB);
    }
    private String hex(int color) { return String.format(Locale.ROOT, "#%06X", color & 0xFFFFFF); }
    private TextView text(String value, int size, int color, boolean bold) {
        return Ui.text(activity, value, size, color, bold);
    }
    private LinearLayout.LayoutParams layout(int width, int height) { return Ui.lp(activity, width, height); }

    public void show() {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout panel = Ui.vertical(activity);
        panel.setPadding(d(18), d(8), d(18), d(15));
        panel.setBackground(Ui.gradient(0xFF262D3A, 0xFF151922, 26, activity));

        TextView handle = text("━━", 20, 0xFF81909C, true);
        handle.setGravity(Gravity.CENTER);
        handle.setContentDescription("Faire glisser vers le bas pour fermer le thème");
        handle.setPadding(0, d(1), 0, d(12));
        panel.addView(handle, layout(-1, 34));
        handle.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN: downY = event.getRawY(); return true;
                case MotionEvent.ACTION_MOVE:
                    panel.setTranslationY(Math.max(0f, event.getRawY() - downY) * .55f);
                    return true;
                case MotionEvent.ACTION_UP:
                    float delta = event.getRawY() - downY;
                    if (delta > d(170) || (collapsed && delta > d(65))) dismiss();
                    else if (delta > d(50)) setCollapsed(true);
                    else if (delta < -d(45)) setCollapsed(false);
                    else if (Math.abs(delta) < d(12)) setCollapsed(!collapsed);
                    panel.animate().translationY(0).setDuration(160).start();
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    panel.animate().translationY(0).setDuration(160).start();
                    return true;
            }
            return false;
        });

        LinearLayout top = Ui.horizontal(activity);
        LinearLayout headings = Ui.vertical(activity);
        headings.addView(text("Personnaliser le thème", 21, Ui.TEXT, true));
        TextView subtitle = text("Les changements s'appliquent sans fermer ce volet", 11, Ui.MUTED, false);
        subtitle.setPadding(0, d(4), 0, 0);
        headings.addView(subtitle);
        top.addView(headings, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView close = text("Terminer", 13, Ui.TEXT, true);
        close.setGravity(Gravity.CENTER);
        close.setPadding(d(13), d(11), d(13), d(11));
        Ui.clickable(close, Ui.shape(0xFF38434D, 16, activity));
        close.setContentDescription("Fermer la personnalisation du thème");
        close.setOnClickListener(v -> dismiss());
        LinearLayout.LayoutParams closeParams = layout(-2, -2);
        closeParams.leftMargin = d(12);
        top.addView(close, closeParams);
        panel.addView(top);
        panel.addView(Ui.gap(activity, 14));

        ScrollView scroll = new ScrollView(activity);
        scrollView = scroll;
        scroll.setVerticalScrollBarEnabled(false);
        body = Ui.vertical(activity);
        scroll.addView(body);
        panel.addView(scroll, new LinearLayout.LayoutParams(-1, Math.min(d(640),
                (int)(activity.getResources().getDisplayMetrics().heightPixels * .72f))));

        preview = new FrameLayout(activity);
        LinearLayout previewInner = Ui.vertical(activity);
        previewInner.setPadding(d(17), d(16), d(17), d(16));
        previewTitle = text("onde.", 29, Ui.TEXT, true);
        previewInner.addView(previewTitle);
        previewInner.addView(Ui.gap(activity, 13));
        LinearLayout previewRow = Ui.horizontal(activity);
        previewChip = text("♫", 22, 0xFF111111, true);
        previewChip.setGravity(Gravity.CENTER);
        previewChip.setBackground(Ui.shape(Ui.ACCENT, 13, activity));
        previewRow.addView(previewChip, layout(46, 46));
        previewCaption = text("Aperçu de votre bibliothèque", 13, Ui.TEXT, true);
        previewCaption.setPadding(d(12), 0, 0, 0);
        previewRow.addView(previewCaption);
        previewInner.addView(previewRow);
        preview.addView(previewInner);
        body.addView(preview, layout(-1, -2));
        body.addView(Ui.gap(activity, 19));

        LinearLayout tabs = Ui.horizontal(activity);
        accentButton = makeTab("Accent");
        backgroundButton = makeTab("Fond");
        textColorButton = makeTab("Texte");
        tabs.addView(accentButton, new LinearLayout.LayoutParams(0, d(48), 1f));
        LinearLayout.LayoutParams middle = new LinearLayout.LayoutParams(0, d(48), 1f);
        middle.leftMargin = d(10);
        tabs.addView(backgroundButton, middle);
        LinearLayout.LayoutParams right = new LinearLayout.LayoutParams(0, d(48), 1f);
        right.leftMargin = d(10);
        tabs.addView(textColorButton, right);
        accentButton.setOnClickListener(v -> selectTab(0));
        backgroundButton.setOnClickListener(v -> selectTab(1));
        textColorButton.setOnClickListener(v -> selectTab(2));
        body.addView(tabs);
        body.addView(Ui.gap(activity, 14));

        // Visual color selection comes first; no hexadecimal input is required.
        selectedLabel = text("Personnalisez votre couleur", 13, Ui.TEXT, true);
        body.addView(selectedLabel);
        body.addView(Ui.gap(activity, 5));
        pickerHint = text("Glissez dans le carré pour choisir la nuance, puis sur la barre arc-en-ciel pour changer de teinte.", 12, Ui.MUTED, false);
        body.addView(pickerHint);
        body.addView(Ui.gap(activity, 8));
        colorPicker = new ColorPickerView(activity);
        colorPicker.setListener((chosen, finished) -> {
            updatePreview(chosen);
            if (finished) save(colorTab, chosen);
        });
        body.addView(colorPicker, layout(-1, 260));

        LinearLayout selectedRow = Ui.horizontal(activity);
        selectedRow.setPadding(d(5), 0, d(4), 0);
        sampleCircle = text("✓", 17, 0xFF142018, true);
        sampleCircle.setGravity(Gravity.CENTER);
        selectedRow.addView(sampleCircle, layout(38, 38));
        TextView selectedDescription = text("Aperçu de votre couleur", 13, Ui.TEXT, true);
        selectedDescription.setPadding(d(12), 0, 0, 0);
        selectedRow.addView(selectedDescription);
        body.addView(selectedRow);
        body.addView(Ui.gap(activity, 15));

        body.addView(text("COULEURS RAPIDES", 11, Ui.MUTED, true));
        body.addView(Ui.gap(activity, 8));
        swatches = new GridLayout(activity);
        swatches.setColumnCount(4);
        body.addView(swatches, layout(-1, -2));
        body.addView(Ui.gap(activity, 10));

        advancedHexButton = text("▸  Saisir un code couleur (avancé)", 12, Ui.MUTED, false);
        advancedHexButton.setPadding(d(8), d(10), d(4), d(10));
        advancedHexButton.setOnClickListener(v -> {
            hexAdvancedOpen = !hexAdvancedOpen;
            hexRow.setVisibility(hexAdvancedOpen ? View.VISIBLE : View.GONE);
            advancedHexButton.setText(hexAdvancedOpen
                    ? "▾  Masquer le code couleur" : "▸  Saisir un code couleur (avancé)");
        });
        body.addView(advancedHexButton);

        hexRow = Ui.horizontal(activity);
        hexRow.setVisibility(View.GONE);
        hexField = new EditText(activity);
        hexField.setSingleLine(true);
        hexField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        hexField.setTextColor(Ui.TEXT);
        hexField.setHintTextColor(Ui.MUTED);
        hexField.setTextSize(15);
        hexField.setSelectAllOnFocus(true);
        hexField.setHint("#RRGGBB");
        hexField.setPadding(d(13), d(4), d(7), d(4));
        hexField.setBackground(Ui.outlined(0xFF29313F, 0xFF556474, 13, activity));
        hexRow.addView(hexField, new LinearLayout.LayoutParams(0, d(49), 1f));
        TextView applyHex = text("Appliquer", 13, 0xFF101712, true);
        applyHex.setGravity(Gravity.CENTER);
        applyHex.setPadding(d(12), 0, d(12), 0);
        applyHex.setBackground(Ui.shape(0xFFA8F36A, 12, activity));
        LinearLayout.LayoutParams ap = layout(-2, 49);
        ap.leftMargin = d(9);
        hexRow.addView(applyHex, ap);
        applyHexButton = applyHex;
        applyHex.setOnClickListener(v -> {
            String input = hexField.getText().toString().trim();
            if (!input.matches("(?i)#[0-9a-f]{6}")) {
                hexField.setError("Code couleur attendu : #A8F36A");
                return;
            }
            save(colorTab, Color.parseColor(input));
            hexField.clearFocus();
        });
        body.addView(hexRow);
        body.addView(Ui.gap(activity, 17));

        body.addView(Ui.line(activity));
        body.addView(Ui.gap(activity, 14));
        body.addView(text("IMAGE DE FOND", 11, Ui.MUTED, true));
        body.addView(Ui.gap(activity, 9));
        imageButton = actionRow("Choisir une photo", "Image personnelle derrière la bibliothèque", "▣");
        imageButton.setOnClickListener(v -> actions.onPickWallpaper());
        body.addView(imageButton);
        TextView remove = actionRow("Retirer l'image", "Revenir à la couleur de fond", "✕");
        remove.setOnClickListener(v -> {
            actions.onClearWallpaper();
            refreshState();
        });
        body.addView(Ui.gap(activity, 8));
        body.addView(remove);
        body.addView(Ui.gap(activity, 13));
        TextView reset = actionRow("Rétablir le thème d'origine", "Rétablir les couleurs et retirer l'image", "↻");
        reset.setOnClickListener(v -> {
            actions.onReset();
            refreshState();
        });
        body.addView(reset);
        body.addView(Ui.gap(activity, 18));

        dialog.setContentView(panel);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setDimAmount(.56f);
            window.setGravity(Gravity.BOTTOM);
            window.setNavigationBarColor(0xFF151922);
        }
        dialog.setOnDismissListener(v -> dialog = null);
        dialog.show();
        if (window != null) window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        refreshState();
    }

    private TextView makeTab(String title) {
        TextView view = text(title, 13, Ui.TEXT, true);
        view.setGravity(Gravity.CENTER);
        return view;
    }

    private TextView actionRow(String title, String description, String symbol) {
        // The description is available to TalkBack and uses a short row to preserve height.
        TextView view = text(symbol + "   " + title + "   ›", 14, Ui.TEXT, true);
        view.setContentDescription(title + ". " + description);
        view.setPadding(d(13), d(14), d(11), d(14));
        Ui.clickable(view, Ui.shape(0xFF29313F, 14, activity));
        return view;
    }

    private void selectTab(int tab) {
        if (hexField != null) hexField.clearFocus();
        colorTab = tab;
        refreshState();
    }

    private void save(int tab, int color) {
        if (hexField != null) hexField.clearFocus();
        actions.onColor(colorKey(tab), color);
        // The editor's Dialog is deliberately left open: no dismissal or Activity.recreate().
        refreshState();
    }

    public void refreshState() {
        if (dialog == null || !dialog.isShowing() || swatches == null) return;
        int selected = currentColor(colorTab);
        int bg = Ui.BG;
        int accent = currentColor(0);
        preview.setBackground(Ui.outlined(bg, 0xFF55606F, 19, activity));
        previewChip.setBackground(Ui.shape(accent, 13, activity));
        previewChip.setTextColor(perceivedLuma(accent) > 135 ? 0xFF161B22 : Color.WHITE);
        previewCaption.setTextColor(Ui.MUTED);
        boolean hasImage = !prefs.getString("theme_wallpaper", "").isEmpty();
        previewCaption.setText(hasImage ? "Image en arrière-plan" : "Aperçu de votre bibliothèque");
        int activeTabText = perceivedLuma(accent) > 135 ? 0xFF101712 : Color.WHITE;
        accentButton.setTextColor(colorTab == 0 ? activeTabText : Ui.TEXT);
        backgroundButton.setTextColor(colorTab == 1 ? activeTabText : Ui.TEXT);
        textColorButton.setTextColor(colorTab == 2 ? activeTabText : Ui.TEXT);
        accentButton.setBackground(Ui.shape(colorTab == 0 ? accent : 0xFF303A45, 13, activity));
        backgroundButton.setBackground(Ui.shape(colorTab == 1 ? accent : 0xFF303A45, 13, activity));
        textColorButton.setBackground(Ui.shape(colorTab == 2 ? accent : 0xFF303A45, 13, activity));
        applyHexButton.setTextColor(activeTabText);
        applyHexButton.setBackground(Ui.shape(accent, 12, activity));
        selectedLabel.setText(colorTab == 0 ? "Couleur principale" : colorTab == 1 ? "Couleur du fond" : "Couleur du texte");
        colorPicker.setColor(selected);
        updatePreview(selected);
        if (!hexField.hasFocus()) {
            hexField.setText(hex(selected));
            hexField.setSelection(hexField.length());
        }
        swatches.removeAllViews();
        int[] colors = colorTab == 0 ? ACCENTS : colorTab == 1 ? BACKGROUNDS : TEXT_COLORS;
        String[] names = colorTab == 0 ? ACCENT_NAMES : colorTab == 1 ? BACKGROUND_NAMES : TEXT_COLOR_NAMES;
        for (int i = 0; i < colors.length; i++) {
            final int chosen = colors[i];
            LinearLayout cell = Ui.vertical(activity);
            cell.setGravity(Gravity.CENTER);
            cell.setPadding(d(5), d(7), d(5), d(12));
            TextView circle = text(chosen == selected ? "✓" : "", 20,
                    perceivedLuma(chosen) > 135 ? 0xFF1B2027 : Color.WHITE, true);
            circle.setGravity(Gravity.CENTER);
            GradientDrawable fill = Ui.shape(chosen, 30, activity);
            fill.setStroke(d(chosen == selected ? 3 : 1),
                    chosen == selected ? Color.WHITE : 0xFF667280);
            circle.setBackground(fill);
            cell.addView(circle, layout(47, 47));
            TextView name = text(names[i], 10, Ui.MUTED, false);
            name.setGravity(Gravity.CENTER);
            name.setSingleLine(true);
            name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams np = layout(-1, 26);
            np.topMargin = d(5);
            cell.addView(name, np);
            cell.setContentDescription(names[i] + (chosen == selected ? ", sélectionné" : ", toucher pour appliquer"));
            cell.setOnClickListener(v -> save(colorTab, chosen));
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = 0;
            lp.height = -2;
            lp.columnSpec = GridLayout.spec(i % 4, 1, 1f);
            lp.rowSpec = GridLayout.spec(i / 4);
            swatches.addView(cell, lp);
        }
    }

    /** While the finger moves only the preview changes; the full activity is refreshed on release. */
    private void updatePreview(int chosen) {
        if (preview == null || sampleCircle == null) return;
        int displayedAccent = colorTab == 0 ? chosen : currentColor(0);
        int displayedBackground = colorTab == 1 ? chosen : currentColor(1);
        int displayedText = colorTab == 2 ? chosen : currentColor(2);
        // The actual player darkens bright backgrounds to keep its light text readable.
        int readableBackground = Ui.effectiveBackground(displayedBackground);
        preview.setBackground(Ui.outlined(readableBackground, 0xFF55606F, 19, activity));
        previewChip.setBackground(Ui.shape(displayedAccent, 13, activity));
        previewChip.setTextColor(perceivedLuma(displayedAccent) > 135 ? 0xFF161B22 : Color.WHITE);
        previewTitle.setTextColor(displayedText);
        previewCaption.setTextColor(colorTab == 2 ? displayedText : currentColor(2));
        sampleCircle.setBackground(Ui.outlined(chosen, 0xFFEBEFF3, 22, activity));
        sampleCircle.setTextColor(perceivedLuma(chosen) > 135 ? 0xFF151B20 : Color.WHITE);
        if (pickerHint != null) {
            if (colorTab == 2 && contrastRatio(displayedText, readableBackground) < 4.5f) {
                pickerHint.setText("Attention : ce texte risque d'être difficile à lire sur le fond choisi.");
            } else if (colorTab == 1 && readableBackground != displayedBackground) {
                pickerHint.setText("Le fond choisi est légèrement assombri pour faciliter la lecture.");
            } else {
                pickerHint.setText("Glissez dans le carré pour la nuance, puis sur la barre arc-en-ciel pour la teinte.");
            }
        }
    }

    private static float contrastRatio(int a, int b) {
        float l1 = contrastLuminance(a), l2 = contrastLuminance(b);
        return (Math.max(l1, l2) + .05f) / (Math.min(l1, l2) + .05f);
    }
    private static float contrastLuminance(int c) {
        float r = linear(Color.red(c) / 255f);
        float g = linear(Color.green(c) / 255f);
        float b = linear(Color.blue(c) / 255f);
        return .2126f * r + .7152f * g + .0722f * b;
    }
    private static float linear(float value) {
        return value <= .04045f ? value / 12.92f : (float)Math.pow((value + .055f) / 1.055f, 2.4);
    }
    private static int perceivedLuma(int value) {
        return (Color.red(value) * 299 + Color.green(value) * 587 + Color.blue(value) * 114) / 1000;
    }

    private void setCollapsed(boolean value) {
        if (collapsed == value) return;
        collapsed = value;
        if (scrollView != null) scrollView.setVisibility(collapsed ? View.GONE : View.VISIBLE);
        if (dialog != null && dialog.getWindow() != null)
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    public void dismiss() {
        if (dialog != null) dialog.dismiss();
    }
}
