package com.xiddoc.androidautox.store;

import android.content.SharedPreferences;

/**
 * The store's preferences: whether to use the F-Droid index, and an optional catalog URL.
 * Takes the {@link SharedPreferences} so it can be tested without Android.
 */
public final class StoreSettings {

    /** Name of the preferences file. */
    public static final String PREFS_NAME = "store_settings";

    static final String KEY_FDROID = "fdroid_enabled";
    static final String KEY_REMOTE_URL = "remote_catalog_url";

    private final SharedPreferences prefs;

    public StoreSettings(SharedPreferences prefs) {
        this.prefs = prefs;
    }

    /** Whether the F-Droid index may be downloaded (about 21 MB) and used. Off by default. */
    public boolean fdroidEnabled() {
        return prefs.getBoolean(KEY_FDROID, false);
    }

    public void setFdroidEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_FDROID, enabled).apply();
    }

    /** The user's own catalog URL, or empty when none is set. */
    public String remoteUrl() {
        String url = prefs.getString(KEY_REMOTE_URL, "");
        return StoreValidation.isHttpsUrl(url) ? url : "";
    }

    /**
     * @param url an https address, or null/empty to clear
     * @return false (and nothing is stored) if {@code url} is neither empty nor a valid https address
     */
    public boolean setRemoteUrl(String url) {
        String trimmed = url == null ? "" : url.trim();
        if (trimmed.isEmpty()) {
            prefs.edit().remove(KEY_REMOTE_URL).apply();
            return true;
        }
        if (!StoreValidation.isHttpsUrl(trimmed)) return false;
        prefs.edit().putString(KEY_REMOTE_URL, trimmed).apply();
        return true;
    }
}
