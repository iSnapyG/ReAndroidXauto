package com.xiddoc.androidautox.store.android;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;

import com.xiddoc.androidautox.store.ApkInspector;
import com.xiddoc.androidautox.store.Hashing;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads an APK's package name, version and signing certificates with the platform's own
 * parser (the same one the installer will use). Excluded from coverage: needs a device.
 */
public final class AndroidApkInspector implements ApkInspector {

    private final PackageManager packageManager;

    public AndroidApkInspector(PackageManager packageManager) {
        this.packageManager = packageManager;
    }

    @Override
    public Info inspect(File apk) {
        try {
            PackageInfo info = packageManager.getPackageArchiveInfo(apk.getAbsolutePath(),
                    PackageManager.GET_SIGNING_CERTIFICATES);
            if (info == null || info.packageName == null) return null;
            List<String> fingerprints = new ArrayList<String>();
            if (info.signingInfo != null) {
                Signature[] signers = info.signingInfo.getApkContentsSigners();
                if (signers != null) {
                    for (Signature s : signers) fingerprints.add(Hashing.sha256Hex(s.toByteArray()));
                }
            }
            return new Info(info.packageName, info.getLongVersionCode(), fingerprints);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
