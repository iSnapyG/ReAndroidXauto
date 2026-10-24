package com.xiddoc.androidautox.store;

import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** {@link InstallRecords} kept as one JSON string in {@link SharedPreferences}. */
public final class PrefsInstallRecords implements InstallRecords {

    /** Name of the preferences file. */
    public static final String PREFS_NAME = "store_installs";

    static final String KEY = "records";

    private final SharedPreferences prefs;

    public PrefsInstallRecords(SharedPreferences prefs) {
        this.prefs = prefs;
    }

    @Override
    public synchronized InstallRecord get(String packageName) {
        return read().get(packageName);
    }

    @Override
    public synchronized void put(InstallRecord record) {
        Map<String, InstallRecord> all = read();
        all.put(record.packageName, record);
        write(all);
    }

    @Override
    public synchronized void remove(String packageName) {
        Map<String, InstallRecord> all = read();
        all.remove(packageName);
        write(all);
    }

    @Override
    public synchronized List<InstallRecord> all() {
        return new ArrayList<InstallRecord>(read().values());
    }

    private Map<String, InstallRecord> read() {
        Map<String, InstallRecord> out = new LinkedHashMap<String, InstallRecord>();
        for (InstallRecord r : InstallRecord.decode(prefs.getString(KEY, null))) {
            out.put(r.packageName, r);
        }
        return out;
    }

    private void write(Map<String, InstallRecord> all) {
        prefs.edit().putString(KEY, InstallRecord.encode(all.values())).apply();
    }
}
