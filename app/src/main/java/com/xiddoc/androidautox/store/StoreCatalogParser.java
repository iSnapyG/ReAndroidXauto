package com.xiddoc.androidautox.store;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Reads and writes the store's catalog format.
 *
 * <pre>
 * {
 *   "version": 1,
 *   "apps": [
 *     { "name": "AntennaPod", "summary": "Podcast player", "package": "de.danoeh.antennapod",
 *       "kind": "car",                      // "car", "autox" or "other"
 *       "categories": ["Podcast"],          // optional
 *       "signer": ["&lt;sha256 of signing cert&gt;"],  // required, except for "fdroid"
 *       "fdroid": true },                   // look the APK up in the F-Droid index
 *
 *     { "name": "...", "package": "...", "signer": ["..."],
 *       "github": "owner/repo", "asset": "arm64.*\\.apk" },        // newest GitHub release
 *
 *     { "name": "...", "package": "...", "signer": ["..."],
 *       "url": "https://.../app.apk", "sha256": "...", "size": 123,
 *       "versionName": "1.2", "versionCode": 12 }                  // fixed APK
 *   ]
 * }
 * </pre>
 *
 * Exactly one source per app. An app that is invalid is skipped (and counted) rather than
 * failing the whole file; a file that is not a catalog at all throws.
 */
public final class StoreCatalogParser {

    public static final int FORMAT_VERSION = 1;

    private StoreCatalogParser() {}

    public static final class Result {
        public final List<StoreApp> apps;
        public final int skipped;

        Result(List<StoreApp> apps, int skipped) {
            this.apps = Collections.unmodifiableList(apps);
            this.skipped = skipped;
        }
    }

    /**
     * @param origin  stamped on every app, whatever the file claims
     * @param maxApps apps beyond this many are ignored (and counted as skipped)
     */
    public static Result parse(Reader in, StoreApp.Origin origin, int maxApps) throws IOException {
        JsonReader r = new JsonReader(in);
        try {
            return read(r, origin, maxApps);
        } catch (IllegalStateException e) {
            throw new StoreFormatException("not a catalog file");
        }
    }

    private static Result read(JsonReader r, StoreApp.Origin origin, int maxApps) throws IOException {
        List<StoreApp> apps = new ArrayList<StoreApp>();
        int skipped = 0;
        long version = -1;
        boolean sawApps = false;
        r.beginObject();
        while (r.hasNext()) {
            String key = r.nextName();
            if (key.equals("version")) {
                version = JsonRead.number(r, -1);
            } else if (key.equals("apps") && r.peek() == JsonToken.BEGIN_ARRAY) {
                sawApps = true;
                r.beginArray();
                while (r.hasNext()) {
                    StoreApp app = readApp(r, origin);
                    if (app == null || apps.size() >= maxApps) {
                        skipped++;
                    } else {
                        apps.add(app);
                    }
                }
                r.endArray();
            } else {
                r.skipValue();
            }
        }
        r.endObject();
        if (version != FORMAT_VERSION) throw new StoreFormatException("unsupported catalog version");
        if (!sawApps) throw new StoreFormatException("catalog has no apps list");
        return new Result(apps, skipped);
    }

    /** One app, or null if it is not valid. Always consumes the whole value. */
    private static StoreApp readApp(JsonReader r, StoreApp.Origin origin) throws IOException {
        if (r.peek() != JsonToken.BEGIN_OBJECT) {
            r.skipValue();
            return null;
        }
        String name = null, summary = null, pkg = null, kind = null, versionName = null;
        String url = null, sha256 = null, github = null, asset = null;
        List<String> categories = new ArrayList<String>();
        List<String> signers = new ArrayList<String>();
        long versionCode = 0, size = 0;
        boolean fdroid = false;

        r.beginObject();
        while (r.hasNext()) {
            String key = r.nextName();
            switch (key) {
                case "name": name = JsonRead.string(r); break;
                case "summary": summary = JsonRead.string(r); break;
                case "package": pkg = JsonRead.string(r); break;
                case "kind": kind = JsonRead.string(r); break;
                case "categories": categories = JsonRead.strings(r, 20); break;
                case "signer": signers = JsonRead.strings(r, 8); break;
                case "versionName": versionName = JsonRead.string(r); break;
                case "versionCode": versionCode = JsonRead.number(r, 0); break;
                case "size": size = JsonRead.number(r, 0); break;
                case "url": url = JsonRead.string(r); break;
                case "sha256": sha256 = JsonRead.string(r); break;
                case "github": github = JsonRead.string(r); break;
                case "asset": asset = JsonRead.string(r); break;
                case "fdroid": fdroid = JsonRead.bool(r); break;
                default: r.skipValue(); break;
            }
        }
        r.endObject();

        try {
            int sources = (url != null ? 1 : 0) + (github != null ? 1 : 0) + (fdroid ? 1 : 0);
            if (sources != 1) throw new IllegalArgumentException("exactly one source is required");
            ApkSource source = url != null ? ApkSource.direct(url, sha256, size)
                    : github != null ? ApkSource.github(github, asset)
                    : ApkSource.fdroid();
            return new StoreApp(name, summary, pkg, parseKind(kind), origin, categories, signers,
                    source, versionName, versionCode);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static StoreApp.Kind parseKind(String kind) {
        if (kind == null || kind.equals("other")) return StoreApp.Kind.OTHER;
        if (kind.equals("car")) return StoreApp.Kind.CAR;
        if (kind.equals("autox")) return StoreApp.Kind.AUTOX;
        throw new IllegalArgumentException("unknown kind");
    }

    private static void writeSource(JsonWriter w, ApkSource source) throws IOException {
        switch (source.type) {
            case DIRECT:
                w.name("size").value(source.size);
                w.name("url").value(source.url);
                w.name("sha256").value(source.sha256);
                break;
            case GITHUB:
                w.name("github").value(source.repo);
                w.name("asset").value(source.assetPattern);
                break;
            default:
                w.name("fdroid").value(true);
                break;
        }
    }

    /** Writes apps in this format (the on-device copy of the F-Droid index, and the user's own apps). */
    public static void write(List<StoreApp> apps, Writer out) throws IOException {
        JsonWriter w = new JsonWriter(out);
        w.beginObject();
        w.name("version").value(FORMAT_VERSION);
        w.name("apps").beginArray();
        for (StoreApp app : apps) {
            w.beginObject();
            w.name("name").value(app.name);
            w.name("summary").value(app.summary);
            w.name("package").value(app.packageName);
            w.name("kind").value(app.kind.name().toLowerCase(java.util.Locale.ROOT));
            w.name("categories").beginArray();
            for (String c : app.categories) w.value(c);
            w.endArray();
            w.name("signer").beginArray();
            for (String s : app.signers) w.value(s);
            w.endArray();
            w.name("versionName").value(app.versionName);
            w.name("versionCode").value(app.versionCode);
            writeSource(w, app.source);
            w.endObject();
        }
        w.endArray();
        w.endObject();
        w.flush();
    }
}
