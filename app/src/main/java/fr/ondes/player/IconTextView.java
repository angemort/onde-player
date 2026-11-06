package fr.ondes.player;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.widget.TextView;

/** Vector icons painted with native Android Canvas: no icon-font or external library. */
public final class IconTextView extends TextView {
    private final Paint brush = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();

    public IconTextView(Context context) { super(context); }

    @Override protected void onDraw(Canvas canvas) {
        String icon = getText().toString();
        if (!isKnown(icon)) { super.onDraw(canvas); return; }
        float size = Math.min(getWidth() - getPaddingLeft() - getPaddingRight(),
                getHeight() - getPaddingTop() - getPaddingBottom());
        if (size <= 0) return;
        size = Math.min(size, Ui.dp(getContext(), icon.equals("▶") || icon.equals("Ⅱ") ? 32 : 24));
        canvas.save();
        canvas.translate((getWidth() - size) / 2f, (getHeight() - size) / 2f);
        canvas.scale(size / 24f, size / 24f);
        brush.setColor(getCurrentTextColor());
        brush.setStrokeWidth(2.15f);
        brush.setStrokeJoin(Paint.Join.ROUND);
        brush.setStrokeCap(Paint.Cap.ROUND);
        brush.setStyle(Paint.Style.STROKE);
        path.reset();
        switch (icon) {
            case "▶":
                brush.setStyle(Paint.Style.FILL);
                path.moveTo(7, 4.4f); path.lineTo(20, 12); path.lineTo(7, 19.6f); path.close();
                canvas.drawPath(path, brush);
                break;
            case "Ⅱ":
                brush.setStyle(Paint.Style.FILL);
                canvas.drawRoundRect(6, 4, 10, 20, 1, 1, brush);
                canvas.drawRoundRect(14, 4, 18, 20, 1, 1, brush);
                break;
            case "⏭":
            case "⏮":
                // One forward triangle + a stop bar: skip track, not fast-forward.
                boolean back = icon.equals("⏮");
                canvas.save();
                if (back) { canvas.translate(24, 0); canvas.scale(-1, 1); }
                brush.setStyle(Paint.Style.FILL);
                path.moveTo(5, 4.8f); path.lineTo(17, 12);
                path.lineTo(5, 19.2f); path.close();
                canvas.drawPath(path, brush);
                canvas.drawRoundRect(18.5f, 4.4f, 21, 19.6f, 1.1f, 1.1f, brush);
                canvas.restore();
                break;
            case "♡": case "♥":
                path.moveTo(12, 20);
                path.cubicTo(8, 16.9f, 2.6f, 13, 2.6f, 8.7f);
                path.cubicTo(2.6f, 3.4f, 9.5f, 2.3f, 12, 6.5f);
                path.cubicTo(14.5f, 2.3f, 21.4f, 3.4f, 21.4f, 8.7f);
                path.cubicTo(21.4f, 13, 16, 16.9f, 12, 20); path.close();
                if (icon.equals("♥")) brush.setStyle(Paint.Style.FILL);
                canvas.drawPath(path, brush);
                break;
            case "⋮":
                brush.setStyle(Paint.Style.FILL);
                for (int y : new int[]{5, 12, 19}) canvas.drawCircle(12, y, 1.8f, brush);
                break;
            case "‹": case "⌄": case "⌃": case "›":
                if (icon.equals("⌄")) {
                    path.moveTo(5, 9); path.lineTo(12, 16); path.lineTo(19, 9);
                } else if (icon.equals("⌃")) {
                    path.moveTo(5, 15); path.lineTo(12, 8); path.lineTo(19, 15);
                } else if (icon.equals("‹")) {
                    path.moveTo(15, 5); path.lineTo(8, 12); path.lineTo(15, 19);
                } else {
                    path.moveTo(9, 5); path.lineTo(16, 12); path.lineTo(9, 19);
                }
                canvas.drawPath(path, brush);
                break;
            case "↻": case "↻₁":
                canvas.drawArc(4, 4, 20, 20, 220, 260, false, brush);
                path.moveTo(17.4f, 2.9f); path.lineTo(20.2f, 7.2f); path.lineTo(15.5f, 7.5f);
                canvas.drawPath(path, brush);
                if (icon.equals("↻₁")) {
                    brush.setStyle(Paint.Style.FILL); brush.setTextSize(10);
                    canvas.drawText("1", 10, 15.8f, brush);
                }
                break;
            case "⤨":
                path.moveTo(3, 6); path.lineTo(6, 6); path.cubicTo(11, 6, 12.6f, 18, 18, 18); path.lineTo(21, 18);
                canvas.drawPath(path, brush); path.reset();
                path.moveTo(17.5f, 14.8f); path.lineTo(21, 18); path.lineTo(17.5f, 21);
                canvas.drawPath(path, brush); path.reset();
                path.moveTo(3, 18); path.lineTo(6, 18); path.cubicTo(11, 18, 12.6f, 6, 18, 6); path.lineTo(21, 6);
                canvas.drawPath(path, brush); path.reset();
                path.moveTo(17.5f, 3); path.lineTo(21, 6); path.lineTo(17.5f, 9);
                canvas.drawPath(path, brush);
                break;
            case "☷": case "≡":
                for (int y : new int[]{6, 12, 18}) canvas.drawLine(4, y, 20, y, brush);
                break;
            case "≋":
                for (int i = 0; i < 3; i++) {
                    int y = 6 + i * 6;
                    canvas.drawLine(4, y, 20, y, brush);
                    brush.setStyle(Paint.Style.FILL);
                    canvas.drawCircle(i == 1 ? 15 : i == 0 ? 9 : 12, y, 2.5f, brush);
                    brush.setStyle(Paint.Style.STROKE);
                }
                break;
            case "♫":
                brush.setStyle(Paint.Style.FILL);
                canvas.drawOval(2.5f, 15, 9.5f, 21, brush);
                canvas.drawOval(12.5f, 12, 19.5f, 18, brush);
                brush.setStyle(Paint.Style.STROKE);
                brush.setStrokeWidth(2.2f);
                canvas.drawLine(8.6f, 18, 8.6f, 5.5f, brush);
                canvas.drawLine(18.6f, 15, 18.6f, 3.5f, brush);
                canvas.drawLine(8.6f, 5.5f, 18.6f, 3.5f, brush);
                break;
            case "＋":
                canvas.drawLine(12, 4, 12, 20, brush);
                canvas.drawLine(4, 12, 20, 12, brush);
                break;
            case "⛶":
                canvas.drawLine(4, 9, 4, 4, brush);
                canvas.drawLine(4, 4, 9, 4, brush);
                canvas.drawLine(15, 4, 20, 4, brush);
                canvas.drawLine(20, 4, 20, 9, brush);
                canvas.drawLine(20, 15, 20, 20, brush);
                canvas.drawLine(20, 20, 15, 20, brush);
                canvas.drawLine(9, 20, 4, 20, brush);
                canvas.drawLine(4, 20, 4, 15, brush);
                break;
            case "✕":
                canvas.drawLine(5, 5, 19, 19, brush);
                canvas.drawLine(19, 5, 5, 19, brush);
                break;
            case "✓":
                path.moveTo(3.5f, 12); path.lineTo(9.3f, 18); path.lineTo(20, 6);
                canvas.drawPath(path, brush);
                break;
            case "↑": case "↓":
                if (icon.equals("↓")) { canvas.translate(24, 24); canvas.rotate(180, 0, 0); }
                canvas.drawLine(12, 20, 12, 4, brush);
                path.moveTo(6, 10); path.lineTo(12, 4); path.lineTo(18, 10);
                canvas.drawPath(path, brush);
                break;
            case "✎":
                path.moveTo(5, 16); path.lineTo(16.8f, 4.2f);
                path.lineTo(19.8f, 7.2f); path.lineTo(8, 19); path.lineTo(4, 20); path.close();
                canvas.drawPath(path, brush);
                break;
            case "⇩":
                canvas.drawLine(12, 3, 12, 15, brush);
                path.moveTo(7, 10); path.lineTo(12, 15); path.lineTo(17, 10);
                canvas.drawPath(path, brush);
                path.reset(); path.moveTo(4, 17); path.lineTo(4, 20); path.lineTo(20, 20); path.lineTo(20, 17);
                canvas.drawPath(path, brush);
                break;
            case "⌕":
                canvas.drawCircle(10, 10, 6, brush);
                canvas.drawLine(14.6f, 14.6f, 21, 21, brush);
                break;
            case "◷": case "⏱":
                canvas.drawCircle(12, 12, 9, brush);
                canvas.drawLine(12, 6, 12, 12, brush);
                canvas.drawLine(12, 12, 16.5f, 14.5f, brush);
                if (icon.equals("⏱")) {
                    canvas.drawLine(12, 0.8f, 12, 3, brush);
                    canvas.drawLine(8.5f, 1, 15.5f, 1, brush);
                }
                break;
            case "ⓘ":
                canvas.drawCircle(12, 12, 9, brush);
                canvas.drawCircle(12, 7.1f, 1.1f, brush);
                canvas.drawLine(12, 11, 12, 17, brush);
                break;
            case "☆":
                for (int i = 0; i < 10; i++) {
                    float angle = (float) (Math.PI * (i * 2 - 5) / 10);
                    float radius = i % 2 == 0 ? 10f : 4.7f;
                    float x = 12 + (float)Math.cos(angle) * radius;
                    float y = 12 + (float)Math.sin(angle) * radius;
                    if (i == 0) path.moveTo(x,y); else path.lineTo(x,y);
                }
                path.close(); canvas.drawPath(path, brush);
                break;
            default: super.onDraw(canvas);
        }
        canvas.restore();
    }

    private boolean isKnown(String glyph) {
        switch (glyph) {
            case "▶": case "Ⅱ": case "⏭": case "⏮": case "♡": case "♥":
            case "⋮": case "‹": case "⌄": case "⌃": case "›": case "↻": case "↻₁":
            case "⤨": case "☷": case "≡": case "≋": case "♫": case "＋":
            case "⛶": case "✕": case "✓": case "↑": case "↓": case "✎": case "⇩":
            case "⌕": case "◷": case "⏱": case "ⓘ": case "☆": return true;
            default: return false;
        }
    }
}
