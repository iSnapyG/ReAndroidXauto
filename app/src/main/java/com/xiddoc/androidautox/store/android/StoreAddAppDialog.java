package com.xiddoc.androidautox.store.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.text.InputType;
import android.util.TypedValue;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.xiddoc.androidautox.R;
import com.xiddoc.androidautox.store.CustomApps;
import com.xiddoc.androidautox.store.StoreApp;
import com.xiddoc.androidautox.store.StoreController;

import java.io.IOException;

/**
 * "Add an app from GitHub": the user types the repository, the package name and the signing
 * key, and the app joins the store like any other (installs and updates included). All the
 * checking is done by {@link CustomApps}; this is only the form. Excluded from coverage (UI).
 */
public final class StoreAddAppDialog {

    private StoreAddAppDialog() {}

    public static void show(final Activity activity) {
        final StoreController controller = StoreEnvironment.controller(activity);
        int pad = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 20,
                activity.getResources().getDisplayMetrics());
        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(pad, pad / 2, pad, 0);

        TextView intro = new TextView(activity);
        intro.setText(R.string.store_add_intro);
        layout.addView(intro);

        final EditText name = field(activity, R.string.store_add_name_hint, InputType.TYPE_CLASS_TEXT);
        final EditText repo = field(activity, R.string.store_add_repo_hint,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        final EditText pkg = field(activity, R.string.store_add_package_hint,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        final EditText signer = field(activity, R.string.store_add_signer_hint,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        final EditText pattern = field(activity, R.string.store_add_pattern_hint,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        layout.addView(name);
        layout.addView(repo);
        layout.addView(pkg);
        layout.addView(signer);
        layout.addView(pattern);

        final Spinner kind = new Spinner(activity);
        kind.setAdapter(new ArrayAdapter<String>(activity, android.R.layout.simple_spinner_dropdown_item,
                new String[]{
                        activity.getString(R.string.store_home_car),
                        activity.getString(R.string.store_home_autox),
                        activity.getString(R.string.store_add_kind_other)}));
        layout.addView(kind);

        final AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(R.string.store_add_title)
                .setView(layout)
                .setPositiveButton(R.string.store_add_button, null)
                .setNegativeButton(R.string.store_settings_close, null)
                .create();
        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface d) {
                // Set here so a mistake keeps the form open instead of closing it.
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        StoreApp.Kind chosen = kind.getSelectedItemPosition() == 0 ? StoreApp.Kind.CAR
                                : kind.getSelectedItemPosition() == 1 ? StoreApp.Kind.AUTOX
                                : StoreApp.Kind.OTHER;
                        CustomApps.Draft draft = CustomApps.githubApp(name.getText().toString(),
                                repo.getText().toString(), pkg.getText().toString(),
                                signer.getText().toString(), pattern.getText().toString(), chosen);
                        switch (draft.problem) {
                            case REPO: repo.setError(activity.getString(R.string.store_add_error_repo)); return;
                            case PACKAGE: pkg.setError(activity.getString(R.string.store_add_error_package)); return;
                            case PROTECTED: pkg.setError(activity.getString(R.string.store_add_error_protected)); return;
                            case SIGNER: signer.setError(activity.getString(R.string.store_add_error_signer)); return;
                            case PATTERN: pattern.setError(activity.getString(R.string.store_add_error_pattern)); return;
                            default: break;
                        }
                        save(activity, controller, draft.app, dialog);
                    }
                });
            }
        });
        dialog.show();
    }

    private static void save(final Activity activity, final StoreController controller,
                             final StoreApp app, final AlertDialog dialog) {
        new Thread() {
            @Override
            public void run() {
                String message;
                boolean added = false;
                try {
                    added = controller.addCustomApp(app);
                    message = added ? activity.getString(R.string.store_add_done, app.name)
                            : activity.getString(R.string.store_add_limit, CustomApps.MAX_APPS);
                } catch (IOException e) {
                    message = String.valueOf(e.getMessage());
                }
                final String shown = message;
                final boolean ok = added;
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(activity, shown, Toast.LENGTH_LONG).show();
                        if (ok) dialog.dismiss();
                    }
                });
            }
        }.start();
    }

    private static EditText field(Activity activity, int hint, int inputType) {
        EditText e = new EditText(activity);
        e.setHint(hint);
        e.setInputType(inputType);
        e.setSingleLine(true);
        return e;
    }
}
