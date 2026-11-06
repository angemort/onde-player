package fr.ondes.player;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

/** Bottom sheets with drag-to-collapse, drag-to-close and an accessible expand control. */
public final class OptionSheet {
    private OptionSheet() {}

    public static final class Item {
        final String icon, title, subtitle;
        final Runnable action;
        final boolean danger;
        public Item(String icon, String title, String subtitle, Runnable action) {
            this(icon, title, subtitle, action, false);
        }
        public Item(String icon, String title, String subtitle, Runnable action, boolean danger) {
            this.icon=icon; this.title=title; this.subtitle=subtitle; this.action=action; this.danger=danger;
        }
    }
    public static List<Item> items() { return new ArrayList<>(); }
    private static int d(Activity a, float n) { return Ui.dp(a, n); }

    public static void show(Activity activity, String title, String subtitle, List<Item> actions) {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        DragSheet sheet = new DragSheet(activity, dialog);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setPadding(d(activity, 20), d(activity, 7), d(activity, 20), d(activity, 19));
        sheet.setBackground(Ui.gradient(Ui.SURFACE_ALT, Ui.BG, 28, activity));

        // A generous grip hit area; also works as an expand/collapse button.
        LinearLayout grip = Ui.vertical(activity);
        grip.setGravity(Gravity.CENTER);
        grip.setContentDescription("Réduire ou développer le volet");
        grip.setClickable(true);
        grip.setFocusable(true);
        grip.setPadding(0, d(activity, 9), 0, d(activity, 12));
        View handle = new View(activity);
        handle.setBackground(Ui.shape(Ui.OUTLINE, 4, activity));
        grip.addView(handle, Ui.lp(activity, 45, 4));
        sheet.addView(grip, Ui.lp(activity, -1, 29));

        LinearLayout top = Ui.horizontal(activity);
        top.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout names = Ui.vertical(activity);
        TextView heading = Ui.text(activity, title, 22, Ui.TEXT, true);
        heading.setMaxLines(2);
        heading.setEllipsize(android.text.TextUtils.TruncateAt.END);
        names.addView(heading);
        if (subtitle != null && !subtitle.isEmpty()) {
            names.addView(Ui.gap(activity, 5));
            TextView sub = Ui.text(activity, subtitle, 12, Ui.MUTED, false);
            sub.setMaxLines(2);
            names.addView(sub);
        }
        top.addView(names, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView toggle = Ui.icon(activity, "⌄", 41, 24, Ui.ACCENT, Ui.SURFACE_ALT);
        toggle.setContentDescription("Réduire le volet");
        LinearLayout.LayoutParams tp = Ui.lp(activity, 41, 41);
        tp.leftMargin = d(activity, 11);
        top.addView(toggle, tp);
        sheet.addView(top);

        TextView hint = Ui.text(activity, "Glisser vers le bas pour réduire ou fermer", 11, Ui.MUTED, false);
        hint.setPadding(0, d(activity, 8), 0, d(activity, 10));
        sheet.addView(hint);

        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(false);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout rows = Ui.vertical(activity);
        for (Item item : actions) {
            LinearLayout row = Ui.horizontal(activity);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(d(activity, 12), d(activity, 12), d(activity, 12), d(activity, 12));
            Ui.clickable(row, Ui.shape(Ui.SURFACE_ALT, 15, activity));
            TextView icon = Ui.icon(activity, item.icon, 43, 22,
                    item.danger ? 0xFFFFA3A6 : Ui.ACCENT, item.danger ? 0xFF4A2B32 : Ui.ACCENT_SECONDARY);
            row.addView(icon, Ui.lp(activity, 43, 43));
            icon.setClickable(false);
            LinearLayout texts = Ui.vertical(activity);
            texts.setPadding(d(activity, 13), d(activity, 1), d(activity, 7), 0);
            TextView label = Ui.text(activity, item.title, 14, item.danger ? 0xFFFFB3B8 : Ui.TEXT, true);
            label.setMaxLines(2);
            texts.addView(label);
            if (item.subtitle != null && !item.subtitle.isEmpty()) {
                texts.addView(Ui.gap(activity, 4));
                TextView description = Ui.text(activity, item.subtitle, 11, Ui.MUTED, false);
                description.setMaxLines(2);
                texts.addView(description);
            }
            row.addView(texts, new LinearLayout.LayoutParams(0, -2, 1f));
            TextView arrow = Ui.icon(activity, "›", 30, 20, Ui.MUTED, Color.TRANSPARENT);
            arrow.setClickable(false);
            row.addView(arrow, Ui.lp(activity, 30, 30));
            LinearLayout.LayoutParams rp = Ui.lp(activity, -1, -2);
            rp.bottomMargin = d(activity, 11);
            rows.addView(row, rp);
            row.setContentDescription(item.title + (item.subtitle == null ? "" : ", " + item.subtitle));
            row.setOnClickListener(v -> { dialog.dismiss(); if (item.action != null) item.action.run(); });
        }
        scroll.addView(rows);
        int maxScrollHeight = (int)(activity.getResources().getDisplayMetrics().heightPixels * .59f);
        int listHeight = Math.min(d(activity, 74) * Math.max(1, actions.size()), maxScrollHeight);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(-1, listHeight);
        sheet.addView(scroll, scrollParams);
        TextView close = Ui.text(activity, "Fermer", 13, Ui.MUTED, true);
        close.setGravity(Gravity.CENTER);
        close.setPadding(0, d(activity, 12), 0, d(activity, 5));
        sheet.addView(close);
        close.setOnClickListener(v -> dialog.dismiss());
        sheet.configure(top, scroll, close, hint, toggle);
        grip.setOnClickListener(v -> sheet.toggle());
        toggle.setOnClickListener(v -> sheet.toggle());

        dialog.setContentView(sheet);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setDimAmount(.66f);
            window.setGravity(Gravity.BOTTOM);
            window.setNavigationBarColor(Ui.BG);
        }
        dialog.show();
        if (window != null) window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sheet.setTranslationY(d(activity, 55));
        sheet.setAlpha(0f);
        sheet.animate().translationY(0f).alpha(1f).setDuration(220)
                .setInterpolator(new DecelerateInterpolator()).start();
    }

    /** Intercepts vertical gestures only when the option list is at the top.
     * Normal scrolling, tapping actions and horizontal gestures remain untouched. */
    private static final class DragSheet extends LinearLayout {
        private final Activity activity;
        private final Dialog dialog;
        private final int touchSlop;
        private View header;
        private ScrollView scroll;
        private View footer;
        private TextView hint, toggle;
        private float startX, startY;
        private long startTime;
        private boolean dragging, collapsed;
        private boolean downOnHeader;

        DragSheet(Activity activity, Dialog dialog) {
            super(activity);
            this.activity = activity;
            this.dialog = dialog;
            touchSlop = ViewConfiguration.get(activity).getScaledTouchSlop();
        }
        void configure(View header, ScrollView scroll, View footer, TextView hint, TextView toggle) {
            this.header = header;
            this.scroll = scroll;
            this.footer = footer;
            this.hint = hint;
            this.toggle = toggle;
        }
        void toggle() { setCollapsed(!collapsed); }
        private void setCollapsed(boolean value) {
            if (collapsed == value) return;
            collapsed = value;
            scroll.setVisibility(collapsed ? GONE : VISIBLE);
            footer.setVisibility(collapsed ? GONE : VISIBLE);
            hint.setText(collapsed ? "Glisser vers le haut pour ouvrir · vers le bas pour fermer" :
                    "Glisser vers le bas pour réduire ou fermer");
            toggle.setText(collapsed ? "⌃" : "⌄");
            toggle.setContentDescription(collapsed ? "Développer le volet" : "Réduire le volet");
            Window window = dialog.getWindow();
            if (window != null) {
                window.setDimAmount(collapsed ? .30f : .66f);
                window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            }
            requestLayout();
        }
        @Override public boolean onInterceptTouchEvent(MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    startX = e.getRawX(); startY = e.getRawY(); startTime = e.getEventTime();
                    downOnHeader = header != null && e.getY() <= header.getBottom() + d(activity, 33);
                    dragging = false;
                    return false;
                case MotionEvent.ACTION_MOVE:
                    float dx = e.getRawX() - startX;
                    float dy = e.getRawY() - startY;
                    if (Math.abs(dy) <= touchSlop * 1.4f || Math.abs(dy) < Math.abs(dx) * 1.3f)
                        return false;
                    boolean canPullDown = downOnHeader || scroll == null || !scroll.canScrollVertically(-1);
                    if ((dy > 0 && (collapsed || canPullDown)) || (dy < 0 && collapsed)) {
                        dragging = true;
                        if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                        return true;
                    }
                    return false;
                default: return false;
            }
        }
        @Override public boolean onTouchEvent(MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    startX = e.getRawX(); startY = e.getRawY(); startTime = e.getEventTime();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (dragging) {
                        float dy = e.getRawY() - startY;
                        // Compact panels can be pulled upwards to expand.
                        setTranslationY(dy >= 0 ? dy * .85f : collapsed ?
                                Math.max(-d(activity, 62), dy * .30f) : 0f);
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    if (dragging) {
                        dragging = false;
                        float dy = e.getRawY() - startY;
                        float durationMs = Math.max(1, e.getEventTime() - startTime);
                        float velocity = dy * 1000f / durationMs;
                        int decision = SheetGestureRules.resolve(collapsed, dy, getHeight(),
                                activity.getResources().getDisplayMetrics().density, velocity);
                        if (decision == SheetGestureRules.DISMISS) {
                            animate().translationY(getHeight() + d(activity, 36)).setDuration(180)
                                    .withEndAction(dialog::dismiss).start();
                        } else {
                            if (decision == SheetGestureRules.COLLAPSE) setCollapsed(true);
                            if (decision == SheetGestureRules.EXPAND) setCollapsed(false);
                            animate().translationY(0f).setDuration(185)
                                    .setInterpolator(new DecelerateInterpolator()).start();
                        }
                        return true;
                    }
                    return super.onTouchEvent(e);
                case MotionEvent.ACTION_CANCEL:
                    dragging = false;
                    animate().translationY(0).setDuration(180).start();
                    return true;
                default: return super.onTouchEvent(e);
            }
        }
    }
}
