package com.xiddoc.androidautox.store;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class InstallSelectionTest {

    private final Map<String, Long> phone = new HashMap<String, Long>();
    private final InstalledApps installed = new InstalledApps() {
        @Override
        public long versionCode(String packageName) {
            Long v = phone.get(packageName);
            return v == null ? -1 : v;
        }
    };

    private final StoreApp a = StoreFixtures.named("A", "org.example.a", "s", StoreApp.Kind.CAR, 5);
    private final StoreApp b = StoreFixtures.named("B", "org.example.b", "s", StoreApp.Kind.CAR, 5);
    private final StoreApp c = StoreFixtures.named("C", "org.example.c", "s", StoreApp.Kind.CAR, 5);
    private final StoreApp unresolved =
            StoreFixtures.fdroidRef("F", "org.example.f", StoreApp.Kind.CAR, StoreFixtures.SIG_A);
    private final List<StoreApp> all = Arrays.asList(a, b, c, unresolved);

    @Test
    public void togglingTicksAndUnticks() {
        InstallSelection s = new InstallSelection();
        assertTrue(s.toggle("org.example.a", true));
        assertTrue(s.contains("org.example.a"));
        assertEquals(1, s.size());
        assertFalse(s.toggle("org.example.a", true));
        assertFalse(s.contains("org.example.a"));
        assertEquals(0, s.size());
    }

    @Test
    public void anAppThatCannotBeTickedIsNeverKept() {
        InstallSelection s = new InstallSelection();
        s.toggle("org.example.a", true);
        assertFalse(s.toggle("org.example.a", false));
        assertEquals(0, s.size());
        assertFalse(s.toggle("org.example.b", false));
        assertEquals(0, s.size());
    }

    @Test
    public void pickAllTicksOnlyWhatCanBeInstalled() {
        phone.put("org.example.b", 5L);                       // already current
        InstallSelection s = new InstallSelection();
        s.pickAll(all, installed);
        assertTrue(s.contains("org.example.a"));
        assertFalse(s.contains("org.example.b"));
        assertTrue(s.contains("org.example.c"));
        assertFalse(s.contains("org.example.f"));
        assertEquals(2, s.size());
    }

    @Test
    public void clearForgetsEverything() {
        InstallSelection s = new InstallSelection();
        s.pickAll(all, installed);
        s.clear();
        assertEquals(0, s.size());
    }

    @Test
    public void chosenKeepsCatalogOrderAndDropsWhatBecameUnavailable() {
        InstallSelection s = new InstallSelection();
        s.toggle("org.example.c", true);
        s.toggle("org.example.a", true);
        s.toggle("org.example.b", true);
        s.toggle("org.example.gone", true);                   // left the catalog
        phone.put("org.example.b", 5L);                       // installed meanwhile

        List<StoreApp> chosen = s.chosen(all, installed);

        assertEquals(Arrays.asList(a, c), chosen);
        assertEquals(2, s.size());
        assertFalse(s.contains("org.example.b"));
        assertFalse(s.contains("org.example.gone"));
    }

    @Test
    public void pruneKeepsValidTicks() {
        InstallSelection s = new InstallSelection();
        s.toggle("org.example.a", true);
        s.prune(all, installed);
        assertTrue(s.contains("org.example.a"));
    }
}
