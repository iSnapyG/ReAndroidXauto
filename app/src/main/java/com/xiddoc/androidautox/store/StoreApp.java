package com.xiddoc.androidautox.store;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * One app the store can install. Immutable and validated: an instance that exists has a safe
 * package name, a source, and (unless it is resolved through F-Droid) at least one pinned
 * signing-certificate fingerprint to check the downloaded APK against.
 */
public final class StoreApp {

    /** What the app is good for in the car. */
    public enum Kind {
        /** Declares Android Auto support itself (media, navigation, ...). */
        CAR,
        /** An ordinary phone app, meant to be shown on the car screen through AutoX. */
        AUTOX,
        /** Not classified (most F-Droid apps). */
        OTHER
    }

    public enum Origin { CATALOG, FDROID }

    public final String name;
    public final String summary;
    public final String packageName;
    public final Kind kind;
    public final Origin origin;
    public final List<String> categories;
    /** Lowercase hex SHA-256 fingerprints of the signing certificates the APK must carry. */
    public final List<String> signers;
    public final ApkSource source;
    /** Latest known version name, or empty when it is only known after resolving. */
    public final String versionName;
    /** Latest known version code, or 0 when unknown. */
    public final long versionCode;

    public StoreApp(String name, String summary, String packageName, Kind kind, Origin origin,
                    List<String> categories, List<String> signers, ApkSource source,
                    String versionName, long versionCode) {
        if (!StoreValidation.isPackageName(packageName)) {
            throw new IllegalArgumentException("bad package name");
        }
        if (kind == null || origin == null || source == null) {
            throw new IllegalArgumentException("kind, origin and source are required");
        }
        if (versionCode < 0) throw new IllegalArgumentException("negative version code");

        Set<String> pins = new LinkedHashSet<String>();
        if (signers != null) {
            for (String s : signers) {
                String hex = StoreValidation.sha256OrNull(s);
                if (hex == null) throw new IllegalArgumentException("bad signer fingerprint");
                pins.add(hex);
            }
        }
        if (source.type != ApkSource.Type.FDROID && pins.isEmpty()) {
            throw new IllegalArgumentException("a signer fingerprint is required");
        }

        List<String> cats = new ArrayList<String>();
        if (categories != null) {
            for (String c : categories) {
                String clean = StoreValidation.text(c, 40);
                if (!clean.isEmpty()) cats.add(clean);
            }
        }

        String cleanName = StoreValidation.text(name, 60);
        this.name = cleanName.isEmpty() ? packageName : cleanName;
        this.summary = StoreValidation.text(summary, 200);
        this.packageName = packageName;
        this.kind = kind;
        this.origin = origin;
        this.categories = Collections.unmodifiableList(cats);
        this.signers = Collections.unmodifiableList(new ArrayList<String>(pins));
        this.source = source;
        this.versionName = StoreValidation.text(versionName, 40);
        this.versionCode = versionCode;
    }

    /** Stable identifier, unique across origins. */
    public String id() {
        return (origin == Origin.FDROID ? "fdroid:" : "catalog:") + packageName;
    }
}
