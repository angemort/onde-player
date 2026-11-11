package fr.ondes.player;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import java.text.DateFormat;
import java.util.Date;
import java.util.concurrent.Executor;

/** Volet de mises à jour lisible sur téléphone, sans saisie d'URL ni installation silencieuse. */
public final class UpdatePanel {
    private final Activity activity;
    private final Executor executor;
    private final String feedUrl;
    private final Dialog dialog;
    private final SharedPreferences prefs;
    private final int installedCode;
    private final String installedName;
    private TextView latestVersion;
    private TextView checkedAt;
    private TextView status;
    private TextView description;
    private TextView checkButton;
    private TextView downloadButton;
    private TextView githubButton;
    private ProgressBar spinner;
    private boolean checking;
    private UpdateChecker.Release available;

    private UpdatePanel(Activity activity, Executor executor, String feedUrl) throws Exception {
        this.activity = activity;
        this.executor = executor;
        this.feedUrl = feedUrl;
        this.prefs = activity.getSharedPreferences("onde_settings", Context.MODE_PRIVATE);
        PackageInfo packageInfo = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0);
        installedCode = packageInfo.versionCode;
        installedName = packageInfo.versionName == null ? "inconnue" : packageInfo.versionName;
        dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
    }

    public static void show(Activity activity, Executor executor, String feedUrl) {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        try { new UpdatePanel(activity, executor, feedUrl).display(); }
        catch (Exception error) {
            android.widget.Toast.makeText(activity, "Impossible d'ouvrir les mises à jour", android.widget.Toast.LENGTH_LONG).show();
        }
    }

    private int dp(float n) { return Ui.dp(activity, n); }

    private TextView label(String value, int size, int color, boolean bold) {
        return Ui.text(activity, value, size, color, bold);
    }

    private void display() {
        LinearLayout root = Ui.vertical(activity);
        root.setPadding(dp(22), dp(10), dp(22), dp(23));
        root.setBackground(Ui.gradient(Ui.SURFACE_ALT, Ui.BG, 28, activity));

        // Le volet reste confortable d'utilisation sur petit écran.
        LinearLayout grip = Ui.vertical(activity);
        grip.setGravity(Gravity.CENTER);
        grip.setPadding(0, dp(4), 0, dp(14));
        View handle = new View(activity);
        handle.setBackground(Ui.shape(Ui.MUTED, 5, activity));
        grip.addView(handle, Ui.lp(activity, 43, 4));
        root.addView(grip);
        final float[] down = new float[1];
        grip.setOnTouchListener((v, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                down[0] = event.getRawY();
                return true;
            }
            if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                if (event.getRawY() - down[0] > dp(65)) dialog.dismiss();
                return true;
            }
            return true;
        });

        LinearLayout header = Ui.horizontal(activity);
        TextView symbol = Ui.icon(activity, "↻", 46, 27, Ui.ACCENT, Ui.SURFACE);
        header.addView(symbol, Ui.lp(activity, 46, 46));
        LinearLayout titleStack = Ui.vertical(activity);
        titleStack.setPadding(dp(12), 0, 0, 0);
        titleStack.addView(label("Mises à jour", 22, Ui.TEXT, true));
        titleStack.addView(Ui.gap(activity, 4));
        titleStack.addView(label("Onde Player", 12, Ui.MUTED, false));
        header.addView(titleStack, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView close = Ui.icon(activity, "×", 44, 25, Ui.MUTED, Ui.SURFACE);
        close.setContentDescription("Fermer les mises à jour");
        close.setOnClickListener(v -> dialog.dismiss());
        header.addView(close, Ui.lp(activity, 44, 44));
        root.addView(header);
        root.addView(Ui.gap(activity, 19));

        LinearLayout versions = Ui.vertical(activity);
        versions.setPadding(dp(17), dp(16), dp(17), dp(16));
        versions.setBackground(Ui.shape(Ui.SURFACE, 17, activity));
        LinearLayout installedRow = Ui.horizontal(activity);
        installedRow.addView(label("VERSION INSTALLÉE", 11, Ui.MUTED, true),
                new LinearLayout.LayoutParams(0, -2, 1f));
        installedRow.addView(label("v" + installedName, 16, Ui.TEXT, true));
        versions.addView(installedRow);
        versions.addView(Ui.gap(activity, 13));
        LinearLayout onlineRow = Ui.horizontal(activity);
        onlineRow.addView(label("VERSION EN LIGNE", 11, Ui.MUTED, true),
                new LinearLayout.LayoutParams(0, -2, 1f));
        latestVersion = label("Non vérifiée", 14, Ui.MUTED, true);
        onlineRow.addView(latestVersion);
        versions.addView(onlineRow);
        versions.addView(Ui.gap(activity, 13));
        checkedAt = label(lastCheckLabel(), 11, Ui.MUTED, false);
        versions.addView(checkedAt);
        root.addView(versions);
        root.addView(Ui.gap(activity, 16));

        LinearLayout result = Ui.vertical(activity);
        result.setPadding(dp(17), dp(15), dp(17), dp(17));
        result.setBackground(Ui.shape(Ui.SURFACE, 17, activity));
        LinearLayout statusRow = Ui.horizontal(activity);
        spinner = new ProgressBar(activity, null, android.R.attr.progressBarStyleSmall);
        spinner.setIndeterminate(true);
        spinner.setIndeterminateTintList(ColorStateList.valueOf(Ui.ACCENT));
        spinner.setVisibility(View.GONE);
        LinearLayout.LayoutParams spinParams = Ui.lp(activity, 25, 25);
        spinParams.rightMargin = dp(10);
        statusRow.addView(spinner, spinParams);
        status = label("Prêt à vérifier", 16, Ui.TEXT, true);
        statusRow.addView(status, new LinearLayout.LayoutParams(0, -2, 1f));
        result.addView(statusRow);
        result.addView(Ui.gap(activity, 8));
        description = label("Touchez le bouton ci-dessous pour rechercher une nouvelle version sur GitHub.", 13, Ui.MUTED, false);
        description.setLineSpacing(dp(2), 1f);
        result.addView(description);
        root.addView(result);
        root.addView(Ui.gap(activity, 19));

        checkButton = label("Vérifier maintenant", 16, Ui.BG, true);
        checkButton.setGravity(Gravity.CENTER);
        checkButton.setMinHeight(dp(56));
        checkButton.setContentDescription("Vérifier les mises à jour d'Onde Player");
        Ui.clickable(checkButton, Ui.shape(Ui.ACCENT, 16, activity));
        checkButton.setOnClickListener(v -> verify());
        root.addView(checkButton, Ui.lp(activity, -1, 56));

        downloadButton = label("Télécharger la nouvelle version", 15, Ui.TEXT, true);
        downloadButton.setGravity(Gravity.CENTER);
        downloadButton.setMinHeight(dp(52));
        Ui.clickable(downloadButton, Ui.outlined(Ui.SURFACE, Ui.ACCENT, 15, activity));
        LinearLayout.LayoutParams downloadParams = Ui.lp(activity, -1, 52);
        downloadParams.topMargin = dp(11);
        root.addView(downloadButton, downloadParams);
        downloadButton.setVisibility(View.GONE);
        downloadButton.setOnClickListener(v -> download());

        root.addView(Ui.gap(activity, 12));
        githubButton = label("Voir les versions sur GitHub", 13, Ui.ACCENT, true);
        githubButton.setGravity(Gravity.CENTER);
        githubButton.setContentDescription("Ouvrir la page officielle des versions d'Onde Player");
        githubButton.setMinHeight(dp(43));
        githubButton.setOnClickListener(v -> openUrl("https://github.com/angemort/onde-player/releases/latest"));
        root.addView(githubButton, Ui.lp(activity, -1, 43));

        root.addView(Ui.gap(activity, 12));
        TextView footer = label("Vérification automatique au lancement, au maximum une fois toutes les 24 heures. Installation confirmée par Android.", 11, Ui.MUTED, false);
        footer.setGravity(Gravity.CENTER);
        footer.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        footer.setLineSpacing(dp(2), 1f);
        root.addView(footer);

        dialog.setContentView(root);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setDimAmount(.67f);
            window.setGravity(Gravity.BOTTOM);
            window.setNavigationBarColor(Ui.BG);
        }
        dialog.show();
        if (window != null) window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        // Opening this screen means the user wants an immediate check.
        // The once-per-day limit is only for startup background checks.
        verify();
    }

    private String lastCheckLabel() {
        long previous = prefs.getLong("updates_last_success_ms", 0L);
        if (previous <= 0L) return "Aucune vérification manuelle enregistrée";
        return "Dernière vérification : " + DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                .format(new Date(previous));
    }

    private void verify() {
        if (checking || !dialog.isShowing()) return;
        checking = true;
        available = null;
        spinner.setVisibility(View.VISIBLE);
        status.setText("Vérification en cours…");
        description.setText("Connexion sécurisée à GitHub. Cela peut prendre quelques secondes.");
        checkButton.setEnabled(false);
        checkButton.setAlpha(.55f);
        checkButton.setText("Recherche en cours…");
        latestVersion.setText("Recherche…");
        downloadButton.setVisibility(View.GONE);

        executor.execute(() -> {
            UpdateChecker.Release release = null;
            Exception failure = null;
            try { release = UpdateChecker.fetch(feedUrl); }
            catch (Exception e) { failure = e; }
            final UpdateChecker.Release found = release;
            final Exception error = failure;
            activity.runOnUiThread(() -> {
                if (!dialog.isShowing() || activity.isFinishing() || activity.isDestroyed()) return;
                checking = false;
                spinner.setVisibility(View.GONE);
                checkButton.setEnabled(true);
                checkButton.setAlpha(1f);
                checkButton.setText("Revérifier");
                if (error != null) {
                    status.setText("Vérification impossible");
                    latestVersion.setText("Indisponible");
                    description.setText("Connexion ou lecture du manifeste impossible : "
                            + error.getClass().getSimpleName()
                            + ". Réessayez ou ouvrez la dernière Release sur GitHub ci-dessous.");
                    return;
                }
                long when = System.currentTimeMillis();
                prefs.edit().putLong("updates_last_success_ms", when)
                        .putLong("updates_last_check_ms", when).apply();
                checkedAt.setText(lastCheckLabel());
                latestVersion.setText("v" + found.versionName);
                if (found.versionCode <= installedCode) {
                    status.setText("Votre application est à jour");
                    description.setText("Vous utilisez la version " + installedName
                            + " (code " + installedCode + "). La dernière version publiée est "
                            + found.versionName + " (code " + found.versionCode + ").");
                } else {
                    available = found;
                    status.setText("Nouvelle version disponible");
                    String notes = TextUtils.isEmpty(found.notes) ? "Corrections et améliorations." : found.notes;
                    if (notes.length() > 550) notes = notes.substring(0, 550) + "…";
                    description.setText("Version " + found.versionName + " disponible.\n\n" + notes);
                    downloadButton.setText("Télécharger la version " + found.versionName);
                    downloadButton.setVisibility(View.VISIBLE);
                }
            });
        });
    }

    private void openUrl(String url) {
        if (!UpdateChecker.https(url)) return;
        try {
            Intent open = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            open.addCategory(Intent.CATEGORY_BROWSABLE);
            activity.startActivity(open);
        } catch (Exception e) {
            status.setText("Ouverture impossible");
            description.setText("Impossible d'ouvrir GitHub : vérifiez qu'un navigateur est installé.");
        }
    }
    private void download() {
        if (available != null) openUrl(available.apkUrl);
    }
}
