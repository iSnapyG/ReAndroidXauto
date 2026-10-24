package com.xiddoc.androidautox.store;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CatalogLoaderTest {

    private static final String REMOTE_URL = "https://example.com/catalog.json";
    private static final long NOW = 1_700_000_000_000L;

    private static final String SEED = "{\"version\":1,\"apps\":["
            + "{\"name\":\"Player\",\"package\":\"org.example.player\",\"kind\":\"car\",\"signer\":[\""
            + StoreFixtures.SIG_A + "\"],\"fdroid\":true},"
            + "{\"name\":\"Maps\",\"package\":\"org.example.maps\",\"kind\":\"autox\",\"signer\":[\""
            + StoreFixtures.SIG_B + "\"],\"fdroid\":true}]}";

    private static final String REMOTE = "{\"version\":1,\"apps\":["
            + "{\"name\":\"Remote Player\",\"package\":\"org.example.player\",\"kind\":\"autox\",\"signer\":[\""
            + StoreFixtures.SIG_A + "\"],\"fdroid\":true},"
            + "{\"name\":\"Extra\",\"package\":\"org.example.extra\",\"signer\":[\"" + StoreFixtures.SIG_C
            + "\"],\"url\":\"https://example.com/extra.apk\",\"sha256\":\"" + StoreFixtures.repeat('4') + "\"}]}";

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private final StoreFixtures.FakeHttp http = new StoreFixtures.FakeHttp();
    private final DeviceProfile device = new DeviceProfile(34, Arrays.asList("arm64-v8a"));

    private CatalogLoader loader(File dir, String seed) {
        return new CatalogLoader(http, dir, seed, device);
    }

    private CatalogLoader loader() {
        return loader(new File(tmp.getRoot(), "cache/store"), SEED);
    }

    private static Map<String, StoreApp> byPackage(List<StoreApp> apps) {
        Map<String, StoreApp> out = new HashMap<String, StoreApp>();
        for (StoreApp a : apps) out.put(a.packageName, a);
        return out;
    }

    @Test
    public void seedAloneListsUnresolvedReferences() {
        List<StoreApp> apps = loader().load(true);
        assertEquals(2, apps.size());
        for (StoreApp a : apps) assertEquals(ApkSource.Type.FDROID, a.source.type);
    }

    @Test
    public void damagedSeedGivesAnEmptyStore() {
        assertTrue(loader(new File(tmp.getRoot(), "c"), "not json").load(true).isEmpty());
    }

    @Test
    public void fdroidIndexIsDownloadedCachedAndMerged() {
        http.serve(CatalogLoader.FDROID_INDEX_URL, StoreFixtures.fdroidIndex());
        CatalogLoader loader = loader();
        assertFalse(loader.hasFdroidCache());
        assertTrue(loader.fdroidStale(NOW));

        CatalogLoader.RefreshResult r = loader.refresh(null, true, false, NOW);
        assertEquals(CatalogLoader.Step.SKIPPED, r.remote);
        assertEquals(CatalogLoader.Step.OK, r.fdroid);
        assertEquals(2, r.fdroidApps);
        assertTrue(loader.hasFdroidCache());

        Map<String, StoreApp> merged = byPackage(loader.load(true));
        assertEquals(2, merged.size());
        StoreApp player = merged.get("org.example.player");
        assertEquals("Player", player.name);
        assertEquals(StoreApp.Kind.CAR, player.kind);
        assertEquals(ApkSource.Type.DIRECT, player.source.type);
        assertEquals(2, player.versionCode);
        assertEquals("https://f-droid.org/repo/org.example.player_2.apk", player.source.url);

        // opting out of F-Droid leaves the references unresolved
        for (StoreApp a : loader.load(false)) assertEquals(ApkSource.Type.FDROID, a.source.type);
    }

    @Test
    public void fdroidRefreshHonoursAgeForceAndConsent() {
        http.serve(CatalogLoader.FDROID_INDEX_URL, StoreFixtures.fdroidIndex());
        CatalogLoader loader = loader();
        loader.refresh(null, true, false, NOW);
        File cache = new File(tmp.getRoot(), "cache/store/" + CatalogLoader.FDROID_FILE);
        long written = cache.lastModified();
        assertFalse(loader.fdroidStale(written + 1000));
        assertTrue(loader.fdroidStale(written + CatalogLoader.FDROID_MAX_AGE_MILLIS + 1000));

        int before = http.requested.size();
        assertEquals(CatalogLoader.Step.SKIPPED, loader.refresh(null, true, false, written + 1000).fdroid);
        assertEquals(before, http.requested.size());
        assertEquals(CatalogLoader.Step.OK, loader.refresh(null, true, true, written + 1000).fdroid);
        assertEquals(CatalogLoader.Step.OK,
                loader.refresh(null, true, false, written + CatalogLoader.FDROID_MAX_AGE_MILLIS + 1000).fdroid);
        // without the user's consent nothing is downloaded, even when forced
        before = http.requested.size();
        assertEquals(CatalogLoader.Step.SKIPPED, loader.refresh(null, false, true, NOW).fdroid);
        assertEquals(before, http.requested.size());
    }

    @Test
    public void fdroidFailureKeepsTheOldCache() throws IOException {
        http.serve(CatalogLoader.FDROID_INDEX_URL, StoreFixtures.fdroidIndex());
        CatalogLoader loader = loader();
        loader.refresh(null, true, false, NOW);
        File cache = new File(tmp.getRoot(), "cache/store/" + CatalogLoader.FDROID_FILE);
        byte[] before = Files.readAllBytes(cache.toPath());

        http.bodies.clear();                                  // network error
        assertEquals(CatalogLoader.Step.FAILED, loader.refresh(null, true, true, NOW).fdroid);
        http.serve(CatalogLoader.FDROID_INDEX_URL, "{\"nothing\":1}");   // not an index
        assertEquals(CatalogLoader.Step.FAILED, loader.refresh(null, true, true, NOW).fdroid);
        assertTrue(Arrays.equals(before, Files.readAllBytes(cache.toPath())));
    }

    @Test
    public void remoteCatalogOverridesTheSeed() {
        http.serve(REMOTE_URL, REMOTE);
        http.serve(CatalogLoader.FDROID_INDEX_URL, StoreFixtures.fdroidIndex());
        CatalogLoader loader = loader();
        CatalogLoader.RefreshResult r = loader.refresh(REMOTE_URL, true, false, NOW);
        assertEquals(CatalogLoader.Step.OK, r.remote);
        assertEquals(2, r.remoteApps);

        Map<String, StoreApp> merged = byPackage(loader.load(true));
        assertEquals(3, merged.size());
        assertEquals("Remote Player", merged.get("org.example.player").name);
        assertEquals(StoreApp.Kind.AUTOX, merged.get("org.example.player").kind);
        assertEquals("Extra", merged.get("org.example.extra").name);
        assertEquals("Maps", merged.get("org.example.maps").name);
    }

    @Test
    public void badRemoteCatalogsAreRejected() throws IOException {
        CatalogLoader loader = loader();
        assertEquals(CatalogLoader.Step.SKIPPED, loader.refresh(null, false, false, NOW).remote);
        assertEquals(CatalogLoader.Step.SKIPPED, loader.refresh("", false, false, NOW).remote);
        assertEquals(CatalogLoader.Step.FAILED, loader.refresh("http://example.com/c.json", false, false, NOW).remote);
        assertEquals(CatalogLoader.Step.FAILED, loader.refresh(REMOTE_URL, false, false, NOW).remote);   // 404

        http.serve(REMOTE_URL, "this is not json");
        assertEquals(CatalogLoader.Step.FAILED, loader.refresh(REMOTE_URL, false, false, NOW).remote);
        assertFalse(new File(tmp.getRoot(), "cache/store/" + CatalogLoader.REMOTE_FILE).exists());

        http.serve(REMOTE_URL, new byte[(int) CatalogLoader.MAX_REMOTE_BYTES + 1]);
        assertEquals(CatalogLoader.Step.FAILED, loader.refresh(REMOTE_URL, false, false, NOW).remote);

        // a good catalog, then a bad one: the good one stays
        http.serve(REMOTE_URL, REMOTE);
        assertEquals(CatalogLoader.Step.OK, loader.refresh(REMOTE_URL, false, false, NOW).remote);
        http.serve(REMOTE_URL, "{\"version\":9}");
        assertEquals(CatalogLoader.Step.FAILED, loader.refresh(REMOTE_URL, false, false, NOW).remote);
        assertEquals(3, loader.load(false).size());
    }

    @Test
    public void damagedCachesAreIgnored() throws IOException {
        File dir = new File(tmp.getRoot(), "cache/store");
        assertTrue(dir.mkdirs());
        Files.write(new File(dir, CatalogLoader.REMOTE_FILE).toPath(), "garbage".getBytes());
        Files.write(new File(dir, CatalogLoader.FDROID_FILE).toPath(), "garbage".getBytes());
        List<StoreApp> apps = loader(dir, SEED).load(true);
        assertEquals(2, apps.size());
        for (StoreApp a : apps) assertEquals(ApkSource.Type.FDROID, a.source.type);
    }

    @Test
    public void clearingCachesForgetsDownloads() throws IOException {
        http.serve(REMOTE_URL, REMOTE);
        http.serve(CatalogLoader.FDROID_INDEX_URL, StoreFixtures.fdroidIndex());
        CatalogLoader loader = loader();
        loader.clearCaches();                                  // nothing there yet: fine
        loader.refresh(REMOTE_URL, true, false, NOW);
        assertEquals(3, loader.load(true).size());
        loader.clearCaches();
        assertFalse(loader.hasFdroidCache());
        assertEquals(2, loader.load(true).size());
    }
}
