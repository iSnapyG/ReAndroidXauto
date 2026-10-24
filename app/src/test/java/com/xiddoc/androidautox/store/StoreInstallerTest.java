package com.xiddoc.androidautox.store;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StoreInstallerTest {

    private static final String PKG = "org.example.app";
    private static final String URL = "https://example.com/" + PKG + ".apk";
    private static final byte[] APK = StoreFixtures.bytes("pretend this is an apk");
    private static final String APK_SHA = Hashing.sha256Hex(APK);

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private final StoreFixtures.FakeHttp http = new StoreFixtures.FakeHttp();
    private final StoreFixtures.MemoryRecords records = new StoreFixtures.MemoryRecords();
    private final Map<String, Long> phone = new HashMap<String, Long>();
    private final List<String> commands = new ArrayList<String>();
    private final List<String> stages = new ArrayList<String>();
    private List<String> pmOutput = Collections.singletonList("Success");
    private boolean root = true;
    private ApkInspector.Info info = new ApkInspector.Info(PKG, 10, Collections.singletonList(StoreFixtures.SIG_A));
    private boolean cancel;
    private File work;
    private File inspected;

    @Before
    public void setUp() {
        work = new File(tmp.getRoot(), "work");
        http.serve(URL, APK);
    }

    private StoreInstaller installer(long maxBytes) {
        return new StoreInstaller(http, work,
                new ApkInspector() {
                    @Override
                    public Info inspect(File apk) {
                        assertTrue(apk.isFile());
                        inspected = apk;
                        return info;
                    }
                },
                new InstalledApps() {
                    @Override
                    public long versionCode(String packageName) {
                        Long v = phone.get(packageName);
                        return v == null ? -1 : v;
                    }
                },
                new RootShell() {
                    @Override
                    public boolean isRoot() {
                        return root;
                    }

                    @Override
                    public List<String> run(String command) {
                        commands.add(command);
                        return pmOutput;
                    }
                },
                records,
                new java.util.function.LongSupplier() {
                    @Override
                    public long getAsLong() {
                        return 1234;
                    }
                },
                maxBytes);
    }

    private StoreInstaller installer() {
        return installer(StoreInstaller.MAX_APK_BYTES);
    }

    private final StoreInstaller.Listener listener = new StoreInstaller.Listener() {
        @Override
        public void onStage(StoreInstaller.Stage stage, int percent) {
            stages.add(stage + ":" + percent);
        }

        @Override
        public boolean cancelled() {
            return cancel;
        }
    };

    private StoreApp app() {
        return StoreFixtures.direct(PKG, APK_SHA, StoreFixtures.SIG_A);
    }

    private void assertNothingInstalled() {
        assertTrue(commands.isEmpty());
        assertNull(records.get(PKG));
        assertFalse(new File(work, PKG + ".apk").exists());
    }

    @Test
    public void installsAFixedUrlApp() {
        StoreInstaller.Result r = installer().install(app(), listener);
        assertTrue(r.ok());
        assertEquals(StoreInstaller.Outcome.OK, r.outcome);
        assertEquals(Collections.singletonList(
                "pm install -r -S " + APK.length + " < " + new File(work, PKG + ".apk").getAbsolutePath()), commands);
        InstallRecord rec = records.get(PKG);
        assertEquals(10, rec.versionCode);
        assertEquals("1.0", rec.versionName);
        assertEquals(1234, rec.installedAtMillis);
        assertFalse(new File(work, PKG + ".apk").exists());                  // temp file is gone
        assertEquals(Arrays.asList("RESOLVING:-1", "DOWNLOADING:0", "DOWNLOADING:99", "VERIFYING:-1",
                "INSTALLING:-1"), stages);
        assertEquals("application/vnd.android.package-archive, */*", http.accepts.get(0));
    }

    @Test
    public void unknownLengthMeansNoPercentage() {
        http.lengths.put(URL, -1L);
        assertTrue(installer().install(app(), listener).ok());
        assertTrue(stages.contains("DOWNLOADING:-1"));
    }

    @Test
    public void expectedSizeFillsInForAnUnknownLength() {
        http.lengths.put(URL, -1L);
        StoreApp sized = new StoreApp("n", "", PKG, StoreApp.Kind.OTHER, StoreApp.Origin.CATALOG,
                Collections.<String>emptyList(), Collections.singletonList(StoreFixtures.SIG_A),
                ApkSource.direct(URL, APK_SHA, APK.length), "1.0", 1);
        assertTrue(installer().install(sized, listener).ok());
        assertTrue(stages.contains("DOWNLOADING:99"));
    }

    @Test
    public void updatesOverAnOlderVersion() {
        phone.put(PKG, 5L);
        assertTrue(installer().install(app(), listener).ok());
        assertEquals(1, commands.size());
    }

    @Test
    public void refusesProtectedPackagesAndMissingRoot() {
        StoreApp gms = StoreFixtures.direct("com.google.android.gms", APK_SHA, StoreFixtures.SIG_A);
        assertEquals(StoreInstaller.Outcome.PROTECTED, installer().install(gms, listener).outcome);
        root = false;
        assertEquals(StoreInstaller.Outcome.NO_ROOT, installer().install(app(), listener).outcome);
        assertNothingInstalled();
        assertTrue(stages.isEmpty());
    }

    @Test
    public void fdroidReferencesCannotBeInstalledUntilResolved() {
        StoreApp ref = StoreFixtures.fdroidRef("Ref", PKG, StoreApp.Kind.CAR, StoreFixtures.SIG_A);
        assertEquals(StoreInstaller.Outcome.NOT_RESOLVED, installer().install(ref, listener).outcome);
        assertNothingInstalled();
    }

    private void serveRelease(String json) {
        http.serve("https://api.github.com/repos/owner/repo/releases/latest", json);
    }

    private static final String ASSET_URL = "https://github.com/owner/repo/releases/download/v2.0/app.apk";

    private static String release(String assets) {
        return "{\"tag_name\":\"v2.0\",\"assets\":[" + assets + "]}";
    }

    @Test
    public void installsTheLatestGithubRelease() {
        serveRelease(release("{\"name\":\"app.apk\",\"browser_download_url\":\"" + ASSET_URL
                + "\",\"size\":22,\"digest\":\"sha256:" + APK_SHA + "\"}"));
        http.serve(ASSET_URL, APK);
        assertTrue(installer().install(StoreFixtures.github(PKG, StoreFixtures.SIG_A), listener).ok());
        assertEquals("v2.0", records.get(PKG).versionName);
        assertEquals("application/vnd.github+json", http.accepts.get(0));
    }

    @Test
    public void githubDigestIsEnforcedWhenPresent() {
        serveRelease(release("{\"name\":\"app.apk\",\"browser_download_url\":\"" + ASSET_URL
                + "\",\"digest\":\"sha256:" + StoreFixtures.repeat('0') + "\"}"));
        http.serve(ASSET_URL, APK);
        assertEquals(StoreInstaller.Outcome.HASH_MISMATCH,
                installer().install(StoreFixtures.github(PKG, StoreFixtures.SIG_A), listener).outcome);
        assertNothingInstalled();
    }

    @Test
    public void githubProblemsMeanNoDownload() {
        StoreApp gh = StoreFixtures.github(PKG, StoreFixtures.SIG_A);
        StoreInstaller.Result missing = installer().install(gh, listener);          // API 404
        assertEquals(StoreInstaller.Outcome.NO_DOWNLOAD, missing.outcome);
        assertTrue(missing.detail, missing.detail.contains("404"));

        serveRelease(release("{\"name\":\"app.zip\",\"browser_download_url\":\"" + ASSET_URL + "\"}"));
        assertEquals(StoreInstaller.Outcome.NO_DOWNLOAD, installer().install(gh, listener).outcome);

        serveRelease("[]");
        assertEquals(StoreInstaller.Outcome.NO_DOWNLOAD, installer().install(gh, listener).outcome);
        assertNothingInstalled();
    }

    @Test
    public void downloadProblems() throws IOException {
        http.bodies.clear();
        StoreInstaller.Result r = installer().install(app(), listener);
        assertEquals(StoreInstaller.Outcome.DOWNLOAD_FAILED, r.outcome);
        assertTrue(r.detail, r.detail.contains("404"));

        // the working directory cannot be created because a file is in the way
        File blocker = tmp.newFile("blocker");
        work = blocker;
        http.serve(URL, APK);
        assertEquals(StoreInstaller.Outcome.DOWNLOAD_FAILED, installer().install(app(), listener).outcome);
        assertTrue(commands.isEmpty());
    }

    @Test
    public void tooLargeDownloadsAreStopped() {
        assertEquals(StoreInstaller.Outcome.TOO_LARGE, installer(5).install(app(), listener).outcome);
        assertNothingInstalled();
    }

    @Test
    public void downloadsCanBeCancelled() {
        cancel = true;
        assertEquals(StoreInstaller.Outcome.CANCELLED, installer().install(app(), listener).outcome);
        assertNothingInstalled();
    }

    @Test
    public void verificationFailuresStopTheInstall() {
        assertEquals(StoreInstaller.Outcome.HASH_MISMATCH, installer().install(
                StoreFixtures.direct(PKG, StoreFixtures.repeat('0'), StoreFixtures.SIG_A), listener).outcome);

        info = new ApkInspector.Info("org.other.app", 10, Collections.singletonList(StoreFixtures.SIG_A));
        assertEquals(StoreInstaller.Outcome.WRONG_PACKAGE, installer().install(app(), listener).outcome);

        info = new ApkInspector.Info(PKG, 10, Collections.singletonList(StoreFixtures.SIG_B));
        assertEquals(StoreInstaller.Outcome.SIGNER_MISMATCH, installer().install(app(), listener).outcome);

        info = new ApkInspector.Info(PKG, 10, Collections.<String>emptyList());
        assertEquals(StoreInstaller.Outcome.UNSIGNED, installer().install(app(), listener).outcome);

        info = new ApkInspector.Info(PKG, 10, Collections.singletonList(StoreFixtures.SIG_A));
        phone.put(PKG, 20L);
        assertEquals(StoreInstaller.Outcome.DOWNGRADE, installer().install(app(), listener).outcome);

        info = null;
        phone.clear();
        assertEquals(StoreInstaller.Outcome.UNREADABLE_APK, installer().install(app(), listener).outcome);
        assertNotNull(inspected);
        assertNothingInstalled();
    }

    @Test
    public void everyVerdictHasAnOutcome() {
        for (ApkVerifier.Verdict v : ApkVerifier.Verdict.values()) {
            assertEquals(v.name(), StoreInstaller.Outcome.valueOf(v.name()).name());
        }
    }

    @Test
    public void packageManagerFailureIsReported() {
        pmOutput = Arrays.asList("Performing Streamed Install", "Failure [INSTALL_FAILED_INSUFFICIENT_STORAGE]");
        StoreInstaller.Result r = installer().install(app(), listener);
        assertEquals(StoreInstaller.Outcome.INSTALL_FAILED, r.outcome);
        assertEquals("INSTALL_FAILED_INSUFFICIENT_STORAGE", r.detail);
        assertFalse(r.ok());
        assertNull(records.get(PKG));
        assertFalse(new File(work, PKG + ".apk").exists());
    }

    @Test
    public void uninstallsWhatTheStoreInstalled() {
        records.put(new InstallRecord(PKG, "App", 10, "1.0", 1));
        phone.put(PKG, 10L);
        pmOutput = Collections.singletonList("Success");
        StoreInstaller.Result r = installer().uninstall(PKG, listener);
        assertTrue(r.ok());
        assertEquals(Collections.singletonList("pm uninstall " + PKG), commands);
        assertNull(records.get(PKG));
        assertEquals(Collections.singletonList("REMOVING:-1"), stages);
    }

    @Test
    public void uninstallRefusals() {
        assertEquals(StoreInstaller.Outcome.INVALID, installer().uninstall("bad; reboot", listener).outcome);
        assertEquals(StoreInstaller.Outcome.INVALID, installer().uninstall(null, listener).outcome);

        records.put(new InstallRecord("com.android.settings", "S", 1, "1", 1));
        assertEquals(StoreInstaller.Outcome.PROTECTED,
                installer().uninstall("com.android.settings", listener).outcome);
        assertNotNull(records.get("com.android.settings"));

        assertEquals(StoreInstaller.Outcome.NOT_INSTALLED_BY_STORE, installer().uninstall(PKG, listener).outcome);

        records.put(new InstallRecord(PKG, "App", 10, "1.0", 1));
        phone.put(PKG, 10L);
        root = false;
        assertEquals(StoreInstaller.Outcome.NO_ROOT, installer().uninstall(PKG, listener).outcome);
        assertTrue(commands.isEmpty());
        assertNotNull(records.get(PKG));
    }

    @Test
    public void uninstallOfAnAppThatIsAlreadyGoneJustForgetsIt() {
        records.put(new InstallRecord(PKG, "App", 10, "1.0", 1));
        assertTrue(installer().uninstall(PKG, listener).ok());
        assertNull(records.get(PKG));
        assertTrue(commands.isEmpty());
    }

    @Test
    public void uninstallFailureKeepsTheRecord() {
        records.put(new InstallRecord(PKG, "App", 10, "1.0", 1));
        phone.put(PKG, 10L);
        pmOutput = Collections.singletonList("Failure [DELETE_FAILED_DEVICE_POLICY_MANAGER]");
        StoreInstaller.Result r = installer().uninstall(PKG, listener);
        assertEquals(StoreInstaller.Outcome.UNINSTALL_FAILED, r.outcome);
        assertEquals("DELETE_FAILED_DEVICE_POLICY_MANAGER", r.detail);
        assertNotNull(records.get(PKG));
    }
}
