package com.xiddoc.androidautox.store;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StoreFilterTest {

    private static final StoreApp VLC = StoreFixtures.named("VLC", "org.videolan.vlc", "Media player",
            StoreApp.Kind.CAR, 5, "Multimedia", "Online Media Player");
    private static final StoreApp ANTENNA = StoreFixtures.named("AntennaPod", "de.danoeh.antennapod",
            "Podcast manager", StoreApp.Kind.CAR, 3, "Podcast", "multimedia");
    private static final StoreApp NEWPIPE = StoreFixtures.named("NewPipe", "org.schabi.newpipe",
            "Lightweight YouTube player", StoreApp.Kind.AUTOX, 0, "Multimedia");
    private static final StoreApp OSM = StoreFixtures.named("Organic Maps", "app.organicmaps",
            "Offline maps", StoreApp.Kind.OTHER, 9, "Navigation");
    private static final StoreApp DUP_A = StoreFixtures.named("Same", "org.example.b", "x", StoreApp.Kind.OTHER, 1);
    private static final StoreApp DUP_B = StoreFixtures.named("Same", "org.example.a", "x", StoreApp.Kind.OTHER, 1);
    private static final List<StoreApp> ALL = Arrays.asList(VLC, OSM, NEWPIPE, ANTENNA);

    private static List<String> names(List<StoreApp> apps) {
        List<String> out = new ArrayList<String>();
        for (StoreApp a : apps) out.add(a.name);
        return out;
    }

    @Test
    public void byKind() {
        assertEquals(Arrays.asList("AntennaPod", "VLC"), names(StoreFilter.byKind(ALL, StoreApp.Kind.CAR)));
        assertEquals(Arrays.asList("NewPipe"), names(StoreFilter.byKind(ALL, StoreApp.Kind.AUTOX)));
        assertTrue(StoreFilter.byKind(new ArrayList<StoreApp>(), StoreApp.Kind.OTHER).isEmpty());
    }

    @Test
    public void byCategoryIgnoresCaseAndListsEachAppOnce() {
        StoreApp twice = StoreFixtures.named("Twice", "org.example.twice", "x", StoreApp.Kind.OTHER, 1,
                "Multimedia", "multimedia");
        List<StoreApp> withTwice = new ArrayList<StoreApp>(ALL);
        withTwice.add(twice);
        assertEquals(Arrays.asList("AntennaPod", "NewPipe", "Twice", "VLC"),
                names(StoreFilter.byCategory(withTwice, "MULTIMEDIA")));
        assertEquals(Arrays.asList("Organic Maps"), names(StoreFilter.byCategory(ALL, "Navigation")));
        assertTrue(StoreFilter.byCategory(ALL, "Games").isEmpty());
    }

    @Test
    public void blankSearchMatchesNothing() {
        assertTrue(StoreFilter.search(ALL, "   ").isEmpty());
        assertTrue(StoreFilter.search(ALL, null).isEmpty());
    }

    @Test
    public void searchOrdering() {
        // "pod": name prefix (none), name contains (AntennaPod), summary only (none) ...
        assertEquals(Arrays.asList("AntennaPod"), names(StoreFilter.search(ALL, "pod")));
        // "player" is in VLC's and NewPipe's summaries only
        assertEquals(Arrays.asList("NewPipe", "VLC"), names(StoreFilter.search(ALL, "player")));
        // prefix beats contains beats summary-only; "an" starts AntennaPod... and is inside Organic
        List<StoreApp> apps = Arrays.asList(OSM, ANTENNA, VLC);
        assertEquals(Arrays.asList("AntennaPod", "Organic Maps", "VLC"), names(StoreFilter.search(apps, "an")));
        // all words must match, in the name, package or summary
        assertEquals(Arrays.asList("NewPipe"), names(StoreFilter.search(ALL, "youtube newpipe")));
        assertEquals(Arrays.asList("VLC"), names(StoreFilter.search(ALL, "videolan")));
        assertTrue(StoreFilter.search(ALL, "youtube vlc").isEmpty());
        // identical names fall back to the package name
        List<StoreApp> same = StoreFilter.search(Arrays.asList(DUP_A, DUP_B), "same");
        assertEquals("org.example.a", same.get(0).packageName);
        assertEquals("org.example.b", same.get(1).packageName);
    }

    @Test
    public void rankPutsPrefixThenContainsThenSummary() {
        StoreApp prefix = StoreFixtures.named("Mapper", "org.a.b", "x", StoreApp.Kind.OTHER, 1);
        StoreApp contains = StoreFixtures.named("Atlas Map", "org.c.d", "x", StoreApp.Kind.OTHER, 1);
        StoreApp summaryOnly = StoreFixtures.named("Zed", "org.e.f", "a map of things", StoreApp.Kind.OTHER, 1);
        assertEquals(Arrays.asList("Mapper", "Atlas Map", "Zed"),
                names(StoreFilter.search(Arrays.asList(summaryOnly, contains, prefix), "map")));
    }

    @Test
    public void installedAndUpdates() {
        Map<String, Long> have = new HashMap<String, Long>();
        have.put("org.videolan.vlc", 5L);            // current
        have.put("de.danoeh.antennapod", 2L);        // older than the catalog's 3
        have.put("org.schabi.newpipe", 1L);          // catalog version unknown
        assertEquals(Arrays.asList("AntennaPod", "NewPipe", "VLC"), names(StoreFilter.installed(ALL, have)));
        assertEquals(Arrays.asList("AntennaPod"), names(StoreFilter.updates(ALL, have)));
        assertTrue(StoreFilter.updates(ALL, new HashMap<String, Long>()).isEmpty());
    }

    @Test
    public void browseCategoriesAreListed() {
        assertTrue(StoreFilter.BROWSE_CATEGORIES.contains("Navigation"));
    }
}
