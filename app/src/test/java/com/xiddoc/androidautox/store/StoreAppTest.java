package com.xiddoc.androidautox.store;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class StoreAppTest {

    private static final ApkSource DIRECT =
            ApkSource.direct("https://example.com/a.apk", StoreFixtures.SIG_B, 0);

    private static StoreApp make(String name, String pkg, StoreApp.Kind kind, StoreApp.Origin origin,
                                 List<String> cats, List<String> signers, ApkSource src, long code) {
        return new StoreApp(name, "summary", pkg, kind, origin, cats, signers, src, "1.0", code);
    }

    private static void assertRejected(StoreApp.Kind kind, StoreApp.Origin origin, ApkSource src,
                                       String pkg, List<String> signers, long code) {
        try {
            make("n", pkg, kind, origin, null, signers, src, code);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // ok
        }
    }

    @Test
    public void validAppIsCleanedUp() {
        StoreApp a = make("  My\u0000 App  ", "com.example.app", StoreApp.Kind.CAR,
                StoreApp.Origin.CATALOG, Arrays.asList("Media", "  ", "Podcast"),
                Arrays.asList(StoreFixtures.SIG_A.toUpperCase(), StoreFixtures.SIG_A), DIRECT, 7);
        assertEquals("My App", a.name);
        assertEquals(Arrays.asList("Media", "Podcast"), a.categories);
        assertEquals(Collections.singletonList(StoreFixtures.SIG_A), a.signers);
        assertEquals("catalog:com.example.app", a.id());
        assertEquals(7, a.versionCode);
        assertEquals("1.0", a.versionName);
    }

    @Test
    public void nameDefaultsToPackage() {
        StoreApp a = make(null, "com.example.app", StoreApp.Kind.OTHER, StoreApp.Origin.FDROID,
                null, Collections.singletonList(StoreFixtures.SIG_A), DIRECT, 0);
        assertEquals("com.example.app", a.name);
        assertEquals("fdroid:com.example.app", a.id());
        assertTrue(a.categories.isEmpty());
    }

    @Test
    public void fdroidReferenceNeedsNoSigner() {
        StoreApp a = make("n", "com.example.app", StoreApp.Kind.OTHER, StoreApp.Origin.CATALOG,
                null, null, ApkSource.fdroid(), 0);
        assertTrue(a.signers.isEmpty());
    }

    @Test
    public void rejectsInvalidInput() {
        List<String> ok = Collections.singletonList(StoreFixtures.SIG_A);
        assertRejected(StoreApp.Kind.OTHER, StoreApp.Origin.CATALOG, DIRECT, "bad", ok, 0);
        assertRejected(null, StoreApp.Origin.CATALOG, DIRECT, "com.example.app", ok, 0);
        assertRejected(StoreApp.Kind.OTHER, null, DIRECT, "com.example.app", ok, 0);
        assertRejected(StoreApp.Kind.OTHER, StoreApp.Origin.CATALOG, null, "com.example.app", ok, 0);
        assertRejected(StoreApp.Kind.OTHER, StoreApp.Origin.CATALOG, DIRECT, "com.example.app", ok, -1);
        assertRejected(StoreApp.Kind.OTHER, StoreApp.Origin.CATALOG, DIRECT, "com.example.app",
                Collections.singletonList("nothex"), 0);
        assertRejected(StoreApp.Kind.OTHER, StoreApp.Origin.CATALOG, DIRECT, "com.example.app",
                Collections.<String>emptyList(), 0);
        assertRejected(StoreApp.Kind.OTHER, StoreApp.Origin.CATALOG, DIRECT, "com.example.app", null, 0);
    }
}
