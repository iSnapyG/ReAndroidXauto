package com.xiddoc.androidautox.store;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** UpdateCheck, Page, InstalledView and CatalogMerge. */
public class SmallLogicTest {

    @Test
    public void updateStatus() {
        assertEquals(UpdateCheck.Status.NOT_INSTALLED, UpdateCheck.status(5, -1));
        assertEquals(UpdateCheck.Status.INSTALLED_VERSION_UNKNOWN, UpdateCheck.status(0, 3));
        assertEquals(UpdateCheck.Status.UPDATE_AVAILABLE, UpdateCheck.status(5, 3));
        assertEquals(UpdateCheck.Status.CURRENT, UpdateCheck.status(5, 5));
        assertEquals(UpdateCheck.Status.CURRENT, UpdateCheck.status(4, 5));
        assertEquals(UpdateCheck.Status.UPDATE_AVAILABLE, UpdateCheck.status(1, 0));
    }

    @Test
    public void paging() {
        List<Integer> all = Arrays.asList(1, 2, 3, 4, 5);
        Page<Integer> first = Page.of(all, 0, 2);
        assertEquals(Arrays.asList(1, 2), first.items);
        assertEquals(3, first.pageCount);
        assertFalse(first.hasPrevious());
        assertTrue(first.hasNext());
        Page<Integer> last = Page.of(all, 99, 2);
        assertEquals(Collections.singletonList(5), last.items);
        assertEquals(2, last.index);
        assertTrue(last.hasPrevious());
        assertFalse(last.hasNext());
        assertEquals(0, Page.of(all, -4, 2).index);
        assertEquals(1, Page.of(all, 0, 5).pageCount);
        assertEquals(1, Page.of(all, 0, 0).items.size());     // size is at least 1
        Page<Integer> empty = Page.of(Collections.<Integer>emptyList(), 3, 4);
        assertTrue(empty.items.isEmpty());
        assertEquals(1, empty.pageCount);
        assertEquals(0, empty.index);
    }

    @Test
    public void installedViewUsesTheRealVersions() {
        StoreFixtures.MemoryRecords records = new StoreFixtures.MemoryRecords();
        records.put(new InstallRecord("org.example.a", "A", 1, "1", 0));
        records.put(new InstallRecord("org.example.gone", "G", 1, "1", 0));
        final Map<String, Long> phone = new HashMap<String, Long>();
        phone.put("org.example.a", 9L);
        Map<String, Long> view = InstalledView.tracked(records, new InstalledApps() {
            @Override
            public long versionCode(String packageName) {
                Long v = phone.get(packageName);
                return v == null ? -1 : v;
            }
        });
        assertEquals(1, view.size());
        assertEquals(Long.valueOf(9), view.get("org.example.a"));
    }

    private static StoreApp fdroidApp(String name, String pkg, long code, String... signers) {
        return new StoreApp(name, "fd summary", pkg, StoreApp.Kind.OTHER, StoreApp.Origin.FDROID,
                Collections.singletonList("FdCat"), Arrays.asList(signers),
                ApkSource.direct("https://f-droid.org/repo/" + pkg + ".apk", StoreFixtures.SIG_C, 5),
                "fd", code);
    }

    @Test
    public void mergeResolvesFdroidReferences() {
        List<StoreApp> catalog = Arrays.asList(
                StoreFixtures.fdroidRef("Pretty", "org.example.pinned", StoreApp.Kind.CAR, StoreFixtures.SIG_A),
                StoreFixtures.fdroidRef("org.example.bare", "org.example.bare", StoreApp.Kind.AUTOX),
                StoreFixtures.fdroidRef("Wrong", "org.example.changed", StoreApp.Kind.CAR, StoreFixtures.SIG_B),
                StoreFixtures.fdroidRef("Waiting", "org.example.unindexed", StoreApp.Kind.CAR, StoreFixtures.SIG_A),
                StoreFixtures.direct("org.example.fixed", StoreFixtures.SIG_C, StoreFixtures.SIG_A),
                StoreFixtures.direct("org.example.fixed", StoreFixtures.SIG_B, StoreFixtures.SIG_B),   // duplicate: ignored
                StoreFixtures.named("Full", "org.example.full", "mine", StoreApp.Kind.CAR, 0, "Mine"),
                new StoreApp("Described", "my summary", "org.example.described", StoreApp.Kind.CAR,
                        StoreApp.Origin.CATALOG, Collections.singletonList("Mine"),
                        Collections.singletonList(StoreFixtures.SIG_A), ApkSource.fdroid(), "", 0));
        // "full" is a fixed-URL entry; give the F-Droid copy of it a different name to prove precedence
        List<StoreApp> fdroid = Arrays.asList(
                fdroidApp("FD pinned", "org.example.pinned", 4, StoreFixtures.SIG_A),
                fdroidApp("FD bare", "org.example.bare", 6, StoreFixtures.SIG_B),
                fdroidApp("FD changed", "org.example.changed", 2, StoreFixtures.SIG_A),
                fdroidApp("FD fixed", "org.example.fixed", 3, StoreFixtures.SIG_A),
                fdroidApp("FD other", "org.example.other", 8, StoreFixtures.SIG_A),
                fdroidApp("FD described", "org.example.described", 1, StoreFixtures.SIG_A),
                fdroidApp("FD other again", "org.example.other", 9, StoreFixtures.SIG_A));

        List<StoreApp> merged = CatalogMerge.merge(catalog, fdroid);
        Map<String, StoreApp> byPackage = new HashMap<String, StoreApp>();
        for (StoreApp a : merged) byPackage.put(a.packageName, a);

        assertEquals(7, merged.size());
        StoreApp pinned = byPackage.get("org.example.pinned");
        assertEquals("Pretty", pinned.name);                         // the catalog's own words win
        assertEquals("fd summary", pinned.summary);                  // gaps are filled from F-Droid
        assertEquals(Collections.singletonList("FdCat"), pinned.categories);
        assertEquals(StoreApp.Kind.CAR, pinned.kind);
        assertEquals(StoreApp.Origin.CATALOG, pinned.origin);
        assertEquals(ApkSource.Type.DIRECT, pinned.source.type);
        assertEquals(4, pinned.versionCode);
        assertEquals(Collections.singletonList(StoreFixtures.SIG_A), pinned.signers);

        StoreApp bare = byPackage.get("org.example.bare");
        assertEquals("FD bare", bare.name);
        assertEquals(Collections.singletonList(StoreFixtures.SIG_B), bare.signers);
        assertEquals(StoreApp.Kind.AUTOX, bare.kind);

        assertEquals(false, byPackage.containsKey("org.example.changed"));   // signer no longer matches
        assertEquals(ApkSource.Type.FDROID, byPackage.get("org.example.unindexed").source.type);
        assertEquals(StoreFixtures.SIG_C, byPackage.get("org.example.fixed").source.sha256);
        assertEquals("Full", byPackage.get("org.example.full").name);
        StoreApp described = byPackage.get("org.example.described");
        assertEquals("my summary", described.summary);
        assertEquals(Collections.singletonList("Mine"), described.categories);
        assertEquals(StoreApp.Origin.FDROID, byPackage.get("org.example.other").origin);
        assertEquals("FD other", byPackage.get("org.example.other").name);
    }

    @Test
    public void mergeWithoutAnIndexKeepsReferences() {
        List<StoreApp> catalog = Collections.singletonList(
                StoreFixtures.fdroidRef("Ref", "org.example.ref", StoreApp.Kind.CAR, StoreFixtures.SIG_A));
        List<StoreApp> merged = CatalogMerge.merge(catalog, new ArrayList<StoreApp>());
        assertEquals(1, merged.size());
        assertEquals(ApkSource.Type.FDROID, merged.get(0).source.type);
    }
}
