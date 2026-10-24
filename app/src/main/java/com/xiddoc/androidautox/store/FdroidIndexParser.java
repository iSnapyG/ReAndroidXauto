package com.xiddoc.androidautox.store;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Streams F-Droid's {@code index-v2.json} (about 67 MB) and keeps, for each app, the newest
 * version this phone can actually install. Nothing but the few fields the store needs is ever
 * held in memory.
 *
 * <p>Each app's APK is addressed as {@code https://f-droid.org/repo/<file>}, with the SHA-256
 * and signing-certificate fingerprints the index lists for that exact file.
 */
public final class FdroidIndexParser {

    public static final String REPO_URL = "https://f-droid.org/repo";

    private static final Pattern APK_FILE = Pattern.compile("/[A-Za-z0-9._-]{1,200}\\.apk");

    private FdroidIndexParser() {}

    /** @param maxApps the parse stops keeping apps after this many */
    public static List<StoreApp> parse(Reader in, DeviceProfile device, int maxApps) throws IOException {
        JsonReader r = new JsonReader(in);
        try {
            return read(r, device, maxApps);
        } catch (IllegalStateException e) {
            throw new StoreFormatException("not an F-Droid index");
        }
    }

    private static List<StoreApp> read(JsonReader r, DeviceProfile device, int maxApps) throws IOException {
        List<StoreApp> apps = new ArrayList<StoreApp>();
        boolean sawPackages = false;
        r.beginObject();
        while (r.hasNext()) {
            String key = r.nextName();
            if (key.equals("packages") && r.peek() == JsonToken.BEGIN_OBJECT) {
                sawPackages = true;
                r.beginObject();
                while (r.hasNext()) {
                    String pkg = r.nextName();
                    StoreApp app = readPackage(r, pkg, device);
                    if (app != null && apps.size() < maxApps) apps.add(app);
                }
                r.endObject();
            } else {
                r.skipValue();
            }
        }
        r.endObject();
        if (!sawPackages) throw new StoreFormatException("index has no packages");
        return apps;
    }

    /** What we keep from one {@code versions} entry. */
    private static final class Version {
        String file;
        String sha256;
        long size;
        String versionName;
        long versionCode;
        int minSdk;
        int maxSdk;
        List<String> nativeCode = Collections.emptyList();
        List<String> signers = Collections.emptyList();
    }

    private static StoreApp readPackage(JsonReader r, String pkg, DeviceProfile device) throws IOException {
        if (r.peek() != JsonToken.BEGIN_OBJECT) {
            r.skipValue();
            return null;
        }
        String name = null, summary = null;
        List<String> categories = Collections.emptyList();
        Version best = null;
        r.beginObject();
        while (r.hasNext()) {
            String key = r.nextName();
            if (key.equals("metadata") && r.peek() == JsonToken.BEGIN_OBJECT) {
                r.beginObject();
                while (r.hasNext()) {
                    String field = r.nextName();
                    if (field.equals("name")) name = JsonRead.localized(r);
                    else if (field.equals("summary")) summary = JsonRead.localized(r);
                    else if (field.equals("categories")) categories = JsonRead.strings(r, 20);
                    else r.skipValue();
                }
                r.endObject();
            } else if (key.equals("versions") && r.peek() == JsonToken.BEGIN_OBJECT) {
                best = readVersions(r, device);
            } else {
                r.skipValue();
            }
        }
        r.endObject();
        if (best == null) return null;
        try {
            return new StoreApp(name, summary, pkg, StoreApp.Kind.OTHER, StoreApp.Origin.FDROID,
                    categories, best.signers,
                    ApkSource.direct(REPO_URL + best.file, best.sha256, best.size),
                    best.versionName, best.versionCode);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** The installable version with the highest version code, or null. */
    private static Version readVersions(JsonReader r, DeviceProfile device) throws IOException {
        Version best = null;
        r.beginObject();
        while (r.hasNext()) {
            r.nextName(); // the version's hash key
            Version v = readVersion(r);
            if (v == null || !usable(v, device)) continue;
            if (best == null || v.versionCode > best.versionCode) best = v;
        }
        r.endObject();
        return best;
    }

    private static boolean usable(Version v, DeviceProfile device) {
        return v.file != null
                && APK_FILE.matcher(v.file).matches()
                && StoreValidation.sha256OrNull(v.sha256) != null
                && !v.signers.isEmpty()
                && v.versionCode > 0
                && device.accepts(v.minSdk, v.maxSdk, v.nativeCode);
    }

    private static Version readVersion(JsonReader r) throws IOException {
        if (r.peek() != JsonToken.BEGIN_OBJECT) {
            r.skipValue();
            return null;
        }
        Version v = new Version();
        r.beginObject();
        while (r.hasNext()) {
            String key = r.nextName();
            if (key.equals("file") && r.peek() == JsonToken.BEGIN_OBJECT) {
                r.beginObject();
                while (r.hasNext()) {
                    String field = r.nextName();
                    if (field.equals("name")) v.file = JsonRead.string(r);
                    else if (field.equals("sha256")) v.sha256 = JsonRead.string(r);
                    else if (field.equals("size")) v.size = JsonRead.number(r, 0);
                    else r.skipValue();
                }
                r.endObject();
            } else if (key.equals("manifest") && r.peek() == JsonToken.BEGIN_OBJECT) {
                readManifest(r, v);
            } else {
                r.skipValue();
            }
        }
        r.endObject();
        return v;
    }

    private static void readManifest(JsonReader r, Version v) throws IOException {
        r.beginObject();
        while (r.hasNext()) {
            String key = r.nextName();
            if (key.equals("versionName")) {
                v.versionName = JsonRead.string(r);
            } else if (key.equals("versionCode")) {
                v.versionCode = JsonRead.number(r, 0);
            } else if (key.equals("nativecode")) {
                v.nativeCode = JsonRead.strings(r, 16);
            } else if (key.equals("usesSdk") && r.peek() == JsonToken.BEGIN_OBJECT) {
                r.beginObject();
                while (r.hasNext()) {
                    String field = r.nextName();
                    if (field.equals("minSdkVersion")) v.minSdk = (int) JsonRead.number(r, 0);
                    else if (field.equals("maxSdkVersion")) v.maxSdk = (int) JsonRead.number(r, 0);
                    else r.skipValue();
                }
                r.endObject();
            } else if (key.equals("signer") && r.peek() == JsonToken.BEGIN_OBJECT) {
                r.beginObject();
                while (r.hasNext()) {
                    if (r.nextName().equals("sha256")) v.signers = JsonRead.strings(r, 8);
                    else r.skipValue();
                }
                r.endObject();
            } else {
                r.skipValue();
            }
        }
        r.endObject();
    }
}
