package com.xiddoc.androidautox.store;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.function.LongSupplier;

/**
 * The store's brain, shared by the car screens: it owns the app list, runs one operation at a
 * time (refresh, install, update, uninstall) on a background executor, and tells observers
 * when something changed.
 *
 * <p>Observers are called on the executor's thread; the UI layer re-posts to its own thread.
 */
public final class StoreController {

    public enum Operation { NONE, REFRESHING, INSTALLING, REMOVING }

    /** Called whenever the app list, the installed state or the running operation changed. */
    public interface Observer {
        void onChanged();
    }

    private final CatalogLoader loader;
    private final StoreInstaller installer;
    private final InstallRecords records;
    private final InstalledApps installedApps;
    private final StoreSettings settings;
    private final Executor executor;
    private final LongSupplier clock;
    private final List<Observer> observers = new CopyOnWriteArrayList<Observer>();

    private List<StoreApp> apps = Collections.emptyList();
    private Map<String, Long> tracked = Collections.emptyMap();
    private Operation operation = Operation.NONE;
    private String busyName = "";
    private StoreInstaller.Stage stage;
    private int percent = -1;
    private final Map<String, StoreInstaller.Result> results = new HashMap<String, StoreInstaller.Result>();
    private CatalogLoader.RefreshResult lastRefresh;
    private volatile boolean cancelRequested;

    public StoreController(CatalogLoader loader, StoreInstaller installer, InstallRecords records,
                           InstalledApps installedApps, StoreSettings settings, Executor executor,
                           LongSupplier clock) {
        this.loader = loader;
        this.installer = installer;
        this.records = records;
        this.installedApps = installedApps;
        this.settings = settings;
        this.executor = executor;
        this.clock = clock;
    }

    public void addObserver(Observer o) {
        observers.add(o);
    }

    public void removeObserver(Observer o) {
        observers.remove(o);
    }

    // ---- state ----------------------------------------------------------------------------

    public synchronized List<StoreApp> apps() {
        return apps;
    }

    /** Installed-by-the-store apps still on the phone, with their real version codes. */
    public synchronized Map<String, Long> trackedVersions() {
        return tracked;
    }

    /** Version code of any installed package, or -1. */
    public long installedVersion(String packageName) {
        return installedApps.versionCode(packageName);
    }

    public synchronized boolean installedByStore(String packageName) {
        return tracked.containsKey(packageName);
    }

    public synchronized Operation operation() {
        return operation;
    }

    public synchronized boolean busy() {
        return operation != Operation.NONE;
    }

    /** Name of the app being installed or removed, empty otherwise. */
    public synchronized String busyName() {
        return busyName;
    }

    /** Stage of the running install, or null. */
    public synchronized StoreInstaller.Stage stage() {
        return stage;
    }

    /** Download percentage of the running install, -1 if unknown. */
    public synchronized int percent() {
        return percent;
    }

    /** The outcome of the last install or removal of this package, or null. */
    public synchronized StoreInstaller.Result lastResult(String packageName) {
        return results.get(packageName);
    }

    /** The result of the last catalog refresh, or null if none ran. */
    public synchronized CatalogLoader.RefreshResult lastRefresh() {
        return lastRefresh;
    }

    public boolean fdroidEnabled() {
        return settings.fdroidEnabled();
    }

    public boolean fdroidCached() {
        return loader.hasFdroidCache();
    }

    public void cancel() {
        cancelRequested = true;
    }

    /** Forgets the downloaded catalogs and rebuilds the list from the bundled one. */
    public void clearCaches() throws IOException {
        loader.clearCaches();
        reload();
    }

    // ---- actions --------------------------------------------------------------------------

    /** Rebuilds the list from the local caches (cheap; also re-reads what is installed). */
    public void reload() {
        List<StoreApp> fresh = loader.load(settings.fdroidEnabled());
        Map<String, Long> view = InstalledView.tracked(records, installedApps);
        synchronized (this) {
            apps = Collections.unmodifiableList(fresh);
            tracked = Collections.unmodifiableMap(view);
        }
        notifyObservers();
    }

    /** Downloads what is due (the user's catalog, and the F-Droid index if allowed) in the background. */
    public boolean refresh(final boolean force) {
        if (!begin(Operation.REFRESHING, "")) return false;
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    CatalogLoader.RefreshResult r = loader.refresh(settings.remoteUrl(),
                            settings.fdroidEnabled(), force, clock.getAsLong());
                    synchronized (StoreController.this) {
                        lastRefresh = r;
                    }
                    reload();
                } finally {
                    finish();
                }
            }
        });
        return true;
    }

    /** Installs or updates {@code app} in the background. */
    public boolean install(final StoreApp app) {
        if (!begin(Operation.INSTALLING, app.name)) return false;
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    installOne(app);
                    reload();
                } finally {
                    finish();
                }
            }
        });
        return true;
    }

    /** Updates every installed app the catalog has a newer version of. */
    public boolean updateAll() {
        final List<StoreApp> due = StoreFilter.updates(apps(), trackedVersions());
        if (due.isEmpty() || !begin(Operation.INSTALLING, "")) return false;
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    for (StoreApp app : due) {
                        if (cancelRequested) break;
                        installOne(app);
                        reload();
                    }
                } finally {
                    finish();
                }
            }
        });
        return true;
    }

    /** Removes an app the store installed. */
    public boolean uninstall(final String packageName) {
        if (!begin(Operation.REMOVING, nameOf(packageName))) return false;
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    StoreInstaller.Result r = installer.uninstall(packageName, listener());
                    synchronized (StoreController.this) {
                        results.put(packageName, r);
                    }
                    reload();
                } finally {
                    finish();
                }
            }
        });
        return true;
    }

    // ---- internals ------------------------------------------------------------------------

    private void installOne(StoreApp app) {
        synchronized (this) {
            busyName = app.name;
            stage = null;
            percent = -1;
        }
        notifyObservers();
        StoreInstaller.Result r = installer.install(app, listener());
        synchronized (this) {
            results.put(app.packageName, r);
        }
    }

    private synchronized String nameOf(String packageName) {
        for (StoreApp a : apps) {
            if (a.packageName.equals(packageName)) return a.name;
        }
        return packageName;
    }

    /** Claims the single operation slot; tells observers, outside the lock, if it was free. */
    private boolean begin(Operation op, String name) {
        synchronized (this) {
            if (operation != Operation.NONE) return false;
            operation = op;
            busyName = name;
            stage = null;
            percent = -1;
            cancelRequested = false;
        }
        notifyObservers();
        return true;
    }

    private void finish() {
        synchronized (this) {
            operation = Operation.NONE;
            busyName = "";
            stage = null;
            percent = -1;
        }
        notifyObservers();
    }

    /** The installer's callbacks: remember the stage and tell observers only when it changes. */
    StoreInstaller.Listener listener() {
        return new StoreInstaller.Listener() {
            @Override
            public void onStage(StoreInstaller.Stage s, int p) {
                boolean changed;
                synchronized (StoreController.this) {
                    changed = s != stage || p != percent;
                    stage = s;
                    percent = p;
                }
                if (changed) notifyObservers();
            }

            @Override
            public boolean cancelled() {
                return cancelRequested;
            }
        };
    }

    private void notifyObservers() {
        for (Observer o : new ArrayList<Observer>(observers)) o.onChanged();
    }
}
