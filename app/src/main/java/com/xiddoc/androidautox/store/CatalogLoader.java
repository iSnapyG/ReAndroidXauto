package com.xiddoc.androidautox.store;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.StringReader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Keeps the store's catalog current and builds the merged app list.
 *
 * <p>Three sources feed it: the catalog bundled with the app ({@code seedJson}), an optional
 * catalog the user points at by URL, and the F-Droid index. The last two are downloaded by
 * {@link #refresh} and cached in {@code dir}; {@link #load} only reads local files.
 */
public final class CatalogLoader {

    public static final String FDROID_INDEX_URL = FdroidIndexParser.REPO_URL + "/index-v2.json";
    public static final long FDROID_MAX_AGE_MILLIS = 7L * 24 * 60 * 60 * 1000;

    static final int MAX_SEED_APPS = 500;
    static final int MAX_REMOTE_APPS = 2000;
    static final int MAX_FDROID_APPS = 20000;
    static final long MAX_REMOTE_BYTES = 2L * 1024 * 1024;
    /** Limit on the decompressed index (it is about 67 MB today). */
    static final long MAX_FDROID_BYTES = 256L * 1024 * 1024;

    static final String REMOTE_FILE = "remote-catalog.json";
    static final String FDROID_FILE = "fdroid-catalog.json";
    /** The apps the user added by hand; kept when the downloads are cleared. */
    static final String CUSTOM_FILE = "my-apps.json";
    static final int MAX_CUSTOM_APPS = CustomApps.MAX_APPS;

    public enum Step { SKIPPED, OK, FAILED }

    /** What a {@link #refresh} did. */
    public static final class RefreshResult {
        public final Step remote;
        public final Step fdroid;
        public final int remoteApps;
        public final int fdroidApps;

        RefreshResult(Step remote, Step fdroid, int remoteApps, int fdroidApps) {
            this.remote = remote;
            this.fdroid = fdroid;
            this.remoteApps = remoteApps;
            this.fdroidApps = fdroidApps;
        }
    }

    private final HttpFetcher http;
    private final File dir;
    private final String seedJson;
    private final DeviceProfile device;

    public CatalogLoader(HttpFetcher http, File dir, String seedJson, DeviceProfile device) {
        this.http = http;
        this.dir = dir;
        this.seedJson = seedJson;
        this.device = device;
    }

    /**
     * The merged list from what is on disk. Damaged or missing caches are ignored; the
     * bundled seed is always used.
     *
     * @param useFdroid whether to use the cached F-Droid index (otherwise F-Droid apps stay
     *                  unresolved and unlisted)
     */
    public List<StoreApp> load(boolean useFdroid) {
        List<StoreApp> catalog = new ArrayList<StoreApp>(customApps());
        File remote = new File(dir, REMOTE_FILE);
        if (remote.isFile()) catalog.addAll(readFile(remote, StoreApp.Origin.CATALOG, MAX_REMOTE_APPS));
        catalog.addAll(readSeed());
        List<StoreApp> fdroid = new ArrayList<StoreApp>();
        File cache = new File(dir, FDROID_FILE);
        if (useFdroid && cache.isFile()) {
            fdroid.addAll(readFile(cache, StoreApp.Origin.FDROID, MAX_FDROID_APPS));
        }
        return CatalogMerge.merge(catalog, fdroid);
    }

    /** The apps the user added by hand (empty if none or the file is damaged). */
    public List<StoreApp> customApps() {
        File file = new File(dir, CUSTOM_FILE);
        if (!file.isFile()) return new ArrayList<StoreApp>();
        return readFile(file, StoreApp.Origin.CATALOG, MAX_CUSTOM_APPS);
    }

    /** Replaces the user's own apps. */
    public void saveCustomApps(List<StoreApp> apps) throws IOException {
        Files.createDirectories(dir.toPath());
        File target = new File(dir, CUSTOM_FILE);
        File tmp = new File(dir, CUSTOM_FILE + ".tmp");
        Writer w = new BufferedWriter(new OutputStreamWriter(
                Files.newOutputStream(tmp.toPath()), StandardCharsets.UTF_8));
        try {
            StoreCatalogParser.write(apps, w);
        } finally {
            w.close();
        }
        Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    /** True if the F-Droid cache is missing or older than a week. */
    public boolean fdroidStale(long nowMillis) {
        File cache = new File(dir, FDROID_FILE);
        return !cache.isFile() || nowMillis - cache.lastModified() > FDROID_MAX_AGE_MILLIS;
    }

    /** True if an F-Droid cache exists on disk. */
    public boolean hasFdroidCache() {
        return new File(dir, FDROID_FILE).isFile();
    }

    /**
     * Downloads what is due. Each part fails on its own, leaving the previous cache in place.
     *
     * @param remoteUrl  the user's catalog URL, or null/empty for none
     * @param useFdroid  whether the user allowed the F-Droid index download
     * @param force      refresh the F-Droid index even if the cache is recent
     */
    public RefreshResult refresh(String remoteUrl, boolean useFdroid, boolean force, long nowMillis) {
        Step remoteStep = Step.SKIPPED;
        int remoteApps = 0;
        if (remoteUrl != null && !remoteUrl.isEmpty()) {
            try {
                remoteApps = fetchRemote(remoteUrl);
                remoteStep = Step.OK;
            } catch (IOException e) {
                remoteStep = Step.FAILED;
            }
        }
        Step fdroidStep = Step.SKIPPED;
        int fdroidApps = 0;
        if (useFdroid && (force || fdroidStale(nowMillis))) {
            try {
                fdroidApps = fetchFdroid();
                fdroidStep = Step.OK;
            } catch (IOException e) {
                fdroidStep = Step.FAILED;
            }
        }
        return new RefreshResult(remoteStep, fdroidStep, remoteApps, fdroidApps);
    }

    /** Forgets everything downloaded (the bundled seed and the user's own apps are unaffected). */
    public void clearCaches() throws IOException {
        Files.deleteIfExists(new File(dir, REMOTE_FILE).toPath());
        Files.deleteIfExists(new File(dir, FDROID_FILE).toPath());
    }

    private int fetchRemote(String url) throws IOException {
        if (!StoreValidation.isHttpsUrl(url)) throw new IOException("catalog address must be https");
        HttpResponse response = http.get(url, "application/json");
        try {
            byte[] bytes = Streams.readBounded(response.body, MAX_REMOTE_BYTES);
            // Parse before saving so a bad download never replaces a good cache.
            StoreCatalogParser.Result parsed = StoreCatalogParser.parse(
                    new InputStreamReader(new java.io.ByteArrayInputStream(bytes), StandardCharsets.UTF_8),
                    StoreApp.Origin.CATALOG, MAX_REMOTE_APPS);
            replace(new File(dir, REMOTE_FILE), bytes);
            return parsed.apps.size();
        } finally {
            response.close();
        }
    }

    private int fetchFdroid() throws IOException {
        HttpResponse response = http.get(FDROID_INDEX_URL, "application/json");
        try {
            InputStream bounded = new Streams.Bounded(response.body, MAX_FDROID_BYTES);
            List<StoreApp> apps = FdroidIndexParser.parse(
                    new InputStreamReader(bounded, StandardCharsets.UTF_8), device, MAX_FDROID_APPS);
            File target = new File(dir, FDROID_FILE);
            File tmp = new File(dir, FDROID_FILE + ".tmp");
            Files.createDirectories(dir.toPath());
            Writer w = new BufferedWriter(new OutputStreamWriter(
                    Files.newOutputStream(tmp.toPath()), StandardCharsets.UTF_8));
            try {
                StoreCatalogParser.write(apps, w);
            } finally {
                w.close();
            }
            Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return apps.size();
        } finally {
            response.close();
        }
    }

    private void replace(File target, byte[] bytes) throws IOException {
        Files.createDirectories(dir.toPath());
        File tmp = new File(dir, target.getName() + ".tmp");
        Files.write(tmp.toPath(), bytes);
        Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    private List<StoreApp> readSeed() {
        try {
            return StoreCatalogParser.parse(new StringReader(seedJson), StoreApp.Origin.CATALOG,
                    MAX_SEED_APPS).apps;
        } catch (IOException e) {
            return new ArrayList<StoreApp>();
        }
    }

    private static List<StoreApp> readFile(File file, StoreApp.Origin origin, int max) {
        try {
            Reader r = new InputStreamReader(Files.newInputStream(file.toPath()), StandardCharsets.UTF_8);
            try {
                return StoreCatalogParser.parse(r, origin, max).apps;
            } finally {
                r.close();
            }
        } catch (IOException e) {
            return new ArrayList<StoreApp>();
        }
    }
}
