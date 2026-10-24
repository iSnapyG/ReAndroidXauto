package com.xiddoc.androidautox.store;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class CustomAppsTest {

    private static final String SIG = StoreFixtures.SIG_A;

    private static CustomApps.Draft draft(String repo, String pkg, String signer, String pattern) {
        return CustomApps.githubApp("Mine", repo, pkg, signer, pattern, StoreApp.Kind.AUTOX);
    }

    @Test
    public void buildsAGithubApp() {
        CustomApps.Draft d = CustomApps.githubApp("  My   App ", " owner/repo ", " org.example.mine ",
                SIG.toUpperCase().replaceAll("(..)(?!$)", "$1:"), " arm64.*\\.apk ", StoreApp.Kind.CAR);
        assertEquals(CustomApps.Problem.NONE, d.problem);
        StoreApp app = d.app;
        assertEquals("My App", app.name);
        assertEquals("org.example.mine", app.packageName);
        assertEquals(StoreApp.Kind.CAR, app.kind);
        assertEquals(StoreApp.Origin.CATALOG, app.origin);
        assertEquals(java.util.Collections.singletonList(SIG), app.signers);
        assertEquals(ApkSource.Type.GITHUB, app.source.type);
        assertEquals("owner/repo", app.source.repo);
        assertEquals("arm64.*\\.apk", app.source.assetPattern);
        assertEquals("From github.com/owner/repo", app.summary);
    }

    @Test
    public void defaultsTheNameAndThePattern() {
        for (String pattern : new String[]{null, "", "   "}) {
            CustomApps.Draft d = CustomApps.githubApp(null, "owner/repo", "org.example.mine", SIG,
                    pattern, StoreApp.Kind.OTHER);
            assertEquals(CustomApps.Problem.NONE, d.problem);
            assertEquals("org.example.mine", d.app.name);
            assertEquals(CustomApps.DEFAULT_PATTERN, d.app.source.assetPattern);
        }
    }

    @Test
    public void reportsTheFirstProblem() {
        assertEquals(CustomApps.Problem.REPO, draft(null, "org.example.a", SIG, "").problem);
        assertEquals(CustomApps.Problem.REPO, draft("no-slash", "org.example.a", SIG, "").problem);
        assertEquals(CustomApps.Problem.REPO, draft("a/b/c", "org.example.a", SIG, "").problem);
        assertEquals(CustomApps.Problem.PACKAGE, draft("o/r", null, SIG, "").problem);
        assertEquals(CustomApps.Problem.PACKAGE, draft("o/r", "nodots", SIG, "").problem);
        assertEquals(CustomApps.Problem.PACKAGE, draft("o/r", "org.example.a; rm -rf /", SIG, "").problem);
        assertEquals(CustomApps.Problem.PROTECTED, draft("o/r", "com.google.android.gms", SIG, "").problem);
        assertEquals(CustomApps.Problem.PROTECTED, draft("o/r", "com.android.vending", SIG, "").problem);
        assertEquals(CustomApps.Problem.SIGNER, draft("o/r", "org.example.a", null, "").problem);
        assertEquals(CustomApps.Problem.SIGNER, draft("o/r", "org.example.a", "abc", "").problem);
        assertEquals(CustomApps.Problem.PATTERN, draft("o/r", "org.example.a", SIG, "(").problem);
        assertNull(draft("o/r", "org.example.a", SIG, "(").app);
        assertNotNull(draft("o/r", "org.example.a", SIG, "x").app);
    }

    private static StoreApp app(String pkg) {
        return CustomApps.githubApp("n", "o/r", pkg, SIG, "", StoreApp.Kind.OTHER).app;
    }

    @Test
    public void addReplacesAnAppWithTheSamePackage() {
        List<StoreApp> list = new ArrayList<StoreApp>();
        list = CustomApps.add(list, app("org.example.a"));
        list = CustomApps.add(list, app("org.example.b"));
        StoreApp newer = CustomApps.githubApp("Newer", "o/r2", "org.example.a", SIG, "", StoreApp.Kind.CAR).app;
        list = CustomApps.add(list, newer);
        assertEquals(2, list.size());
        assertEquals("org.example.b", list.get(0).packageName);
        assertEquals("Newer", list.get(1).name);
    }

    @Test
    public void removeDropsOnlyThatPackage() {
        List<StoreApp> list = CustomApps.add(CustomApps.add(new ArrayList<StoreApp>(),
                app("org.example.a")), app("org.example.b"));
        assertEquals(1, CustomApps.remove(list, "org.example.a").size());
        assertEquals(2, CustomApps.remove(list, "org.example.zzz").size());
    }

    @Test
    public void thereIsALimit() {
        List<StoreApp> list = new ArrayList<StoreApp>();
        for (int i = 0; i < CustomApps.MAX_APPS - 1; i++) {
            assertTrue(CustomApps.canAdd(list, app("org.example.app" + i)));
            list = CustomApps.add(list, app("org.example.app" + i));
        }
        assertTrue(CustomApps.canAdd(list, app("org.example.last")));       // one place left
        list = CustomApps.add(list, app("org.example.last"));
        assertEquals(CustomApps.MAX_APPS, list.size());
        assertFalse(CustomApps.canAdd(list, app("org.example.extra")));     // full: no new package
        assertTrue(CustomApps.canAdd(list, app("org.example.last")));       // full: replacing is fine
    }
}
