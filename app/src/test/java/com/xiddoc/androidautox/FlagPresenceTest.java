package com.xiddoc.androidautox;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Plain-JUnit coverage for {@link FlagPresence}, using hand-built dex files and zips. */
public class FlagPresenceTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    // ------------------------------------------------------------ test fixtures

    /** A minimal but structurally valid dex: header, string id table, string data. */
    private static byte[] dex(String... strings) {
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        int dataStart = 0x70 + 4 * strings.length;
        int[] offsets = new int[strings.length];
        for (int i = 0; i < strings.length; i++) {
            offsets[i] = dataStart + data.size();
            writeUleb(data, strings[i].length());
            byte[] b = strings[i].getBytes(StandardCharsets.UTF_8);
            data.write(b, 0, b.length);
            data.write(0);
        }
        ByteBuffer header = ByteBuffer.allocate(dataStart).order(ByteOrder.LITTLE_ENDIAN);
        header.put(new byte[]{'d', 'e', 'x', '\n', '0', '3', '5', 0});
        header.putInt(0x38, strings.length);
        header.putInt(0x3C, 0x70);
        for (int i = 0; i < strings.length; i++) header.putInt(0x70 + 4 * i, offsets[i]);
        byte[] h = header.array();
        byte[] d = data.toByteArray();
        byte[] all = new byte[h.length + d.length];
        System.arraycopy(h, 0, all, 0, h.length);
        System.arraycopy(d, 0, all, h.length, d.length);
        return all;
    }

    private static void writeUleb(ByteArrayOutputStream out, int value) {
        int v = value;
        do {
            int b = v & 0x7F;
            v >>>= 7;
            out.write(v != 0 ? (b | 0x80) : b);
        } while (v != 0);
    }

    private File zip(String name, Map<String, byte[]> entries) throws IOException {
        File f = tmp.newFile(name);
        try (ZipOutputStream z = new ZipOutputStream(new FileOutputStream(f))) {
            for (Map.Entry<String, byte[]> e : entries.entrySet()) {
                z.putNextEntry(new ZipEntry(e.getKey()));
                z.write(e.getValue());
                z.closeEntry();
            }
        }
        return f;
    }

    private static Set<String> set(String... s) {
        return new HashSet<String>(Arrays.asList(s));
    }

    private static Set<String> scan(byte[] dex, String... wanted) throws IOException {
        Set<String> found = new HashSet<String>();
        FlagPresence.scanDex(dex, set(wanted), found);
        return found;
    }

    // ------------------------------------------------------------ scanDex

    @Test
    public void scanDex_findsWantedNamesAndIgnoresTheRest() throws IOException {
        byte[] d = dex("Coolwalk__enabled", "SystemUi__other", "Lcom/foo/Bar;", "plain");
        assertEquals(set("Coolwalk__enabled"), scan(d, "Coolwalk__enabled", "Gone__flag"));
    }

    @Test
    public void scanDex_requiresAnExactMatchNotAPrefix() throws IOException {
        byte[] d = dex("Coolwalk__enabled_v2");
        assertTrue(scan(d, "Coolwalk__enabled").isEmpty());
    }

    @Test
    public void scanDex_prefilterSkipsShortLongAndSingleUnderscoreStrings() throws IOException {
        String tooLong = "Feature__" + new String(new char[200]).replace('\0', 'x');
        byte[] d = dex(
                "a__",            // shorter than 4 bytes
                tooLong,          // longer than 200 bytes (also needs a 2-byte length prefix)
                "abc_def_ghi",    // underscores, but never two in a row
                "abc_",           // ends in an underscore
                "ab__cd");        // a real match
        Set<String> wanted = set("a__", tooLong, "abc_def_ghi", "abc_", "ab__cd");
        Set<String> found = new HashSet<String>();
        FlagPresence.scanDex(d, wanted, found);
        assertEquals(set("ab__cd"), found);
    }

    @Test
    public void scanDex_emptyStringTable_findsNothing() throws IOException {
        assertTrue(scan(dex(), "Coolwalk__enabled").isEmpty());
    }

    @Test
    public void scanDex_rejectsTooShortOrWrongMagic() {
        assertRejected(new byte[10], "not a dex file");
        for (int i = 0; i < 4; i++) {
            byte[] d = dex("Coolwalk__enabled");
            d[i] = 'X';
            assertRejected(d, "not a dex file");
        }
    }

    @Test
    public void scanDex_rejectsStringTableOutsideTheFile() {
        byte[] d = dex("Coolwalk__enabled");
        ByteBuffer.wrap(d).order(ByteOrder.LITTLE_ENDIAN).putInt(0x38, 1000000);
        assertRejected(d, "string table out of range");
    }

    @Test
    public void scanDex_rejectsStringDataOutsideTheFile() {
        byte[] d = dex("Coolwalk__enabled");
        ByteBuffer.wrap(d).order(ByteOrder.LITTLE_ENDIAN).putInt(0x70, d.length + 50);
        assertRejected(d, "string data out of range");
    }

    @Test
    public void scanDex_rejectsLengthPrefixThatRunsOffTheEnd() {
        byte[] d = dex("Coolwalk__enabled");
        d[d.length - 1] = (byte) 0x80;
        ByteBuffer.wrap(d).order(ByteOrder.LITTLE_ENDIAN).putInt(0x70, d.length - 1);
        assertRejected(d, "unterminated string length");
    }

    @Test
    public void scanDex_rejectsStringWithoutTerminator() {
        byte[] d = dex("Coolwalk__enabled");
        d[d.length - 1] = 'x';
        assertRejected(d, "unterminated string");
    }

    private static void assertRejected(byte[] d, String messagePart) {
        try {
            FlagPresence.scanDex(d, set("Coolwalk__enabled"), new HashSet<String>());
            fail("expected IOException containing: " + messagePart);
        } catch (IOException e) {
            assertTrue(e.getMessage(), e.getMessage().contains(messagePart));
        }
    }

    // ------------------------------------------------------------ scanApks

    @Test
    public void scanApks_nothingWanted_returnsEmptyWithoutReading() throws IOException {
        File notAZip = tmp.newFile("junk.apk");
        assertTrue(FlagPresence.scanApks(Collections.singletonList(notAZip),
                Collections.<String>emptyList()).isEmpty());
    }

    @Test
    public void scanApks_noApks_findsNothing() throws IOException {
        assertTrue(FlagPresence.scanApks(Collections.<File>emptyList(), set("A__b")).isEmpty());
    }

    @Test
    public void scanApks_readsEveryClassesDexAndSkipsOtherEntries() throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<String, byte[]>();
        entries.put("AndroidManifest.xml", "<manifest/>".getBytes(StandardCharsets.UTF_8));
        entries.put("classes.dex", dex("Alpha__one"));
        entries.put("classes2.dex", dex("Beta__two"));
        // Not at the archive root, so it must not be read (it would be rejected as malformed).
        entries.put("assets/classes.dex", "garbage".getBytes(StandardCharsets.UTF_8));
        File apk = zip("a.apk", entries);
        Set<String> found = FlagPresence.scanApks(Collections.singletonList(apk),
                Arrays.asList("Alpha__one", "Beta__two", "Gamma__three"));
        assertEquals(set("Alpha__one", "Beta__two"), found);
    }

    @Test
    public void scanApks_combinesSplitApksAndStopsEarlyWhenAllFound() throws IOException {
        Map<String, byte[]> base = new LinkedHashMap<String, byte[]>();
        base.put("classes.dex", dex("Alpha__one"));
        Map<String, byte[]> split = new LinkedHashMap<String, byte[]>();
        split.put("classes.dex", dex("Beta__two"));
        File a = zip("base.apk", base);
        File b = zip("split.apk", split);
        List<File> both = Arrays.asList(a, b);
        assertEquals(set("Alpha__one", "Beta__two"),
                FlagPresence.scanApks(both, Arrays.asList("Alpha__one", "Beta__two")));

        // Everything is in the first APK, so the second is never opened: a corrupt second APK
        // must not matter.
        File corrupt = tmp.newFile("corrupt.apk");
        assertEquals(set("Alpha__one"),
                FlagPresence.scanApks(Arrays.asList(a, corrupt), Collections.singletonList("Alpha__one")));
    }

    @Test
    public void scanApks_malformedDexSurfacesAsAnError() throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<String, byte[]>();
        entries.put("classes.dex", "this is not a dex file at all, just text padding..".getBytes(StandardCharsets.UTF_8));
        File apk = zip("bad.apk", entries);
        try {
            FlagPresence.scanApks(Collections.singletonList(apk), Collections.singletonList("A__b"));
            fail("expected IOException");
        } catch (IOException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("not a dex file"));
        }
    }

    @Test
    public void scanApks_emptyDexEntryIsRejected() throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<String, byte[]>();
        entries.put("classes.dex", new byte[0]);
        File apk = zip("empty.apk", entries);
        try {
            FlagPresence.scanApks(Collections.singletonList(apk), Collections.singletonList("A__b"));
            fail("expected IOException");
        } catch (IOException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("not a dex file"));
        }
    }

    @Test
    public void scanApks_notAZipSurfacesAsAnError() throws IOException {
        File f = tmp.newFile("notzip.apk");
        try (FileOutputStream out = new FileOutputStream(f)) {
            out.write("definitely not a zip".getBytes(StandardCharsets.UTF_8));
        }
        try {
            FlagPresence.scanApks(Collections.singletonList(f), Collections.singletonList("A__b"));
            fail("expected IOException");
        } catch (IOException expected) {
            // ok
        }
    }

    @Test
    public void scanApks_refusesAnAbsurdlyLargeDexEntry() throws IOException {
        File f = tmp.newFile("huge.apk");
        try (ZipOutputStream z = new ZipOutputStream(new FileOutputStream(f))) {
            z.putNextEntry(new ZipEntry("classes.dex"));
            byte[] chunk = new byte[1 << 20];
            long remaining = FlagPresence.MAX_DEX_BYTES + 1;
            while (remaining > 0) {
                int n = (int) Math.min(chunk.length, remaining);
                z.write(chunk, 0, n);
                remaining -= n;
            }
            z.closeEntry();
        }
        try {
            FlagPresence.scanApks(Collections.singletonList(f), Collections.singletonList("A__b"));
            fail("expected IOException");
        } catch (IOException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("dex too large"));
        }
    }
}
