package com.xiddoc.androidautox.store;

import java.util.LinkedHashMap;
import java.util.Map;

/** Which of the apps the store installed are still on the phone, and at what version. */
public final class InstalledView {

    private InstalledView() {}

    /**
     * Package name to the version code actually installed now, for every record whose app is
     * still installed. Records of apps that were removed behind the store's back are left out.
     */
    public static Map<String, Long> tracked(InstallRecords records, InstalledApps installed) {
        Map<String, Long> out = new LinkedHashMap<String, Long>();
        for (InstallRecord r : records.all()) {
            long v = installed.versionCode(r.packageName);
            if (v >= 0) out.put(r.packageName, v);
        }
        return out;
    }
}
