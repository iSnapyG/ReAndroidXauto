package com.xiddoc.androidautox.store;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Apps the user adds themselves from a GitHub repository, for example a patched build that a
 * community repository publishes. They are checked like every other app: the download must
 * carry the signing key the user entered.
 */
public final class CustomApps {

    public static final int MAX_APPS = 50;
    /** Used when the user leaves the asset pattern empty: any file ending in ".apk". */
    public static final String DEFAULT_PATTERN = "\\.apk$";

    public enum Problem { NONE, REPO, PACKAGE, PROTECTED, SIGNER, PATTERN }

    /** A new app, or the first problem found with the input. */
    public static final class Draft {
        public final StoreApp app;
        public final Problem problem;

        private Draft(StoreApp app, Problem problem) {
            this.app = app;
            this.problem = problem;
        }
    }

    private CustomApps() {}

    /**
     * @param name         shown in the store; may be empty (the package name is used)
     * @param repo         {@code owner/name} on GitHub
     * @param packageName  package name of the app inside the APK
     * @param signer       SHA-256 of the signing certificate, with or without colons
     * @param assetPattern regex picking the release file by name; empty for any ".apk"
     * @param kind         how the app is meant to be used in the car
     */
    public static Draft githubApp(String name, String repo, String packageName, String signer,
                                  String assetPattern, StoreApp.Kind kind) {
        String cleanRepo = repo == null ? "" : repo.trim();
        if (!StoreValidation.isGithubRepo(cleanRepo)) return problem(Problem.REPO);
        String pkg = packageName == null ? "" : packageName.trim();
        if (!StoreValidation.isPackageName(pkg)) return problem(Problem.PACKAGE);
        if (PackagePolicy.isProtected(pkg)) return problem(Problem.PROTECTED);
        String pin = StoreValidation.sha256OrNull(signer);
        if (pin == null) return problem(Problem.SIGNER);
        String pattern = assetPattern == null || assetPattern.trim().isEmpty()
                ? DEFAULT_PATTERN : assetPattern.trim();
        ApkSource source;
        try {
            source = ApkSource.github(cleanRepo, pattern);
        } catch (IllegalArgumentException e) {
            return problem(Problem.PATTERN);
        }
        StoreApp app = new StoreApp(name, "From github.com/" + cleanRepo, pkg, kind,
                StoreApp.Origin.CATALOG, Collections.<String>emptyList(),
                Collections.singletonList(pin), source, "", 0);
        return new Draft(app, Problem.NONE);
    }

    /** True if {@code app} can be added: it replaces an app with the same package, or there is room. */
    public static boolean canAdd(List<StoreApp> existing, StoreApp app) {
        if (existing.size() < MAX_APPS) return true;
        for (StoreApp a : existing) {
            if (a.packageName.equals(app.packageName)) return true;
        }
        return false;
    }

    /** {@code existing} with {@code app} added, replacing any app with the same package. */
    public static List<StoreApp> add(List<StoreApp> existing, StoreApp app) {
        List<StoreApp> out = remove(existing, app.packageName);
        out.add(app);
        return out;
    }

    /** {@code existing} without the app of that package. */
    public static List<StoreApp> remove(List<StoreApp> existing, String packageName) {
        List<StoreApp> out = new ArrayList<StoreApp>();
        for (StoreApp a : existing) {
            if (!a.packageName.equals(packageName)) out.add(a);
        }
        return out;
    }

    private static Draft problem(Problem p) {
        return new Draft(null, p);
    }
}
