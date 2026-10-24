package com.xiddoc.androidautox;

import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * An immutable snapshot of which tweaks are switched on, plus the numeric value of each
 * slider tweak (message/media heads-up timeout, USB/Wi-Fi bitrate). It is the one shared
 * shape behind presets and file export/import.
 *
 * <p>Text format (one setting per line, {@code #} starts a comment):
 * <pre>
 * # AndroidAutoX tweak config
 * version=1
 * tweak.aa_six_tap=on
 * tweak.aa_hun_ms=on
 * value.messaging_hun_value=8000
 * </pre>
 * Only enabled tweaks are written. Every instance is valid by construction: values are range
 * checked, and an enabled slider tweak always carries its value, so anything that serializes
 * can be parsed back. The patched-apps tweak is deliberately excluded because it depends on
 * apps being reinstalled, which a config file cannot express.
 *
 * <p>Pure Java apart from {@link SharedPreferences}; unit-tested on the plain JUnit classloader.
 */
public final class TweakConfig {

    public static final int FORMAT_VERSION = 1;
    public static final String HEADER = "# AndroidAutoX tweak config";
    /** Upper bound on an imported file, so a wrong file can't be read into memory. */
    public static final int MAX_TEXT_CHARS = 16 * 1024;

    static final String TWEAK_PREFIX = "tweak.";
    static final String VALUE_PREFIX = "value.";

    /** Slider tweak -> the pref key that holds its value. */
    private static final Map<String, String> VALUE_KEY_FOR_TWEAK;
    /** Value pref keys whose stored type is a float (the rest are ints). */
    private static final Set<String> FLOAT_VALUE_KEYS;
    /** Tweak keys a config may switch on, in the registry's order. */
    private static final List<String> CONFIGURABLE;

    static {
        Map<String, String> m = new LinkedHashMap<String, String>();
        m.put("aa_hun_ms", "messaging_hun_value");
        m.put("aa_media_hun", "media_hun_value");
        m.put("aa_bitrate_usb", "usb_bitrate_value");
        m.put("aa_bitrate_wireless", "wifi_bitrate_value");
        VALUE_KEY_FOR_TWEAK = Collections.unmodifiableMap(m);

        Set<String> f = new LinkedHashSet<String>();
        f.add("usb_bitrate_value");
        f.add("wifi_bitrate_value");
        FLOAT_VALUE_KEYS = Collections.unmodifiableSet(f);

        List<String> keys = new ArrayList<String>();
        for (String k : TweakRegistry.ALL_KEYS) {
            if (!"aa_patched_apps".equals(k)) keys.add(k);
        }
        CONFIGURABLE = Collections.unmodifiableList(keys);
    }

    private final Set<String> enabled;
    private final Map<String, String> values;
    private final int skipped;

    private TweakConfig(Set<String> enabled, Map<String, String> values, int skipped) {
        this.enabled = Collections.unmodifiableSet(enabled);
        this.values = Collections.unmodifiableMap(values);
        this.skipped = skipped;
    }

    /** The tweak keys a config can carry. */
    public static List<String> configurableKeys() {
        return CONFIGURABLE;
    }

    /** True if {@code tweakKey} is a slider tweak that needs a numeric value. */
    public static boolean needsValue(String tweakKey) {
        return VALUE_KEY_FOR_TWEAK.containsKey(tweakKey);
    }

    // ---------------------------------------------------------------- reading prefs

    /**
     * Builds a config from the app's saved tweak state. An enabled slider tweak whose saved
     * value is missing or out of range is left out, so the result is always valid.
     */
    public static TweakConfig fromPrefs(SharedPreferences sp) {
        Set<String> on = new LinkedHashSet<String>();
        Map<String, String> vals = new LinkedHashMap<String, String>();
        for (String key : CONFIGURABLE) {
            if (!sp.getBoolean(key, false)) continue;
            String valueKey = VALUE_KEY_FOR_TWEAK.get(key);
            if (valueKey == null) {
                on.add(key);
                continue;
            }
            String canonical = readSavedValue(sp, valueKey);
            if (canonical == null) continue;
            on.add(key);
            vals.put(valueKey, canonical);
        }
        return new TweakConfig(on, vals, 0);
    }

    private static String readSavedValue(SharedPreferences sp, String valueKey) {
        if (!sp.contains(valueKey)) return null;
        String raw = FLOAT_VALUE_KEYS.contains(valueKey)
                ? Float.toString(sp.getFloat(valueKey, 0f))
                : Integer.toString(sp.getInt(valueKey, 0));
        return canonicalValue(valueKey, raw);
    }

    // ---------------------------------------------------------------- text format

    /** Renders the config in the text format above. Parsing the result gives an equal config. */
    public String serialize() {
        StringBuilder sb = new StringBuilder();
        sb.append(HEADER).append('\n');
        sb.append("version=").append(FORMAT_VERSION).append('\n');
        for (String key : enabled) {
            sb.append(TWEAK_PREFIX).append(key).append("=on\n");
        }
        for (Map.Entry<String, String> e : values.entrySet()) {
            sb.append(VALUE_PREFIX).append(e.getKey()).append('=').append(e.getValue()).append('\n');
        }
        return sb.toString();
    }

    /**
     * Parses config text.
     *
     * <p>Unknown tweak or value names are skipped (and counted in {@link #skippedCount()}) so a
     * file exported by a different build still loads. Anything structurally wrong throws.
     *
     * @throws IllegalArgumentException for a null/oversized text, an unsupported version, a
     *         malformed line, an out-of-range value, or an enabled slider tweak with no value
     */
    public static TweakConfig parse(String text) {
        if (text == null) throw new IllegalArgumentException("config text is null");
        if (text.length() > MAX_TEXT_CHARS) throw new IllegalArgumentException("config file is too large");

        Set<String> on = new LinkedHashSet<String>();
        Map<String, String> vals = new LinkedHashMap<String, String>();
        int skippedLines = 0;
        boolean sawVersion = false;

        for (String rawLine : text.split("\\r?\\n")) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            int eq = line.indexOf('=');
            if (eq <= 0) throw new IllegalArgumentException("malformed line: " + abbreviate(line));
            String name = line.substring(0, eq).trim();
            String value = line.substring(eq + 1).trim();

            if (name.equals("version")) {
                if (!value.equals(Integer.toString(FORMAT_VERSION))) {
                    throw new IllegalArgumentException("unsupported config version: " + abbreviate(value));
                }
                sawVersion = true;
            } else if (name.startsWith(TWEAK_PREFIX)) {
                String key = name.substring(TWEAK_PREFIX.length());
                if (!value.equals("on") && !value.equals("off")) {
                    throw new IllegalArgumentException("expected on/off for " + abbreviate(name));
                }
                if (!CONFIGURABLE.contains(key)) {
                    skippedLines++;
                } else if (value.equals("on")) {
                    on.add(key);
                } else {
                    on.remove(key);
                }
            } else if (name.startsWith(VALUE_PREFIX)) {
                String valueKey = name.substring(VALUE_PREFIX.length());
                if (!VALUE_KEY_FOR_TWEAK.containsValue(valueKey)) {
                    skippedLines++;
                } else {
                    vals.put(valueKey, parseValue(valueKey, value));
                }
            } else {
                throw new IllegalArgumentException("unrecognised line: " + abbreviate(line));
            }
        }
        if (!sawVersion) throw new IllegalArgumentException("missing version line");

        // Keep registry order, and require a value for every enabled slider tweak.
        Set<String> ordered = new LinkedHashSet<String>();
        Map<String, String> orderedVals = new LinkedHashMap<String, String>();
        for (String key : CONFIGURABLE) {
            if (!on.contains(key)) continue;
            ordered.add(key);
            String valueKey = VALUE_KEY_FOR_TWEAK.get(key);
            if (valueKey != null) {
                String v = vals.get(valueKey);
                if (v == null) {
                    throw new IllegalArgumentException(key + " needs " + VALUE_PREFIX + valueKey);
                }
                orderedVals.put(valueKey, v);
            }
        }
        return new TweakConfig(ordered, orderedVals, skippedLines);
    }

    private static String parseValue(String valueKey, String raw) {
        String canonical = canonicalValue(valueKey, raw);
        if (canonical == null) {
            throw new IllegalArgumentException("bad value for " + valueKey + ": " + abbreviate(raw));
        }
        return canonical;
    }

    /** Canonical text for a value, or null if it is not a number in the allowed range. */
    private static String canonicalValue(String valueKey, String raw) {
        try {
            if (FLOAT_VALUE_KEYS.contains(valueKey)) {
                float f = Float.parseFloat(raw);
                // Bitrate multiplier: the slider tops out at 20 and 0 would mean "no video".
                if (Float.isNaN(f) || Float.isInfinite(f) || f <= 0f || f > 20f) return null;
                return Float.toString(f);
            }
            int i = Integer.parseInt(raw);
            int max = valueKey.equals("media_hun_value") ? 900000 : 20000;
            if (i < 0 || i > max) return null;
            return Integer.toString(i);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String abbreviate(String s) {
        return s.length() <= 40 ? s : s.substring(0, 40) + "...";
    }

    // ---------------------------------------------------------------- accessors

    /** Enabled tweak keys, in registry order. */
    public Set<String> enabledKeys() {
        return enabled;
    }

    /** Slider values by pref key (only for enabled slider tweaks). */
    public Map<String, String> values() {
        return values;
    }

    /** How many unknown tweak/value lines {@link #parse} ignored. */
    public int skippedCount() {
        return skipped;
    }

    public boolean isEmpty() {
        return enabled.isEmpty();
    }

    /** The saved value for a slider tweak, or null if the tweak is off / not a slider. */
    public String valueFor(String tweakKey) {
        String valueKey = VALUE_KEY_FOR_TWEAK.get(tweakKey);
        return valueKey == null ? null : values.get(valueKey);
    }

    /** Writes the slider values into prefs so {@link TweakRegistry#specsFor} reads them. */
    public void writeValuesTo(SharedPreferences.Editor editor) {
        for (Map.Entry<String, String> e : values.entrySet()) {
            if (FLOAT_VALUE_KEYS.contains(e.getKey())) {
                editor.putFloat(e.getKey(), Float.parseFloat(e.getValue()));
            } else {
                editor.putInt(e.getKey(), Integer.parseInt(e.getValue()));
            }
        }
    }

    // ---------------------------------------------------------------- diff

    /** What has to change to get from one config to another. */
    public static final class Diff {
        /** Tweaks to (re)apply: newly on, or still on but with a different slider value. */
        public final List<String> toEnable;
        /** Tweaks to revert: on now, off in the target. */
        public final List<String> toDisable;

        Diff(List<String> toEnable, List<String> toDisable) {
            this.toEnable = Collections.unmodifiableList(toEnable);
            this.toDisable = Collections.unmodifiableList(toDisable);
        }

        public boolean isEmpty() {
            return toEnable.isEmpty() && toDisable.isEmpty();
        }
    }

    /** Computes the changes needed to move from {@code current} to {@code target}. */
    public static Diff diff(TweakConfig current, TweakConfig target) {
        List<String> enable = new ArrayList<String>();
        List<String> disable = new ArrayList<String>();
        for (String key : target.enabled) {
            if (!current.enabled.contains(key)) {
                enable.add(key);
                continue;
            }
            String want = target.valueFor(key);
            if (want != null && !want.equals(current.valueFor(key))) enable.add(key);
        }
        for (String key : current.enabled) {
            if (!target.enabled.contains(key)) disable.add(key);
        }
        return new Diff(enable, disable);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TweakConfig)) return false;
        TweakConfig other = (TweakConfig) o;
        return enabled.equals(other.enabled) && values.equals(other.values);
    }

    @Override
    public int hashCode() {
        return 31 * enabled.hashCode() + values.hashCode();
    }
}
