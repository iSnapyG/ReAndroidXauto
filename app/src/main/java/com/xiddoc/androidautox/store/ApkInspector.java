package com.xiddoc.androidautox.store;

import java.io.File;
import java.util.List;

/** Reads package name, version and signing certificates out of an APK file. */
public interface ApkInspector {

    final class Info {
        public final String packageName;
        public final long versionCode;
        /** SHA-256 fingerprints of the signing certificates, as hex. */
        public final List<String> signers;

        public Info(String packageName, long versionCode, List<String> signers) {
            this.packageName = packageName;
            this.versionCode = versionCode;
            this.signers = signers;
        }
    }

    /** @return the APK's details, or null if the file is not a readable APK */
    Info inspect(File apk);
}
