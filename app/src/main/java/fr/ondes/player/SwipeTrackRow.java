package fr.ondes.player;

import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** A swipeable music row. Touch, long press and horizontal swipes are mutually exclusive.
 * The horizontal action commits only when the gesture ends very close to the row edge. */
public final class SwipeTrackRow extends FrameLayout {
    private final LinearLayout front;
    private final View playingStripe;
    private boolean selectedRow;
    private int playingState = PlayingTrackRules.OTHER;
    private final LinearLayout nextAction;
    private final LinearLayout queueAction;
    private final TextView nextDescription;
    private final TextView queueDescription;
    private final int slop;
    private final int longPressDelay;
    private Runnable onPlay, onSelect, onNext, onQueue;
    private Runnable pendingLongPress;
    private float startX, startY;
    private boolean swiping, vertical, longPressed, tracking;
    private boolean swipeEnabled = true;
    private float slide;

    public SwipeTrackRow(Context context) {
        super(context);
        slop = ViewConfiguration.get(context).getScaledTouchSlop();
        longPressDelay = ViewConfiguration.getLongPressTimeout();
        setClipChildren(true);
        setClipToPadding(true);
        setClickable(true);
        setFocusable(true);
        setBackground(Ui.shape(Ui.BG, 14, context));

        FrameLayout underlay = new FrameLayout(context);
        underlay.setBackground(Ui.shape(Ui.SURFACE_ALT, 13, context));
        addView(underlay, new FrameLayout.LayoutParams(-1, -1));

        nextAction = action(context, "⏭", "LIRE ENSUITE", Ui.ACCENT_SECONDARY);
        nextDescription = (TextView) nextAction.getChildAt(1);
        FrameLayout.LayoutParams lpNext = new FrameLayout.LayoutParams(-2, -1, Gravity.START);
        underlay.addView(nextAction, lpNext);

        queueAction = action(context, "☷", "AJOUTER À LA FILE", Ui.SELECTION);
        queueDescription = (TextView) queueAction.getChildAt(1);
        FrameLayout.LayoutParams lpQueue = new FrameLayout.LayoutParams(-2, -1, Gravity.END);
        underlay.addView(queueAction, lpQueue);
        nextAction.setVisibility(INVISIBLE);
        queueAction.setVisibility(INVISIBLE);

        front = Ui.horizontal(context);
        front.setPadding(Ui.dp(context, 8), Ui.dp(context, 7), Ui.dp(context, 5), Ui.dp(context, 7));
        front.setBackground(Ui.shape(Ui.SURFACE, 13, context));
        addView(front, new FrameLayout.LayoutParams(-1, -2));

        // Thin accent stripe sits above the row without consuming any
        // horizontal or vertical space. It does not intercept row swipes.
        playingStripe = new View(context);
        playingStripe.setClickable(false);
        playingStripe.setFocusable(false);
        playingStripe.setBackground(Ui.shape(Ui.ACCENT, 3, context));
        playingStripe.setVisibility(View.GONE);
        FrameLayout.LayoutParams stripeParams = new FrameLayout.LayoutParams(
                Ui.dp(context, 3), Ui.dp(context, 39), Gravity.START | Gravity.CENTER_VERTICAL);
        stripeParams.leftMargin = Ui.dp(context, 2);
        addView(playingStripe, stripeParams);
    }

    private static LinearLayout action(Context c, String glyph, String label, int background) {
        LinearLayout action = Ui.horizontal(c);
        action.setGravity(Gravity.CENTER_VERTICAL);
        action.setPadding(Ui.dp(c, 17), 0, Ui.dp(c, 17), 0);
        action.setBackground(Ui.shape(background, 13, c));
        TextView icon = Ui.icon(c, glyph, 32, 19, Ui.TEXT, Color.TRANSPARENT);
        icon.setClickable(false);
        action.addView(icon, Ui.lp(c, 32, 38));
        TextView title = Ui.text(c, label, 11, Ui.TEXT, true);
        title.setPadding(Ui.dp(c, 5), 0, 0, 0);
        title.setSingleLine(true);
        action.addView(title);
        return action;
    }

    public LinearLayout content() { return front; }
    public void configure(Runnable play, Runnable select, Runnable next, Runnable enqueue) {
        onPlay = play;
        onSelect = select;
        onNext = next;
        onQueue = enqueue;
    }
    private void renderRowBackground() {
        int shade = selectedRow ? Ui.SELECTION
                : playingState == PlayingTrackRules.PLAYING ? Ui.ACCENT_SECONDARY
                : playingState == PlayingTrackRules.PAUSED ? Ui.ACCENT_SUBTLE : Ui.SURFACE;
        front.setBackground(Ui.shape(shade, 13, getContext()));
        playingStripe.setVisibility(playingState == PlayingTrackRules.OTHER ? View.GONE : View.VISIBLE);
        playingStripe.setAlpha(playingState == PlayingTrackRules.PAUSED ? .5f : 1f);
    }
    public void setPlaybackVisual(int newState) {
        if (playingState == newState) return;
        playingState = newState;
        renderRowBackground();
    }
    public void setSelectionVisual(boolean selected) {
        if (selectedRow == selected) return;
        selectedRow = selected;
        renderRowBackground();
    }
    public void setSwipeEnabled(boolean enabled) { swipeEnabled = enabled; }
    public void resetPosition() {
        stopPending();
        front.animate().cancel();
        front.setTranslationX(0);
        nextAction.setVisibility(INVISIBLE);
        queueAction.setVisibility(INVISIBLE);
        slide = 0;
        tracking = false;
        swiping = false;
        vertical = false;
        longPressed = false;
    }
    private void stopPending() {
        if (pendingLongPress != null) {
            removeCallbacks(pendingLongPress);
            pendingLongPress = null;
        }
    }
    private float maxDistance() {
        return Math.max(Ui.dp(getContext(), 85), getWidth() - Ui.dp(getContext(), 28));
    }
    private void showSwipe(float distance) {
        float max = maxDistance();
        slide = Math.max(-max, Math.min(max, distance));
        float fraction = Math.min(1f, Math.abs(slide) / max);
        boolean ready = fraction >= .96f;
        nextAction.setVisibility(slide > 0 ? VISIBLE : INVISIBLE);
        queueAction.setVisibility(slide < 0 ? VISIBLE : INVISIBLE);
        nextDescription.setText(ready && slide > 0 ? "RELÂCHER POUR AJOUTER" : "LIRE ENSUITE");
        queueDescription.setText(ready && slide < 0 ? "RELÂCHER POUR AJOUTER" : "AJOUTER À LA FILE");
        nextAction.setAlpha(slide > 0 ? .55f + .45f * fraction : 0f);
        queueAction.setAlpha(slide < 0 ? .55f + .45f * fraction : 0f);
        front.setTranslationX(slide);
    }
    private void release() {
        front.animate().translationX(0).setDuration(190).withEndAction(() -> {
            nextAction.setVisibility(INVISIBLE);
            queueAction.setVisibility(INVISIBLE);
        }).start();
        slide = 0;
    }
    @Override public boolean performClick() {
        super.performClick();
        if (onPlay != null) onPlay.run();
        return true;
    }
    @Override public boolean performLongClick() {
        boolean dispatched = super.performLongClick();
        if (onSelect != null) { onSelect.run(); return true; }
        return dispatched;
    }
    @Override public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                front.animate().cancel();
                front.setTranslationX(0);
                nextAction.setVisibility(INVISIBLE);
                queueAction.setVisibility(INVISIBLE);
                startX = event.getX(); startY = event.getY();
                tracking = true; swiping = false; vertical = false; longPressed = false;
                stopPending();
                pendingLongPress = () -> {
                    pendingLongPress = null;
                    if (tracking && !swiping && !vertical) {
                        longPressed = true;
                        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                        performLongClick();
                    }
                };
                postDelayed(pendingLongPress, longPressDelay);
                return true;
            case MotionEvent.ACTION_MOVE:
                if (!tracking) return true;
                float dx = event.getX() - startX;
                float dy = event.getY() - startY;
                if (!swiping && (Math.abs(dx) > slop * 1.5f || Math.abs(dy) > slop * 1.5f)) {
                    stopPending();
                    if (Math.abs(dy) > Math.abs(dx)) vertical = true;
                    else if (swipeEnabled && Math.abs(dx) > Math.abs(dy) * 1.25f) {
                        swiping = true;
                        if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                    }
                }
                if (swiping) showSwipe(dx);
                return true;
            case MotionEvent.ACTION_UP:
                stopPending();
                if (!tracking) return true;
                tracking = false;
                if (swiping) {
                    boolean commit = GestureRules.shouldCommit(slide, maxDistance());
                    int direction = slide > 0 ? 1 : -1;
                    release();
                    if (commit) {
                        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                        if (direction > 0 && onNext != null) onNext.run();
                        else if (direction < 0 && onQueue != null) onQueue.run();
                    }
                } else if (!longPressed && !vertical &&
                        Math.abs(event.getX() - startX) < slop * 1.5f &&
                        Math.abs(event.getY() - startY) < slop * 1.5f) performClick();
                return true;
            case MotionEvent.ACTION_CANCEL:
                stopPending(); tracking = false;
                if (swiping) release();
                swiping = false;
                return true;
            default: return true;
        }
    }
}
