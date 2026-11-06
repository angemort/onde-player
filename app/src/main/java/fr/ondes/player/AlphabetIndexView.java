package fr.ondes.player;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

/** Narrow A-Z fast scroll rail, drawn natively and usable with a single finger. */
public final class AlphabetIndexView extends View {
    public interface Listener { void onLetter(char letter); }
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF pill = new RectF();
    private Listener listener;
    private int current = -1;
    private int highlighted = -1;
    private boolean dragging;

    public AlphabetIndexView(Context context) {
        super(context);
        setContentDescription("Index alphabétique des titres, glisser pour chercher une lettre");
        setClickable(true);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    }

    public void setListener(Listener listener) { this.listener = listener; }
    public void highlight(char letter) {
        int index = AlphabetRules.LETTERS.indexOf(letter);
        if (!dragging && highlighted != index) { highlighted = index; invalidate(); }
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (getWidth() <= 0 || getHeight() <= 0) return;
        float height = getHeight();
        float lineHeight = height / AlphabetRules.LETTERS.length();
        float centerX = getWidth() / 2f;
        pill.set(2, 1, getWidth() - 2, height - 1);
        paint.setColor(dragging ? 0xE82D403A : 0x80303943);
        canvas.drawRoundRect(pill, getWidth() / 2f, getWidth() / 2f, paint);
        paint.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD));
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(Math.min(Ui.dp(getContext(), 11), lineHeight * .77f));
        Paint.FontMetrics fm = paint.getFontMetrics();
        for (int i = 0; i < AlphabetRules.LETTERS.length(); i++) {
            float cy = lineHeight * (i + .5f);
            if (i == (dragging ? current : highlighted)) {
                paint.setColor(Ui.ACCENT);
                canvas.drawCircle(centerX, cy, Math.min(lineHeight * .48f, getWidth() * .42f), paint);
            }
            paint.setColor(i == (dragging ? current : highlighted) ? Ui.BG : Ui.TEXT);
            canvas.drawText(String.valueOf(AlphabetRules.LETTERS.charAt(i)), centerX,
                    cy - (fm.ascent + fm.descent) / 2f, paint);
        }
    }

    private void moveTo(float y) {
        int index = Math.max(0, Math.min(AlphabetRules.LETTERS.length() - 1,
                (int) (y * AlphabetRules.LETTERS.length() / Math.max(1, getHeight()))));
        if (index == current) return;
        current = index;
        invalidate();
        if (listener != null) listener.onLetter(AlphabetRules.LETTERS.charAt(index));
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                dragging = true;
                current = -1;
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                moveTo(event.getY());
                return true;
            case MotionEvent.ACTION_MOVE:
                if (dragging) moveTo(event.getY());
                return true;
            case MotionEvent.ACTION_UP:
                if (dragging) moveTo(event.getY());
                dragging = false; highlighted = current;
                current = -1;
                invalidate();
                performClick();
                return true;
            case MotionEvent.ACTION_CANCEL:
                dragging = false; current = -1; invalidate(); return true;
            default: return super.onTouchEvent(event);
        }
    }
    @Override public boolean performClick() { super.performClick(); return true; }
}
