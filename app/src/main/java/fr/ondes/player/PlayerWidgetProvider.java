package fr.ondes.player;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.view.View;
import android.widget.RemoteViews;

/** Home-screen transport control, independent from the Activity's lifecycle. */
public final class PlayerWidgetProvider extends AppWidgetProvider {
    @Override public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        SharedPreferences state = context.getSharedPreferences("onde_playback_state", Context.MODE_PRIVATE);
        String title = state.getString("current_title", "Votre musique, partout");
        String artist = state.getString("current_artist", "Ouvrir Onde Player pour commencer");
        for (int id : ids) render(context, manager, id, title, artist, false, null);
    }

    public static void refresh(Context context, Track track, boolean playing, Bitmap artwork) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = manager.getAppWidgetIds(new ComponentName(context, PlayerWidgetProvider.class));
        if (ids.length == 0) return;
        String title = track == null ? "Votre musique, partout" : track.title;
        String artist = track == null ? "Ouvrir Onde Player pour commencer" : track.artist;
        for (int id : ids) render(context, manager, id, title, artist, playing, artwork);
    }

    private static void render(Context context, AppWidgetManager manager, int widgetId,
                               String title, String artist, boolean playing, Bitmap artwork) {
        RemoteViews widget = new RemoteViews(context.getPackageName(), R.layout.widget_player);
        widget.setTextViewText(R.id.widget_title, title);
        widget.setTextViewText(R.id.widget_artist, artist);
        widget.setImageViewResource(R.id.widget_toggle,
                playing ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play);
        if (artwork != null) {
            Bitmap thumb = Bitmap.createScaledBitmap(artwork, 100, 100, true);
            widget.setImageViewBitmap(R.id.widget_cover, thumb);
        }
        else widget.setImageViewResource(R.id.widget_cover, R.drawable.ic_launcher_foreground);
        Intent open = new Intent(context, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        widget.setOnClickPendingIntent(R.id.widget_open, PendingIntent.getActivity(context, 181, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        widget.setOnClickPendingIntent(R.id.widget_toggle, control(context, PlaybackService.ACTION_PLAY_PAUSE, 182));
        widget.setOnClickPendingIntent(R.id.widget_next, control(context, PlaybackService.ACTION_NEXT, 183));
        widget.setOnClickPendingIntent(R.id.widget_previous, control(context, PlaybackService.ACTION_PREVIOUS, 184));
        manager.updateAppWidget(widgetId, widget);
    }

    private static PendingIntent control(Context context, String action, int requestCode) {
        return PendingIntent.getService(context, requestCode,
                new Intent(context, PlaybackService.class).setAction(action),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
