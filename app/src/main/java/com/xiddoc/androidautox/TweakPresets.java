package com.xiddoc.androidautox;

import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Named {@link TweakConfig}s kept in a dedicated preferences file, so a user can save the
 * tweaks they have on ("Road trip", "Commute") and switch back to them later.
 *
 * <p>Each preset is stored as its config text under {@code preset|<name>}. Names are trimmed,
 * runs of whitespace collapsed, and limited to {@link #MAX_NAME_LENGTH} printable characters.
 */
public final class TweakPresets {

    /** Name of the preferences file that holds the presets. */
    public static final String PREFS_NAME = "tweak_presets";
    public static final int MAX_PRESETS = 20;
    public static final int MAX_NAME_LENGTH = 40;

    private static final String KEY_PREFIX = "preset|";

    /** Outcome of {@link #save}. */
    public enum SaveResult { SAVED, REPLACED, INVALID_NAME, TOO_MANY }

    private final SharedPreferences prefs;

    public TweakPresets(SharedPreferences prefs) {
        this.prefs = prefs;
    }

    /**
     * Trims and collapses whitespace.
     *
     * @return the normalized name, or null if it is empty, too long, or contains a control
     *         character
     */
    public static String normalizeName(String raw) {
        if (raw == null) return null;
        String name = raw.trim().replaceAll("\\s+", " ");
        if (name.isEmpty() || name.length() > MAX_NAME_LENGTH) return null;
        for (int i = 0; i < name.length(); i++) {
            if (Character.isISOControl(name.charAt(i))) return null;
        }
        return name;
    }

    /** Saved preset names, sorted without regard to case. */
    public List<String> names() {
        List<String> out = new ArrayList<String>();
        for (Map.Entry<String, ?> e : prefs.getAll().entrySet()) {
            if (e.getKey().startsWith(KEY_PREFIX) && e.getValue() instanceof String) {
                out.add(e.getKey().substring(KEY_PREFIX.length()));
            }
        }
        Collections.sort(out, String.CASE_INSENSITIVE_ORDER);
        return out;
    }

    /** The preset's config, or null if there is none by that name or its text is unreadable. */
    public TweakConfig get(String name) {
        String clean = normalizeName(name);
        if (clean == null) return null;
        String text = prefs.getString(KEY_PREFIX + clean, null);
        if (text == null) return null;
        try {
            return TweakConfig.parse(text);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Saves {@code config} under {@code name}, replacing an existing preset of that name. */
    public SaveResult save(String name, TweakConfig config) {
        String clean = normalizeName(name);
        if (clean == null) return SaveResult.INVALID_NAME;
        String key = KEY_PREFIX + clean;
        boolean exists = prefs.contains(key);
        if (!exists && names().size() >= MAX_PRESETS) return SaveResult.TOO_MANY;
        prefs.edit().putString(key, config.serialize()).apply();
        return exists ? SaveResult.REPLACED : SaveResult.SAVED;
    }

    /** Removes the preset. Returns true if one was removed. */
    public boolean delete(String name) {
        String clean = normalizeName(name);
        if (clean == null) return false;
        String key = KEY_PREFIX + clean;
        if (!prefs.contains(key)) return false;
        prefs.edit().remove(key).apply();
        return true;
    }
}
