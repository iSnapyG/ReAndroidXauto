package com.xiddoc.androidautox.store.android;

import android.content.pm.PackageManager;

import com.xiddoc.androidautox.store.InstalledApps;

/** Asks {@link PackageManager} which version of a package is installed. */
public final class AndroidInstalledApps implements InstalledApps {

    private final PackageManager packageManager;

    public AndroidInstalledApps(PackageManager packageManager) {
        this.packageManager = packageManager;
    }

    @Override
    public long versionCode(String packageName) {
        try {
            return packageManager.getPackageInfo(packageName, 0).getLongVersionCode();
        } catch (PackageManager.NameNotFoundException e) {
            return -1;
        }
    }
}
