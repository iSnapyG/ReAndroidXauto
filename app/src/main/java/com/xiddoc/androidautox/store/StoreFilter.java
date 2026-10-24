package com.xiddoc.androidautox.store;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Browse, search and installed/updates views over the merged catalog. */
public final class StoreFilter {

    /** F-Droid categories worth browsing from the car. */
    public static final List<String> BROWSE_CATEGORIES = Collections.unmodifiableList(Arrays.asList(
            "Multimedia", "Navigation", "Podcast", "Radio", "Audiobook",
            "Online Media Player", "Local Media Player", "Internet", "Phone & SMS", "Connectivity"));

    private StoreFilter() {}

    /** Apps of one kind, by name. */
    public static List<StoreApp> byKind(List<StoreApp> apps, StoreApp.Kind kind) {
        List<StoreApp> out = new ArrayList<StoreApp>();
        for (StoreApp a : apps) {
            if (a.kind == kind) out.add(a);
        }
        return sorted(out);
    }

    /** Apps listing the category (case-insensitive), by name. */
    public static List<StoreApp> byCategory(List<StoreApp> apps, String category) {
        List<StoreApp> out = new ArrayList<StoreApp>();
        for (StoreApp a : apps) {
            for (String c : a.categories) {
                if (c.equalsIgnoreCase(category)) {
                    out.add(a);
                    break;
                }
            }
        }
        return sorted(out);
    }

    /**
     * Apps matching every word of {@code query} in their name, package or summary
     * (case-insensitive). Name matches come first, name-prefix matches before the rest. A
     * blank query matches nothing.
     */
    public static List<StoreApp> search(List<StoreApp> apps, String query) {
        List<StoreApp> out = new ArrayList<StoreApp>();
        String clean = StoreValidation.text(query, 100).toLowerCase(Locale.ROOT);
        if (clean.isEmpty()) return out;
        final String[] words = clean.split(" ");
        for (StoreApp a : apps) {
            String haystack = (a.name + " " + a.packageName + " " + a.summary).toLowerCase(Locale.ROOT);
            boolean all = true;
            for (String w : words) {
                if (!haystack.contains(w)) {
                    all = false;
                    break;
                }
            }
            if (all) out.add(a);
        }
        final String lead = words[0];
        Collections.sort(out, new Comparator<StoreApp>() {
            @Override
            public int compare(StoreApp x, StoreApp y) {
                int rx = rank(x, lead);
                int ry = rank(y, lead);
                return rx != ry ? Integer.compare(rx, ry) : byName(x, y);
            }
        });
        return out;
    }

    /** Apps with a record (installed through the store), by name. */
    public static List<StoreApp> installed(List<StoreApp> apps, Map<String, Long> installedVersions) {
        List<StoreApp> out = new ArrayList<StoreApp>();
        for (StoreApp a : apps) {
            if (installedVersions.containsKey(a.packageName)) out.add(a);
        }
        return sorted(out);
    }

    /** Installed apps for which the catalog has a newer version, by name. */
    public static List<StoreApp> updates(List<StoreApp> apps, Map<String, Long> installedVersions) {
        List<StoreApp> out = new ArrayList<StoreApp>();
        for (StoreApp a : apps) {
            Long have = installedVersions.get(a.packageName);
            if (have != null
                    && UpdateCheck.status(a.versionCode, have) == UpdateCheck.Status.UPDATE_AVAILABLE) {
                out.add(a);
            }
        }
        return sorted(out);
    }

    private static int rank(StoreApp a, String lead) {
        String name = a.name.toLowerCase(Locale.ROOT);
        if (name.startsWith(lead)) return 0;
        return name.contains(lead) ? 1 : 2;
    }

    private static List<StoreApp> sorted(List<StoreApp> apps) {
        Collections.sort(apps, new Comparator<StoreApp>() {
            @Override
            public int compare(StoreApp x, StoreApp y) {
                return byName(x, y);
            }
        });
        return apps;
    }

    private static int byName(StoreApp x, StoreApp y) {
        int c = x.name.compareToIgnoreCase(y.name);
        return c != 0 ? c : x.packageName.compareTo(y.packageName);
    }
}
