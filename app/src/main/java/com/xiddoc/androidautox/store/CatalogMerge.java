package com.xiddoc.androidautox.store;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Combines the curated catalog (bundled seed plus the optional remote one) with the apps from
 * the F-Droid index into the list the store shows.
 *
 * <ul>
 * <li>A catalog app that points at F-Droid is resolved against the index: it takes the index's
 *     download, hash and version. If the catalog pins signing certificates, the index must
 *     list only those; otherwise the app is dropped rather than shown as installable.</li>
 * <li>A catalog app that cannot be resolved yet (index not downloaded) stays in the list with
 *     its F-Droid reference, so it can be shown but not installed.</li>
 * <li>Anything else in the F-Droid index is added as an ordinary app, unless the catalog
 *     mentions its package (even if that entry was dropped).</li>
 * </ul>
 */
public final class CatalogMerge {

    private CatalogMerge() {}

    public static List<StoreApp> merge(List<StoreApp> catalog, List<StoreApp> fdroid) {
        Map<String, StoreApp> index = new LinkedHashMap<String, StoreApp>();
        for (StoreApp f : fdroid) index.put(f.packageName, f);

        Map<String, StoreApp> out = new LinkedHashMap<String, StoreApp>();
        Set<String> claimed = new HashSet<String>();
        for (StoreApp c : catalog) {
            if (!claimed.add(c.packageName)) continue; // first entry for a package wins
            if (c.source.type != ApkSource.Type.FDROID) {
                out.put(c.packageName, c);
                continue;
            }
            StoreApp f = index.get(c.packageName);
            if (f == null) {
                out.put(c.packageName, c);
            } else {
                StoreApp resolved = resolve(c, f);
                if (resolved != null) out.put(c.packageName, resolved);
            }
        }
        // A package the catalog mentions never comes back from the raw index, even when the
        // catalog's entry was dropped because the signer no longer matches its pin.
        for (StoreApp f : fdroid) {
            if (claimed.add(f.packageName)) out.put(f.packageName, f);
        }
        return new ArrayList<StoreApp>(out.values());
    }

    private static StoreApp resolve(StoreApp c, StoreApp f) {
        if (!c.signers.isEmpty() && !c.signers.containsAll(f.signers)) return null;
        return new StoreApp(
                c.name.equals(c.packageName) ? f.name : c.name,
                c.summary.isEmpty() ? f.summary : c.summary,
                c.packageName,
                c.kind,
                StoreApp.Origin.CATALOG,
                c.categories.isEmpty() ? f.categories : c.categories,
                c.signers.isEmpty() ? f.signers : c.signers,
                f.source,
                f.versionName,
                f.versionCode);
    }
}
