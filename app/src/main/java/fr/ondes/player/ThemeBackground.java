package fr.ondes.player;

import android.content.Context;
import android.graphics.Bitmap;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;

/** The background picture remains a soft, readable layer behind all content. */
public final class ThemeBackground {
    private ThemeBackground() {}
    public static void attach(Context context, FrameLayout parent) {
        ThemeGradient.apply(context, parent);
        if (!"image".equals(ThemeStyle.mode(context, "background"))) return;
        Bitmap bitmap = ThemeStyle.picture(context, "background");
        if (bitmap == null) return;
        ImageView image = new ImageView(context);
        image.setImageBitmap(bitmap);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setAlpha(.24f);
        image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        parent.addView(image, 0, new FrameLayout.LayoutParams(-1, -1));
    }
}
