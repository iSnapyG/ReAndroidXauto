package com.xiddoc.androidautox.store.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.text.InputType;
import android.util.TypedValue;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.xiddoc.androidautox.R;
import com.xiddoc.androidautox.store.CatalogLoader;
import com.xiddoc.androidautox.store.StoreController;
import com.xiddoc.androidautox.store.StoreSettings;

import java.io.IOException;

/**
 * The phone-side settings for the store: whether to use the F-Droid catalog, an optional
 * catalog address, refresh and clear. Built in code so it needs no layout of its own.
 * Excluded from coverage (UI).
 */
public final class StoreSettingsDialog {

    private StoreSettingsDialog() {}

    public static void show(final Activity activity) {
        final StoreController controller = StoreEnvironment.controller(activity);
        final StoreSettings settings = StoreEnvironment.settings(activity);
        controller.reloadInBackground();

        final int pad = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 20,
                activity.getResources().getDisplayMetrics());
        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(pad, pad / 2, pad, 0);

        TextView intro = new TextView(activity);
        intro.setText(R.string.store_settings_intro);
        layout.addView(intro);

        final CheckBox fdroid = new CheckBox(activity);
        fdroid.setText(R.string.store_settings_fdroid);
        fdroid.setChecked(settings.fdroidEnabled());
        layout.addView(fdroid);

        final EditText url = new EditText(activity);
        url.setHint(R.string.store_settings_url_hint);
        url.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        url.setText(settings.remoteUrl());
        layout.addView(url);

        final TextView status = new TextView(activity);
        status.setPadding(0, pad / 2, 0, 0);
        layout.addView(status);

        final Runnable showCounts = new Runnable() {
            @Override
            public void run() {
                status.setText(activity.getString(R.string.store_settings_status,
                        controller.apps().size(), controller.trackedVersions().size()));
            }
        };
        showCounts.run();

        final StoreController.Observer observer = new StoreController.Observer() {
            @Override
            public void onChanged() {
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (controller.busy()) {
                            status.setText(R.string.store_settings_refreshing);
                            return;
                        }
                        CatalogLoader.RefreshResult r = controller.lastRefresh();
                        if (r == null) {
                            showCounts.run();
                        } else if (r.remote == CatalogLoader.Step.FAILED || r.fdroid == CatalogLoader.Step.FAILED) {
                            status.setText(R.string.store_settings_refresh_problem);
                        } else {
                            status.setText(activity.getString(R.string.store_settings_refreshed) + " "
                                    + activity.getString(R.string.store_settings_status,
                                    controller.apps().size(), controller.trackedVersions().size()));
                        }
                    }
                });
            }
        };
        controller.addObserver(observer);

        final AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(R.string.store_settings_title)
                .setView(layout)
                .setPositiveButton(R.string.store_settings_save_refresh, null)
                .setNeutralButton(R.string.store_settings_clear, null)
                .setNegativeButton(R.string.store_settings_close, null)
                .create();
        dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface d) {
                controller.removeObserver(observer);
            }
        });
        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface d) {
                // Set here so a bad address keeps the dialog open instead of closing it.
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (!settings.setRemoteUrl(url.getText().toString())) {
                            url.setError(activity.getString(R.string.store_settings_url_invalid));
                            return;
                        }
                        settings.setFdroidEnabled(fdroid.isChecked());
                        controller.refresh(false); // reloads the list when it is done
                    }
                });
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        try {
                            controller.clearCaches();
                            status.setText(R.string.store_settings_cleared);
                        } catch (IOException e) {
                            Toast.makeText(activity, e.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    }
                });
            }
        });
        dialog.show();
    }
}
