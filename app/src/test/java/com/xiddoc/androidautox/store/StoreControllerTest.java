package com.xiddoc.androidautox.store;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.xiddoc.androidautox.FakeSharedPreferences;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.function.LongSupplier;

public class StoreControllerTest {

    private static final byte[] APK = StoreFixtures.bytes("an apk");
    private static final String SHA = Hashing.sha256Hex(APK);
    private static final String REMOTE_URL = "https://example.com/catalog.json";

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private final StoreFixtures.FakeHttp http = new StoreFixtures.FakeHttp();
    private final StoreFixtures.MemoryRecords records = new StoreFixtures.MemoryRecords();
    private final FakeSharedPreferences prefs = new FakeSharedPreferences();
    private final Map<String, Long> phone = new HashMap<String, Long>();
    private final List<String> commands = new ArrayList<String>();
    private final LinkedList<Runnable> queue = new LinkedList<Runnable>();
    private final List<String> seen = new ArrayList<String>();
    private boolean queued;
    private RuntimeException shellFailure;
    private StoreController controller;
    private StoreSettings settings;

    private static String seedApp(String name, String pkg, long code) {
        return "{\"name\":\"" + name + "\",\"package\":\"" + pkg + "\",\"kind\":\"car\",\"signer\":[\""
                + StoreFixtures.SIG_A + "\"],\"url\":\"https://example.com/" + pkg + ".apk\",\"sha256\":\""
                + SHA + "\",\"versionName\":\"v" + code + "\",\"versionCode\":" + code + "}";
    }

    @Before
    public void setUp() {
        String seed = "{\"version\":1,\"apps\":[" + seedApp("App", "org.example.app", 20) + ","
                + seedApp("Other", "org.example.other", 20) + "]}";
        http.serve("https://example.com/org.example.app.apk", APK);
        http.serve("https://example.com/org.example.other.apk", APK);
        settings = new StoreSettings(prefs);
        CatalogLoader loader = new CatalogLoader(http, new File(tmp.getRoot(), "catalog"), seed,
                new DeviceProfile(34, Collections.singletonList("arm64-v8a")));
        InstalledApps installed = new InstalledApps() {
            @Override
            public long versionCode(String packageName) {
                Long v = phone.get(packageName);
                return v == null ? -1 : v;
            }
        };
        RootShell shell = new RootShell() {
            @Override
            public boolean isRoot() {
                return true;
            }

            @Override
            public List<String> run(String command) {
                if (shellFailure != null) throw shellFailure;
                commands.add(command);
                if (command.startsWith("pm install")) {
                    for (String pkg : new String[]{"org.example.app", "org.example.other"}) {
                        if (command.contains(pkg)) phone.put(pkg, 20L);
                    }
                } else if (command.startsWith("pm uninstall ")) {
                    phone.remove(command.substring("pm uninstall ".length()));
                }
                return Collections.singletonList("Success");
            }
        };
        ApkInspector inspector = new ApkInspector() {
            @Override
            public Info inspect(File apk) {
                String pkg = apk.getName().replace(".apk", "");
                return new Info(pkg, 20, Collections.singletonList(StoreFixtures.SIG_A));
            }
        };
        StoreInstaller installer = new StoreInstaller(http, new File(tmp.getRoot(), "dl"), inspector,
                installed, shell, records, new LongSupplier() {
                    @Override
                    public long getAsLong() {
                        return 77;
                    }
                });
        Executor executor = new Executor() {
            @Override
            public void execute(Runnable r) {
                if (queued) queue.add(r);
                else r.run();
            }
        };
        controller = new StoreController(loader, installer, records, installed, settings, executor,
                new LongSupplier() {
                    @Override
                    public long getAsLong() {
                        return 5;
                    }
                });
        controller.addObserver(new StoreController.Observer() {
            @Override
            public void onChanged() {
                seen.add(controller.operation() + "/" + controller.stage() + "/" + controller.percent());
            }
        });
    }

    private void runQueue() {
        while (!queue.isEmpty()) queue.removeFirst().run();
    }

    private StoreApp app(String pkg) {
        for (StoreApp a : controller.apps()) {
            if (a.packageName.equals(pkg)) return a;
        }
        throw new AssertionError(pkg);
    }

    @Test
    public void reloadListsTheCatalog() {
        assertTrue(controller.apps().isEmpty());
        controller.reload();
        assertEquals(2, controller.apps().size());
        assertTrue(controller.trackedVersions().isEmpty());
        assertEquals(-1, controller.installedVersion("org.example.app"));
        assertFalse(controller.installedByStore("org.example.app"));
        assertFalse(controller.busy());
        assertEquals(StoreController.Operation.NONE, controller.operation());
        assertEquals("", controller.busyName());
        assertNull(controller.stage());
        assertEquals(-1, controller.percent());
        assertNull(controller.lastResult("org.example.app"));
        assertNull(controller.lastRefresh());
        assertFalse(controller.fdroidEnabled());
        assertFalse(controller.fdroidCached());
        assertFalse(seen.isEmpty());
    }

    @Test
    public void installTracksProgressAndResult() {
        controller.reload();
        seen.clear();
        assertTrue(controller.install(app("org.example.app")));

        assertTrue(controller.lastResult("org.example.app").ok());
        assertEquals(1, commands.size());
        assertTrue(controller.installedByStore("org.example.app"));
        assertEquals(Long.valueOf(20), controller.trackedVersions().get("org.example.app"));
        assertEquals(20, controller.installedVersion("org.example.app"));
        assertFalse(controller.busy());
        assertTrue(seen.toString(), seen.contains("INSTALLING/null/-1"));
        assertTrue(seen.toString(), seen.contains("INSTALLING/DOWNLOADING/99"));
        assertTrue(seen.toString(), seen.contains("INSTALLING/INSTALLING/-1"));
        assertEquals("NONE/null/-1", seen.get(seen.size() - 1));
    }

    @Test
    public void onlyOneOperationAtATime() {
        records.put(new InstallRecord("org.example.other", "Other", 10, "v10", 1));
        phone.put("org.example.other", 10L);
        controller.reload();
        queued = true;
        assertTrue(controller.install(app("org.example.app")));
        assertTrue(controller.busy());
        assertEquals(StoreController.Operation.INSTALLING, controller.operation());
        assertEquals("App", controller.busyName());
        assertFalse(controller.install(app("org.example.other")));
        assertFalse(controller.refresh(true));
        assertFalse(controller.uninstall("org.example.app"));
        assertFalse(controller.updateAll());
        runQueue();
        assertFalse(controller.busy());
        assertEquals(1, commands.size());
    }

    @Test
    public void failedTasksStillFreeTheSlot() {
        controller.reload();
        shellFailure = new IllegalStateException("boom");
        try {
            controller.install(app("org.example.app"));
            fail("expected the failure to propagate");
        } catch (IllegalStateException expected) {
            assertFalse(controller.busy());
        }
    }

    @Test
    public void uninstallRemovesWhatTheStoreInstalled() {
        controller.reload();
        controller.install(app("org.example.app"));
        queued = true;
        assertTrue(controller.uninstall("org.example.app"));
        assertEquals(StoreController.Operation.REMOVING, controller.operation());
        assertEquals("App", controller.busyName());
        runQueue();
        assertTrue(controller.lastResult("org.example.app").ok());
        assertFalse(controller.installedByStore("org.example.app"));
        assertEquals(-1, controller.installedVersion("org.example.app"));

        queued = true;
        assertTrue(controller.uninstall("org.example.unknown"));
        assertEquals("org.example.unknown", controller.busyName());
        runQueue();
        assertEquals(StoreInstaller.Outcome.NOT_INSTALLED_BY_STORE,
                controller.lastResult("org.example.unknown").outcome);
    }

    @Test
    public void refreshDownloadsTheUsersCatalogAndRemembersTheResult() {
        String extra = "{\"version\":1,\"apps\":[" + seedApp("Extra", "org.example.extra", 1) + "]}";
        http.serve(REMOTE_URL, extra);
        assertTrue(settings.setRemoteUrl(REMOTE_URL));
        assertTrue(controller.refresh(false));
        assertEquals(CatalogLoader.Step.OK, controller.lastRefresh().remote);
        assertEquals(CatalogLoader.Step.SKIPPED, controller.lastRefresh().fdroid);
        assertEquals(3, controller.apps().size());
        assertFalse(controller.busy());
    }

    @Test
    public void updateAllInstallsEveryNewerVersion() {
        controller.reload();
        assertFalse(controller.updateAll());                              // nothing installed yet
        records.put(new InstallRecord("org.example.app", "App", 10, "v10", 1));
        records.put(new InstallRecord("org.example.other", "Other", 10, "v10", 1));
        phone.put("org.example.app", 10L);
        phone.put("org.example.other", 10L);
        controller.reload();

        assertTrue(controller.updateAll());
        assertEquals(2, commands.size());
        assertEquals(Long.valueOf(20), controller.trackedVersions().get("org.example.app"));
        assertEquals(Long.valueOf(20), controller.trackedVersions().get("org.example.other"));
        assertFalse(controller.updateAll());                              // all current now
        assertNotNull(controller.lastResult("org.example.other"));
    }

    @Test
    public void updateAllCanBeCancelled() {
        controller.reload();
        records.put(new InstallRecord("org.example.app", "App", 10, "v10", 1));
        phone.put("org.example.app", 10L);
        controller.reload();
        queued = true;
        assertTrue(controller.updateAll());
        assertEquals("", controller.busyName());
        controller.cancel();
        runQueue();
        assertTrue(commands.isEmpty());
        assertFalse(controller.busy());
    }

    @Test
    public void clearingCachesRebuildsFromTheBundledCatalog() throws Exception {
        http.serve(REMOTE_URL, "{\"version\":1,\"apps\":[" + seedApp("Extra", "org.example.extra", 1) + "]}");
        settings.setRemoteUrl(REMOTE_URL);
        controller.refresh(false);
        assertEquals(3, controller.apps().size());
        controller.clearCaches();
        assertEquals(2, controller.apps().size());
    }

    @Test
    public void observersHearAboutRealProgressChangesOnly() {
        StoreInstaller.Listener listener = controller.listener();
        seen.clear();
        listener.onStage(StoreInstaller.Stage.DOWNLOADING, 10);
        assertEquals(1, seen.size());
        listener.onStage(StoreInstaller.Stage.DOWNLOADING, 10);       // nothing new
        assertEquals(1, seen.size());
        listener.onStage(StoreInstaller.Stage.DOWNLOADING, 11);       // same stage, more progress
        assertEquals(2, seen.size());
        listener.onStage(StoreInstaller.Stage.VERIFYING, 11);         // new stage, same percent
        assertEquals(3, seen.size());
        assertEquals(StoreInstaller.Stage.VERIFYING, controller.stage());
        assertEquals(11, controller.percent());
        assertFalse(listener.cancelled());
        controller.cancel();
        assertTrue(listener.cancelled());
    }

    @Test
    public void removedObserversAreNotCalled() {
        final int[] calls = {0};
        StoreController.Observer o = new StoreController.Observer() {
            @Override
            public void onChanged() {
                calls[0]++;
            }
        };
        controller.addObserver(o);
        controller.reload();
        int after = calls[0];
        assertTrue(after > 0);
        controller.removeObserver(o);
        controller.reload();
        assertEquals(after, calls[0]);
    }
}
