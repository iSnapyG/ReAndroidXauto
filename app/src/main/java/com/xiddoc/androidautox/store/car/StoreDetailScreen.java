package com.xiddoc.androidautox.store.car;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.car.app.CarContext;
import androidx.car.app.CarToast;
import androidx.car.app.model.Action;
import androidx.car.app.model.ListTemplate;
import androidx.car.app.model.MessageTemplate;
import androidx.car.app.model.OnClickListener;
import androidx.car.app.model.Template;

import com.xiddoc.androidautox.PhixitEngine;
import com.xiddoc.androidautox.R;
import com.xiddoc.androidautox.autox.AutoXSettingsStore;
import com.xiddoc.androidautox.store.ApkSource;
import com.xiddoc.androidautox.store.StoreApp;
import com.xiddoc.androidautox.store.StoreController;
import com.xiddoc.androidautox.store.StoreInstaller;
import com.xiddoc.androidautox.store.UpdateCheck;

import java.util.ArrayList;
import java.util.List;

/** One app: what it is, whether it is installed, and the install / update / remove actions. */
final class StoreDetailScreen extends StoreScreen {

    private final StoreApp app;
    private boolean confirmingRemoval;

    StoreDetailScreen(CarContext carContext, StoreController controller, StoreApp app) {
        super(carContext, controller);
        this.app = app;
    }

    @NonNull
    @Override
    public Template onGetTemplate() {
        CarContext c = getCarContext();
        if (controller.busy()) return busy(c);
        if (confirmingRemoval) return confirmRemoval(c);
        return details(c);
    }

    private Template busy(CarContext c) {
        boolean mine = controller.operation() == StoreController.Operation.REFRESHING
                || controller.busyName().equals(app.name);
        String message = mine
                ? StoreText.progress(c, controller)
                : c.getString(R.string.store_busy_other, controller.busyName());
        MessageTemplate.Builder template = new MessageTemplate.Builder(message)
                .setTitle(app.name)
                .setHeaderAction(Action.BACK);
        if (mine && controller.operation() == StoreController.Operation.INSTALLING) {
            template.addAction(new Action.Builder()
                    .setTitle(c.getString(R.string.store_action_cancel))
                    .setOnClickListener(new OnClickListener() {
                        @Override
                        public void onClick() {
                            controller.cancel();
                        }
                    })
                    .build());
        }
        return template.build();
    }

    private Template confirmRemoval(CarContext c) {
        return new MessageTemplate.Builder(c.getString(R.string.store_uninstall_confirm, app.name))
                .setTitle(app.name)
                .setHeaderAction(Action.BACK)
                .addAction(new Action.Builder()
                        .setTitle(c.getString(R.string.store_action_remove))
                        .setOnClickListener(new OnClickListener() {
                            @Override
                            public void onClick() {
                                confirmingRemoval = false;
                                controller.uninstall(app.packageName);
                            }
                        })
                        .build())
                .addAction(new Action.Builder()
                        .setTitle(c.getString(R.string.store_action_cancel))
                        .setOnClickListener(new OnClickListener() {
                            @Override
                            public void onClick() {
                                confirmingRemoval = false;
                                invalidate();
                            }
                        })
                        .build())
                .build();
    }

    private Template details(Context c) {
        long installed = controller.installedVersion(app.packageName);
        UpdateCheck.Status status = UpdateCheck.status(app.versionCode, installed);
        List<StoreRow> rows = new ArrayList<StoreRow>();

        String version = app.versionName.isEmpty() ? "" : c.getString(R.string.store_detail_version, app.versionName) + " · ";
        String origin = app.origin == StoreApp.Origin.FDROID
                ? c.getString(R.string.store_source_fdroid) : c.getString(R.string.store_source_catalog);
        String state = installed >= 0
                ? c.getString(R.string.store_detail_installed_version, installed)
                : c.getString(R.string.store_detail_not_installed);
        rows.add(StoreRow.info(app.name, app.summary, version + origin + " · " + state));

        StoreInstaller.Result last = controller.lastResult(app.packageName);
        if (last != null) rows.add(StoreRow.info(StoreText.outcome(c, last)));

        if (app.source.type == ApkSource.Type.FDROID) {
            rows.add(StoreRow.info(c.getString(R.string.store_needs_fdroid)));
        } else {
            int label = status == UpdateCheck.Status.NOT_INSTALLED ? R.string.store_action_install
                    : status == UpdateCheck.Status.CURRENT ? R.string.store_action_reinstall
                    : R.string.store_action_update;
            rows.add(StoreRow.action(c.getString(label), new Runnable() {
                @Override
                public void run() {
                    controller.install(app);
                }
            }));
        }
        if (controller.installedByStore(app.packageName)) {
            rows.add(StoreRow.action(c.getString(R.string.store_action_uninstall), new Runnable() {
                @Override
                public void run() {
                    confirmingRemoval = true;
                    invalidate();
                }
            }));
        }
        if (app.kind == StoreApp.Kind.AUTOX && installed >= 0) {
            rows.add(StoreRow.action(c.getString(R.string.store_action_use_autox), new Runnable() {
                @Override
                public void run() {
                    useInAutoX();
                }
            }));
        }
        if (app.kind == StoreApp.Kind.CAR) {
            rows.add(StoreRow.info(c.getString(R.string.store_unknown_sources_hint)));
        }
        return new ListTemplate.Builder()
                .setSingleList(items(rows, ""))
                .setTitle(app.name)
                .setHeaderAction(Action.BACK)
                .build();
    }

    private void useInAutoX() {
        CarContext c = getCarContext();
        AutoXSettingsStore.setTargetPackage(
                c.getSharedPreferences(PhixitEngine.PREFS, Context.MODE_PRIVATE), app.packageName);
        CarToast.makeText(c, c.getString(R.string.store_autox_selected, app.name), CarToast.LENGTH_LONG).show();
    }
}
