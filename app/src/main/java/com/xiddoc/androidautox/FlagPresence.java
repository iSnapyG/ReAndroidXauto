package com.xiddoc.androidautox;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Looks for Phenotype flag names inside an installed app's compiled code.
 *
 * <p>Android Auto reads each tweakable setting by its flag name (for example
 * {@code Coolwalk__enabled}), and that name is a string constant in the app's {@code classes*.dex}.
 * If a name is no longer in the string table, Google removed the setting and the tweak that
 * writes it cannot do anything. This class reads each dex file's string table and reports which
 * of the wanted names are present.
 *
 * <p>It is a heuristic, not proof: presence does not guarantee a flag is still honoured, and a
 * name assembled at runtime would not show up. Callers should present results as advisory.
 *
 * <p>Pure {@code java.io}/{@code java.util.zip}; no Android classes, so it is testable on the
 * plain JUnit classloader.
 */
public final class FlagPresence {

    private FlagPresence() {}

    private static final Pattern DEX_ENTRY = Pattern.compile("classes\\d*\\.dex");
    private static final int HEADER_SIZE = 0x70;
    private static final int STRING_IDS_SIZE_OFF = 0x38;
    private static final int STRING_IDS_OFF_OFF = 0x3C;
    /** Refuse a single dex entry bigger than this rather than risk an OOM. */
    static final long MAX_DEX_BYTES = 96L * 1024 * 1024;

    /**
     * Returns the subset of {@code wanted} found in the dex files of {@code apks}.
     *
     * @throws IOException if an APK cannot be read or a dex file is malformed. A failure is
     *         surfaced rather than skipped, so a damaged read can never be mistaken for
     *         "the flag is gone".
     */
    public static Set<String> scanApks(List<File> apks, Collection<String> wanted) throws IOException {
        Set<String> wantedSet = new HashSet<String>(wanted);
        Set<String> found = new HashSet<String>();
        if (wantedSet.isEmpty()) return found;
        for (File apk : apks) {
            try (ZipFile zip = new ZipFile(apk)) {
                Enumeration<? extends ZipEntry> entries = zip.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    if (!DEX_ENTRY.matcher(entry.getName()).matches()) continue;
                    scanDex(readEntry(zip, entry), wantedSet, found);
                    if (found.size() == wantedSet.size()) return found;
                }
            }
        }
        return found;
    }

    private static byte[] readEntry(ZipFile zip, ZipEntry entry) throws IOException {
        // The size comes from the zip's central directory, which a ZipFile always has.
        long declared = entry.getSize();
        if (declared > MAX_DEX_BYTES) throw new IOException("dex too large: " + entry.getName());
        try (InputStream in = zip.getInputStream(entry)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream((int) Math.max(declared, 1L));
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        }
    }

    /**
     * Adds to {@code found} every name in {@code wanted} that is an entry in this dex file's
     * string table.
     *
     * @throws IOException if the data is not a well-formed dex file
     */
    static void scanDex(byte[] dex, Set<String> wanted, Set<String> found) throws IOException {
        if (dex.length < HEADER_SIZE
                || dex[0] != 'd' || dex[1] != 'e' || dex[2] != 'x' || dex[3] != '\n') {
            throw new IOException("not a dex file");
        }
        ByteBuffer buf = ByteBuffer.wrap(dex).order(ByteOrder.LITTLE_ENDIAN);
        long count = buf.getInt(STRING_IDS_SIZE_OFF) & 0xFFFFFFFFL;
        long idsOff = buf.getInt(STRING_IDS_OFF_OFF) & 0xFFFFFFFFL;
        if (idsOff + count * 4 > dex.length) throw new IOException("string table out of range");

        for (int i = 0; i < count; i++) {
            long dataOff = buf.getInt((int) (idsOff + i * 4L)) & 0xFFFFFFFFL;
            if (dataOff >= dex.length) throw new IOException("string data out of range");
            int p = (int) dataOff;
            // Skip the ULEB128 UTF-16 length prefix.
            while (true) {
                if (p >= dex.length) throw new IOException("unterminated string length");
                if ((dex[p++] & 0x80) == 0) break;
            }
            int start = p;
            while (p < dex.length && dex[p] != 0) p++;
            if (p >= dex.length) throw new IOException("unterminated string");
            int len = p - start;
            // Flag names look like Feature__name: cheap length + "__" filter before decoding.
            if (len < 4 || len > 200 || !containsDoubleUnderscore(dex, start, p)) continue;
            String s = new String(dex, start, len, StandardCharsets.UTF_8);
            if (wanted.contains(s)) found.add(s);
        }
    }

    private static boolean containsDoubleUnderscore(byte[] b, int from, int to) {
        for (int i = from; i + 1 < to; i++) {
            if (b[i] == '_' && b[i + 1] == '_') return true;
        }
        return false;
    }
}
