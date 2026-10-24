package com.xiddoc.androidautox;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Turns "which flag names did {@link FlagPresence} find in Android Auto?" into a per-tweak
 * verdict. Only flags that live in the Android Auto app itself
 * ({@link FlagSpec#PKG_GEARHEAD}) can be checked this way; the Google Play services car flags
 * live in a different app, so a tweak made only of those reports {@link Status#UNKNOWN}.
 *
 * <p>The verdict is advisory: see {@link FlagPresence} for the limits of the method.
 */
public final class TweakCompatibility {

    private TweakCompatibility() {}

    /** How well a tweak's settings are found in the installed Android Auto. */
    public enum Status {
        /** Every checkable setting was found. */
        SUPPORTED,
        /** Some settings were found and some were not. */
        PARTIAL,
        /** None of the checkable settings were found, so the tweak likely does nothing. */
        MISSING,
        /** The tweak has no settings that can be checked this way. */
        UNKNOWN
    }

    /** The verdict for one tweak. */
    public static final class Result {
        public final String key;
        public final Status status;
        public final int found;
        public final int total;
        /** Names of the checkable settings that were not found, in spec order. */
        public final List<String> missing;

        Result(String key, Status status, int found, int total, List<String> missing) {
            this.key = key;
            this.status = status;
            this.found = found;
            this.total = total;
            this.missing = Collections.unmodifiableList(missing);
        }
    }

    /** The Android Auto flag names to look for across a set of specs, without duplicates. */
    public static Set<String> gearheadNames(Collection<List<FlagSpec>> specLists) {
        Set<String> names = new LinkedHashSet<String>();
        for (List<FlagSpec> specs : specLists) {
            if (specs == null) continue;
            for (FlagSpec s : specs) {
                if (isCheckable(s)) names.add(s.name);
            }
        }
        return names;
    }

    private static boolean isCheckable(FlagSpec s) {
        return !s.remove && FlagSpec.PKG_GEARHEAD.equals(s.pkg);
    }

    /** Evaluates one tweak against the names that were found in the app. */
    public static Result evaluate(String key, List<FlagSpec> specs, Set<String> present) {
        List<String> missing = new ArrayList<String>();
        // A set, so a flag listed twice in a tweak is counted once.
        Set<String> checked = new HashSet<String>();
        int total = 0;
        int found = 0;
        if (specs != null) {
            for (FlagSpec s : specs) {
                if (!isCheckable(s) || !checked.add(s.name)) continue;
                total++;
                if (present.contains(s.name)) {
                    found++;
                } else {
                    missing.add(s.name);
                }
            }
        }
        Status status;
        if (total == 0) {
            status = Status.UNKNOWN;
        } else if (found == total) {
            status = Status.SUPPORTED;
        } else if (found == 0) {
            status = Status.MISSING;
        } else {
            status = Status.PARTIAL;
        }
        return new Result(key, status, found, total, missing);
    }

    /** Orders results worst-first (missing, partial, unknown, supported), then by key. */
    public static void sortForDisplay(List<Result> results) {
        Collections.sort(results, new Comparator<Result>() {
            @Override
            public int compare(Result a, Result b) {
                int byStatus = Integer.compare(rank(a.status), rank(b.status));
                return byStatus != 0 ? byStatus : a.key.compareTo(b.key);
            }
        });
    }

    private static int rank(Status s) {
        switch (s) {
            case MISSING: return 0;
            case PARTIAL: return 1;
            case UNKNOWN: return 2;
            default:      return 3;
        }
    }
}
