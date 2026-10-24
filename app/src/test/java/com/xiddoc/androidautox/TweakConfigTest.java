package com.xiddoc.androidautox;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

/** Plain-JUnit coverage for {@link TweakConfig}. */
public class TweakConfigTest {

    private FakeSharedPreferences prefs;

    @Before
    public void setUp() {
        prefs = new FakeSharedPreferences();
    }

    private static TweakConfig parse(String... lines) {
        StringBuilder sb = new StringBuilder("version=1\n");
        for (String l : lines) sb.append(l).append('\n');
        return TweakConfig.parse(sb.toString());
    }

    private static void assertRejected(String text, String messagePart) {
        try {
            TweakConfig.parse(text);
            fail("expected IllegalArgumentException containing: " + messagePart);
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(), e.getMessage().contains(messagePart));
        }
    }

    // ------------------------------------------------------------ keys

    @Test
    public void configurableKeys_excludePatchedAppsButKeepTheRest() {
        assertFalse(TweakConfig.configurableKeys().contains("aa_patched_apps"));
        assertTrue(TweakConfig.configurableKeys().contains("aa_six_tap"));
        assertEquals(TweakRegistry.ALL_KEYS.length - 1, TweakConfig.configurableKeys().size());
    }

    @Test
    public void needsValue_onlyForSliderTweaks() {
        assertTrue(TweakConfig.needsValue("aa_hun_ms"));
        assertTrue(TweakConfig.needsValue("aa_media_hun"));
        assertTrue(TweakConfig.needsValue("aa_bitrate_usb"));
        assertTrue(TweakConfig.needsValue("aa_bitrate_wireless"));
        assertFalse(TweakConfig.needsValue("aa_six_tap"));
    }

    // ------------------------------------------------------------ fromPrefs

    @Test
    public void fromPrefs_emptyPrefs_givesEmptyConfig() {
        TweakConfig c = TweakConfig.fromPrefs(prefs);
        assertTrue(c.isEmpty());
        assertTrue(c.values().isEmpty());
        assertEquals(0, c.skippedCount());
    }

    @Test
    public void fromPrefs_readsStaticAndSliderTweaks() {
        prefs.edit()
                .putBoolean("aa_six_tap", true)
                .putBoolean("aa_hun_ms", true).putInt("messaging_hun_value", 8000)
                .putBoolean("aa_media_hun", true).putInt("media_hun_value", 300000)
                .putBoolean("aa_bitrate_usb", true).putFloat("usb_bitrate_value", 1.5f)
                .putBoolean("aa_bitrate_wireless", true).putFloat("wifi_bitrate_value", 2f)
                .putBoolean("aa_speed_hack", false)
                .apply();
        TweakConfig c = TweakConfig.fromPrefs(prefs);
        assertTrue(c.enabledKeys().containsAll(Arrays.asList(
                "aa_six_tap", "aa_hun_ms", "aa_media_hun", "aa_bitrate_usb", "aa_bitrate_wireless")));
        assertFalse(c.enabledKeys().contains("aa_speed_hack"));
        assertEquals("8000", c.valueFor("aa_hun_ms"));
        assertEquals("300000", c.valueFor("aa_media_hun"));
        assertEquals("1.5", c.valueFor("aa_bitrate_usb"));
        assertEquals("2.0", c.valueFor("aa_bitrate_wireless"));
        assertNull(c.valueFor("aa_six_tap"));
    }

    @Test
    public void fromPrefs_sliderTweakWithNoSavedValue_isLeftOut() {
        prefs.edit().putBoolean("aa_hun_ms", true).apply();
        assertTrue(TweakConfig.fromPrefs(prefs).isEmpty());
    }

    @Test
    public void fromPrefs_sliderTweakWithOutOfRangeValue_isLeftOut() {
        prefs.edit()
                .putBoolean("aa_bitrate_usb", true).putFloat("usb_bitrate_value", 0f)
                .putBoolean("aa_hun_ms", true).putInt("messaging_hun_value", 999999)
                .apply();
        assertTrue(TweakConfig.fromPrefs(prefs).isEmpty());
    }

    @Test
    public void fromPrefs_ignoresPatchedAppsEvenIfEnabled() {
        prefs.edit().putBoolean("aa_patched_apps", true).apply();
        assertTrue(TweakConfig.fromPrefs(prefs).isEmpty());
    }

    // ------------------------------------------------------------ serialize / parse

    @Test
    public void serialize_writesHeaderVersionTweaksThenValues() {
        prefs.edit().putBoolean("aa_six_tap", true)
                .putBoolean("aa_hun_ms", true).putInt("messaging_hun_value", 5000).apply();
        String text = TweakConfig.fromPrefs(prefs).serialize();
        assertEquals("# AndroidAutoX tweak config\n"
                + "version=1\n"
                + "tweak.aa_six_tap=on\n"
                + "tweak.aa_hun_ms=on\n"
                + "value.messaging_hun_value=5000\n", text);
    }

    @Test
    public void roundTrip_preservesEverything() {
        prefs.edit().putBoolean("aa_six_tap", true)
                .putBoolean("aa_bitrate_usb", true).putFloat("usb_bitrate_value", 1.25f)
                .putBoolean("aa_media_hun", true).putInt("media_hun_value", 42).apply();
        TweakConfig original = TweakConfig.fromPrefs(prefs);
        TweakConfig copy = TweakConfig.parse(original.serialize());
        assertEquals(original, copy);
        assertEquals(original.hashCode(), copy.hashCode());
        assertEquals(original.serialize(), copy.serialize());
    }

    @Test
    public void parse_ignoresBlankLinesCommentsAndWindowsLineEndings() {
        TweakConfig c = TweakConfig.parse(
                "# hello\r\n\r\nversion=1\r\n  # indented comment\r\ntweak.aa_six_tap = on \r\n");
        assertEquals(Collections.singleton("aa_six_tap"), c.enabledKeys());
        assertEquals(0, c.skippedCount());
    }

    @Test
    public void parse_offRemovesAnEarlierOn() {
        TweakConfig c = parse("tweak.aa_six_tap=on", "tweak.aa_six_tap=off");
        assertTrue(c.isEmpty());
    }

    @Test
    public void parse_enabledKeysComeBackInRegistryOrder() {
        TweakConfig c = parse("tweak.kill_telemetry=on", "tweak.aa_message_autoread=on");
        assertEquals(Arrays.asList("aa_message_autoread", "kill_telemetry"),
                new java.util.ArrayList<String>(c.enabledKeys()));
    }

    @Test
    public void parse_unknownTweaksAndValuesAreSkippedAndCounted() {
        TweakConfig c = parse(
                "tweak.aa_activate_coolwalk=on",   // removed tweak from an older export
                "tweak.uxprototype_tweak=on",       // removed tweak
                "tweak.aa_patched_apps=on",         // never exportable
                "value.some_unknown_value=5",
                "tweak.aa_six_tap=on");
        assertEquals(Collections.singleton("aa_six_tap"), c.enabledKeys());
        assertEquals(4, c.skippedCount());
    }

    @Test
    public void parse_sliderWithValue() {
        TweakConfig c = parse("tweak.aa_hun_ms=on", "value.messaging_hun_value=0");
        assertEquals("0", c.valueFor("aa_hun_ms"));
    }

    @Test
    public void parse_valueForTweakThatIsOff_isDropped() {
        TweakConfig c = parse("value.messaging_hun_value=100");
        assertTrue(c.isEmpty());
        assertTrue(c.values().isEmpty());
    }

    @Test
    public void parse_rejectsNull() {
        try {
            TweakConfig.parse(null);
            fail();
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("null"));
        }
    }

    @Test
    public void parse_rejectsOversizedText() {
        StringBuilder sb = new StringBuilder("version=1\n");
        while (sb.length() <= TweakConfig.MAX_TEXT_CHARS) sb.append("# padding padding padding\n");
        assertRejected(sb.toString(), "too large");
    }

    @Test
    public void parse_rejectsMissingVersion() {
        assertRejected("tweak.aa_six_tap=on\n", "missing version");
    }

    @Test
    public void parse_rejectsUnsupportedVersion() {
        assertRejected("version=2\n", "unsupported config version");
    }

    @Test
    public void parse_rejectsLineWithoutEquals() {
        assertRejected("version=1\njust some text\n", "malformed line");
    }

    @Test
    public void parse_rejectsLineThatStartsWithEquals() {
        assertRejected("version=1\n=on\n", "malformed line");
    }

    @Test
    public void parse_rejectsUnrecognisedLine() {
        assertRejected("version=1\nfoo=bar\n", "unrecognised line");
    }

    @Test
    public void parse_rejectsTweakStateThatIsNotOnOrOff() {
        assertRejected("version=1\ntweak.aa_six_tap=maybe\n", "expected on/off");
    }

    @Test
    public void parse_rejectsSliderTweakWithoutItsValue() {
        assertRejected("version=1\ntweak.aa_bitrate_usb=on\n", "needs value.usb_bitrate_value");
    }

    @Test
    public void parse_longBadLineIsAbbreviatedInTheMessage() {
        String longLine = "x" + new String(new char[80]).replace('\0', 'y');
        try {
            TweakConfig.parse("version=1\n" + longLine + "\n");
            fail();
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().endsWith("..."));
            assertTrue(e.getMessage().length() < longLine.length());
        }
    }

    // ------------------------------------------------------------ value ranges

    @Test
    public void parse_floatValueRange() {
        assertRejected("version=1\ntweak.aa_bitrate_usb=on\nvalue.usb_bitrate_value=NaN\n", "bad value");
        assertRejected("version=1\ntweak.aa_bitrate_usb=on\nvalue.usb_bitrate_value=Infinity\n", "bad value");
        assertRejected("version=1\ntweak.aa_bitrate_usb=on\nvalue.usb_bitrate_value=0\n", "bad value");
        assertRejected("version=1\ntweak.aa_bitrate_usb=on\nvalue.usb_bitrate_value=-1\n", "bad value");
        assertRejected("version=1\ntweak.aa_bitrate_usb=on\nvalue.usb_bitrate_value=20.5\n", "bad value");
        assertRejected("version=1\ntweak.aa_bitrate_usb=on\nvalue.usb_bitrate_value=fast\n", "bad value");
        assertEquals("20.0", parse("tweak.aa_bitrate_usb=on", "value.usb_bitrate_value=20")
                .valueFor("aa_bitrate_usb"));
        assertEquals("0.5", parse("tweak.aa_bitrate_wireless=on", "value.wifi_bitrate_value=0.5")
                .valueFor("aa_bitrate_wireless"));
    }

    @Test
    public void parse_intValueRange() {
        assertRejected("version=1\ntweak.aa_hun_ms=on\nvalue.messaging_hun_value=-1\n", "bad value");
        assertRejected("version=1\ntweak.aa_hun_ms=on\nvalue.messaging_hun_value=20001\n", "bad value");
        assertRejected("version=1\ntweak.aa_hun_ms=on\nvalue.messaging_hun_value=soon\n", "bad value");
        assertRejected("version=1\ntweak.aa_media_hun=on\nvalue.media_hun_value=900001\n", "bad value");
        assertEquals("20000", parse("tweak.aa_hun_ms=on", "value.messaging_hun_value=20000")
                .valueFor("aa_hun_ms"));
        assertEquals("900000", parse("tweak.aa_media_hun=on", "value.media_hun_value=900000")
                .valueFor("aa_media_hun"));
    }

    // ------------------------------------------------------------ writeValuesTo

    @Test
    public void writeValuesTo_putsIntsAndFloatsWithTheRightTypes() {
        TweakConfig c = parse(
                "tweak.aa_hun_ms=on", "value.messaging_hun_value=7000",
                "tweak.aa_bitrate_wireless=on", "value.wifi_bitrate_value=1.5");
        android.content.SharedPreferences.Editor ed = prefs.edit();
        c.writeValuesTo(ed);
        ed.apply();
        assertEquals(7000, prefs.getInt("messaging_hun_value", -1));
        assertEquals(1.5f, prefs.getFloat("wifi_bitrate_value", -1f), 0f);
    }

    // ------------------------------------------------------------ diff

    @Test
    public void diff_newlyEnabledAndNewlyDisabled() {
        TweakConfig current = parse("tweak.aa_six_tap=on", "tweak.kill_telemetry=on");
        TweakConfig target = parse("tweak.aa_six_tap=on", "tweak.aa_speed_hack=on");
        TweakConfig.Diff d = TweakConfig.diff(current, target);
        assertEquals(Collections.singletonList("aa_speed_hack"), d.toEnable);
        assertEquals(Collections.singletonList("kill_telemetry"), d.toDisable);
        assertFalse(d.isEmpty());
    }

    @Test
    public void diff_sliderValueChange_reappliesTheTweak() {
        TweakConfig current = parse("tweak.aa_hun_ms=on", "value.messaging_hun_value=1000");
        TweakConfig target = parse("tweak.aa_hun_ms=on", "value.messaging_hun_value=2000");
        TweakConfig.Diff d = TweakConfig.diff(current, target);
        assertEquals(Collections.singletonList("aa_hun_ms"), d.toEnable);
        assertTrue(d.toDisable.isEmpty());
    }

    @Test
    public void diff_identicalConfigs_isEmpty() {
        TweakConfig a = parse("tweak.aa_six_tap=on", "tweak.aa_hun_ms=on", "value.messaging_hun_value=1000");
        TweakConfig b = parse("tweak.aa_six_tap=on", "tweak.aa_hun_ms=on", "value.messaging_hun_value=1000");
        assertTrue(TweakConfig.diff(a, b).isEmpty());
    }

    @Test
    public void diff_onlyDisables_isNotEmpty() {
        TweakConfig.Diff d = TweakConfig.diff(parse("tweak.aa_six_tap=on"), parse());
        assertTrue(d.toEnable.isEmpty());
        assertEquals(Collections.singletonList("aa_six_tap"), d.toDisable);
        assertFalse(d.isEmpty());
    }

    // ------------------------------------------------------------ equals

    @Test
    public void equals_coversAllBranches() {
        TweakConfig a = parse("tweak.aa_hun_ms=on", "value.messaging_hun_value=1000");
        assertTrue(a.equals(a));
        assertFalse(a.equals("not a config"));
        assertFalse(a.equals(null));
        assertNotEquals(a, parse("tweak.aa_hun_ms=on", "value.messaging_hun_value=2000"));
        assertNotEquals(a, parse("tweak.aa_six_tap=on"));
        assertEquals(a, parse("tweak.aa_hun_ms=on", "value.messaging_hun_value=1000"));
    }
}
