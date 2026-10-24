package com.xiddoc.androidautox;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

/** Plain-JUnit coverage for {@link TweakPresets}. */
public class TweakPresetsTest {

    private FakeSharedPreferences prefs;
    private TweakPresets presets;

    @Before
    public void setUp() {
        prefs = new FakeSharedPreferences();
        presets = new TweakPresets(prefs);
    }

    private static TweakConfig config(String... enabled) {
        StringBuilder sb = new StringBuilder("version=1\n");
        for (String k : enabled) sb.append("tweak.").append(k).append("=on\n");
        return TweakConfig.parse(sb.toString());
    }

    // ------------------------------------------------------------ normalizeName

    @Test
    public void normalizeName_trimsAndCollapsesWhitespace() {
        assertEquals("Road trip", TweakPresets.normalizeName("  Road \t  trip \n"));
    }

    @Test
    public void normalizeName_rejectsNullBlankAndTooLong() {
        assertNull(TweakPresets.normalizeName(null));
        assertNull(TweakPresets.normalizeName(""));
        assertNull(TweakPresets.normalizeName("   "));
        String max = new String(new char[TweakPresets.MAX_NAME_LENGTH]).replace('\0', 'a');
        assertEquals(max, TweakPresets.normalizeName(max));
        assertNull(TweakPresets.normalizeName(max + "a"));
    }

    @Test
    public void normalizeName_rejectsControlCharacters() {
        assertNull(TweakPresets.normalizeName("bad\u0001name"));
    }

    // ------------------------------------------------------------ save / get

    @Test
    public void save_thenGet_roundTrips() {
        assertEquals(TweakPresets.SaveResult.SAVED, presets.save("Commute", config("aa_six_tap")));
        assertEquals(config("aa_six_tap"), presets.get("Commute"));
        // Lookup normalizes the name the same way as save.
        assertEquals(config("aa_six_tap"), presets.get("  Commute "));
    }

    @Test
    public void save_sameNameTwice_replaces() {
        presets.save("A", config("aa_six_tap"));
        assertEquals(TweakPresets.SaveResult.REPLACED, presets.save("A", config("kill_telemetry")));
        assertEquals(config("kill_telemetry"), presets.get("A"));
        assertEquals(Collections.singletonList("A"), presets.names());
    }

    @Test
    public void save_invalidName_isRejectedAndNothingStored() {
        assertEquals(TweakPresets.SaveResult.INVALID_NAME, presets.save("  ", config("aa_six_tap")));
        assertTrue(presets.names().isEmpty());
    }

    @Test
    public void save_enforcesTheLimitOnNewPresetsButStillAllowsReplacing() {
        for (int i = 0; i < TweakPresets.MAX_PRESETS; i++) {
            assertEquals(TweakPresets.SaveResult.SAVED, presets.save("p" + i, config("aa_six_tap")));
        }
        assertEquals(TweakPresets.SaveResult.TOO_MANY, presets.save("one more", config("aa_six_tap")));
        assertEquals(TweakPresets.MAX_PRESETS, presets.names().size());
        assertEquals(TweakPresets.SaveResult.REPLACED, presets.save("p0", config("kill_telemetry")));
    }

    @Test
    public void get_unknownInvalidOrCorruptName_returnsNull() {
        assertNull(presets.get("missing"));
        assertNull(presets.get(null));
        prefs.edit().putString("preset|broken", "this is not a config").apply();
        assertNull(presets.get("broken"));
    }

    // ------------------------------------------------------------ names

    @Test
    public void names_areSortedIgnoringCase_andSkipUnrelatedPrefs() {
        presets.save("banana", config("aa_six_tap"));
        presets.save("Apple", config("aa_six_tap"));
        presets.save("cherry", config("aa_six_tap"));
        prefs.edit().putString("something_else", "x").putInt("preset|not-a-string", 3).apply();
        assertEquals(Arrays.asList("Apple", "banana", "cherry"), presets.names());
    }

    // ------------------------------------------------------------ delete

    @Test
    public void delete_removesOnlyAnExistingPreset() {
        presets.save("A", config("aa_six_tap"));
        assertFalse(presets.delete("nope"));
        assertFalse(presets.delete("  "));
        assertTrue(presets.delete(" A "));
        assertTrue(presets.names().isEmpty());
        assertFalse(presets.delete("A"));
    }
}
