package com.xiddoc.androidautox.store;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The apps ticked in the install list. Remembers picks across searching and filtering, and
 * never hands back an app that cannot be installed any more (it was installed meanwhile, or
 * left the catalog).
 */
public final class InstallSelection {

    private final Set<String> picked = new LinkedHashSet<String>();

    /**
     * Flips the tick on one app.
     *
     * @param selectable whether the app can be ticked at all; if not, any tick is dropped
     * @return the new state of the tick
     */
    public synchronized boolean toggle(String packageName, boolean selectable) {
        if (!selectable) {
            picked.remove(packageName);
            return false;
        }
        if (picked.remove(packageName)) return false;
        picked.add(packageName);
        return true;
    }

    public synchronized boolean contains(String packageName) {
        return picked.contains(packageName);
    }

    public synchronized int size() {
        return picked.size();
    }

    public synchronized void clear() {
        picked.clear();
    }

    /** Ticks every app in {@code visible} that can be installed. */
    public synchronized void pickAll(List<StoreApp> visible, InstalledApps installed) {
        for (StoreApp a : visible) {
            if (selectable(a, installed)) picked.add(a.packageName);
        }
    }

    /** Drops ticks on apps that are no longer in {@code apps} or no longer installable. */
    public synchronized void prune(List<StoreApp> apps, InstalledApps installed) {
        Set<String> keep = new HashSet<String>();
        for (StoreApp a : apps) {
            if (picked.contains(a.packageName) && selectable(a, installed)) keep.add(a.packageName);
        }
        picked.retainAll(keep);
    }

    /** The ticked apps that can still be installed, in the order of {@code apps}. */
    public synchronized List<StoreApp> chosen(List<StoreApp> apps, InstalledApps installed) {
        prune(apps, installed);
        List<StoreApp> out = new ArrayList<StoreApp>();
        for (StoreApp a : apps) {
            if (picked.contains(a.packageName)) out.add(a);
        }
        return out;
    }

    private static boolean selectable(StoreApp app, InstalledApps installed) {
        return InstallChoice.selectable(InstallChoice.of(app, installed.versionCode(app.packageName)));
    }
}
