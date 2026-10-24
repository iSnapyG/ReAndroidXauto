package com.xiddoc.androidautox.store.android;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;

import com.xiddoc.androidautox.store.CatalogLoader;
import com.xiddoc.androidautox.store.DeviceProfile;
import com.xiddoc.androidautox.store.JdkHttpFetcher;
import com.xiddoc.androidautox.store.PrefsInstallRecords;
import com.xiddoc.androidautox.store.StoreController;
import com.xiddoc.androidautox.store.StoreInstaller;
import com.xiddoc.androidautox.store.StoreSettings;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.Executors;
import java.util.function.LongSupplier;

/**
 * Builds the one {@link StoreController} the phone UI and the car screens share. Everything
 * here is wiring of real Android parts into the pure store classes; excluded from coverage.
 */
public final class StoreEnvironment {

    private static final String SEED_ASSET = "store/seed-catalog.json";

    private static StoreController controller;
    private static StoreSettings settings;

    private StoreEnvironment() {}

    public static synchronized StoreController controller(Context context) {
        if (controller == null) {
            build(context.getApplicationContext());
        }
        return controller;
    }

    public static synchronized StoreSettings settings(Context context) {
        if (settings == null) {
            build(context.getApplicationContext());
        }
        return settings;
    }

    private static void build(Context app) {
        PackageManager pm = app.getPackageManager();
        SharedPreferences settingsPrefs = app.getSharedPreferences(StoreSettings.PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences recordPrefs = app.getSharedPreferences(PrefsInstallRecords.PREFS_NAME, Context.MODE_PRIVATE);
        StoreSettings storeSettings = new StoreSettings(settingsPrefs);
        PrefsInstallRecords records = new PrefsInstallRecords(recordPrefs);
        AndroidInstalledApps installed = new AndroidInstalledApps(pm);
        JdkHttpFetcher http = new JdkHttpFetcher();
        DeviceProfile device = new DeviceProfile(Build.VERSION.SDK_INT, Arrays.asList(Build.SUPPORTED_ABIS));

        CatalogLoader loader = new CatalogLoader(http, new File(app.getFilesDir(), "store"),
                readSeed(app), device);
        StoreInstaller installer = new StoreInstaller(http, new File(app.getCacheDir(), "store-apk"),
                new AndroidApkInspector(pm), installed, new LibsuRootShell(), records,
                new LongSupplier() {
                    @Override
                    public long getAsLong() {
                        return System.currentTimeMillis();
                    }
                });
        controller = new StoreController(loader, installer, records, installed, storeSettings,
                Executors.newSingleThreadExecutor(), new LongSupplier() {
                    @Override
                    public long getAsLong() {
                        return System.currentTimeMillis();
                    }
                });
        settings = storeSettings;
    }

    private static String readSeed(Context app) {
        try {
            InputStream in = app.getAssets().open(SEED_ASSET);
            try {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                int n;
                while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
                return new String(out.toByteArray(), StandardCharsets.UTF_8);
            } finally {
                in.close();
            }
        } catch (IOException e) {
            return "{}"; // an empty seed: the store still works with the user's own catalog
        }
    }
}
