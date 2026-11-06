package fr.ondes.player;

import android.app.Activity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.animation.DecelerateInterpolator;
import android.widget.AbsListView;
import android.widget.FrameLayout;
import android.widget.ListView;

/**
 * Swipe-direction driven, reversible header for the titles screen.
 *
 * Android ListView can judder if its measured viewport changes mid-gesture.
 * We collect raw (screen-space) touch movement and commit exactly one size
 * change after ACTION_UP. An animation compensates the resulting list
 * displacement so the first visible track does not abruptly jump.
 */
public final class HeaderMotionController {
    public interface Delegate {
        void applyImmediate(boolean compact);
        boolean isCurrent(ListView list);
    }

    private static final long FADE_OUT_MS = 165L;
    private static final long SLIDE_MS = 235L;
    private final Activity activity;
    private final ListView list;
    private final FrameLayout listFrame;
    private final View[] chrome;
    private final Delegate delegate;
    private final float threshold;
    private int scrollState = AbsListView.OnScrollListener.SCROLL_STATE_IDLE;
    private float lastRawY;
    private float gestureDistance;
    private boolean touching;
    private boolean compact;
    private boolean animating;
    private boolean disposed;
    private Boolean pendingCompact;
    private int epoch;

    public HeaderMotionController(Activity activity, ListView list, FrameLayout listFrame,
                                  View[] chrome, boolean initialCompact, Delegate delegate) {
        this.activity = activity;
        this.list = list;
        this.listFrame = listFrame;
        this.chrome = chrome;
        this.delegate = delegate;
        this.compact = initialCompact;
        threshold = Ui.dp(activity, 46);
    }

    public boolean onTouch(MotionEvent event) {
        if (disposed || !delegate.isCurrent(list)) return false;
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            touching = true;
            lastRawY = event.getRawY();
            gestureDistance = 0f;
            return false;
        }
        if (action == MotionEvent.ACTION_MOVE && touching) {
            float current = event.getRawY();
            gestureDistance = HeaderMotionRules.accumulate(gestureDistance,
                    current - lastRawY, threshold * 2.5f);
            lastRawY = current;
            return false;
        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            if (!touching) return false;
            touching = false;
            if (action == MotionEvent.ACTION_UP) {
                int intent = HeaderMotionRules.intent(gestureDistance, threshold);
                if (intent == HeaderMotionRules.SHOW) request(false);
                else if (intent == HeaderMotionRules.HIDE && list.canScrollVertically(-1))
                    request(true);
                else if (atTop()) request(false);
            }
            gestureDistance = 0f;
        }
        return false; // Never consume taps, swipes on tracks or Android scrolling.
    }

    public void onScrollStateChanged(int state) {
        scrollState = state;
        if (disposed || touching || !delegate.isCurrent(list)) return;
        if (state == AbsListView.OnScrollListener.SCROLL_STATE_IDLE && atTop() && compact) {
            request(false);
        }
    }

    private boolean atTop() {
        return list.getFirstVisiblePosition() == 0
                && (list.getChildCount() == 0
                    || list.getChildAt(0).getTop() >= list.getPaddingTop() - Ui.dp(activity, 6));
    }

    public void request(boolean wantCompact) {
        if (disposed || !delegate.isCurrent(list)) return;
        if (animating) {
            pendingCompact = wantCompact;
            return;
        }
        if (compact == wantCompact) return;
        if (touching) {
            pendingCompact = wantCompact;
            return;
        }
        start(wantCompact);
    }

    private boolean valid(int requestEpoch) {
        return !disposed && epoch == requestEpoch
                && !activity.isFinishing() && !activity.isDestroyed()
                && delegate.isCurrent(list);
    }

    private void start(boolean hide) {
        animating = true;
        int animationEpoch = ++epoch;
        if (hide) {
            // Fade/slide visual chrome first, without resizing the touched ListView.
            for (View item : chrome) {
                if (item == null || item.getVisibility() != View.VISIBLE) continue;
                item.animate().cancel();
                item.animate().alpha(0f).translationY(-Ui.dp(activity, 18))
                        .setDuration(FADE_OUT_MS)
                        .setInterpolator(new DecelerateInterpolator()).start();
            }
            listFrame.postDelayed(() -> {
                if (!valid(animationEpoch)) return;
                commitLayout(true, animationEpoch);
            }, FADE_OUT_MS + 8L);
        } else {
            // Reserve space once, but offset the entire list before drawing so
            // expanding the header doesn't make tracks jump down a full header.
            commitLayout(false, animationEpoch);
        }
    }

    private void commitLayout(boolean hide, int animationEpoch) {
        if (!valid(animationEpoch)) return;
        final int[] oldListPosition = new int[2];
        listFrame.getLocationOnScreen(oldListPosition);
        compact = hide;
        delegate.applyImmediate(hide); // One measured-layout update, never per animation frame.

        if (!hide) {
            for (View item : chrome) {
                if (item == null || item.getVisibility() != View.VISIBLE) continue;
                item.animate().cancel();
                item.setAlpha(0f);
                item.setTranslationY(-Ui.dp(activity, 22));
            }
        } else {
            for (View item : chrome) {
                if (item == null) continue;
                item.animate().cancel();
                item.setTranslationY(0f);
                item.setAlpha(1f);
            }
        }

        ViewTreeObserver observer = listFrame.getViewTreeObserver();
        observer.addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override public boolean onPreDraw() {
                if (observer.isAlive()) observer.removeOnPreDrawListener(this);
                if (!valid(animationEpoch)) return true;
                int[] newListPosition = new int[2];
                listFrame.getLocationOnScreen(newListPosition);
                // Keep music rows at their previous screen coordinate at the
                // moment the header reclaims/releases its vertical space.
                listFrame.setTranslationY(oldListPosition[1] - newListPosition[1]);
                listFrame.animate().cancel();
                listFrame.animate().translationY(0f).setDuration(SLIDE_MS)
                        .setInterpolator(new DecelerateInterpolator())
                        .withEndAction(() -> finish(animationEpoch)).start();
                if (!hide) {
                    for (View item : chrome) {
                        if (item == null || item.getVisibility() != View.VISIBLE) continue;
                        item.animate().alpha(1f).translationY(0f).setDuration(SLIDE_MS)
                                .setInterpolator(new DecelerateInterpolator()).start();
                    }
                }
                return true;
            }
        });
        listFrame.invalidate();
    }

    private void finish(int animationEpoch) {
        if (!valid(animationEpoch)) return;
        animating = false;
        listFrame.setTranslationY(0f);
        Boolean next = pendingCompact;
        pendingCompact = null;
        if (next != null && next != compact) request(next);
    }

    public void dispose() {
        disposed = true;
        epoch++;
        animating = false;
        pendingCompact = null;
        listFrame.animate().cancel();
        listFrame.setTranslationY(0f);
        for (View item : chrome) {
            if (item == null) continue;
            item.animate().cancel();
            item.setTranslationY(0f);
            item.setAlpha(1f);
        }
    }
}
