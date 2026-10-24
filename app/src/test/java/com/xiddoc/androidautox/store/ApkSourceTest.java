package com.xiddoc.androidautox.store;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import org.junit.Test;

public class ApkSourceTest {

    @Test
    public void directSource() {
        ApkSource s = ApkSource.direct("https://example.com/a.apk", StoreFixtures.SIG_A.toUpperCase(), 42);
        assertEquals(ApkSource.Type.DIRECT, s.type);
        assertEquals("https://example.com/a.apk", s.url);
        assertEquals(StoreFixtures.SIG_A, s.sha256);
        assertEquals(42, s.size);
        assertNull(s.repo);
    }

    @Test(expected = IllegalArgumentException.class)
    public void directRejectsHttp() {
        ApkSource.direct("http://example.com/a.apk", StoreFixtures.SIG_A, 0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void directRejectsBadHash() {
        ApkSource.direct("https://example.com/a.apk", "nothex", 0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void directRejectsNegativeSize() {
        ApkSource.direct("https://example.com/a.apk", StoreFixtures.SIG_A, -1);
    }

    @Test
    public void githubSource() {
        ApkSource s = ApkSource.github("owner/repo", "arm64.*\\.apk");
        assertEquals(ApkSource.Type.GITHUB, s.type);
        assertEquals("owner/repo", s.repo);
        assertEquals("arm64.*\\.apk", s.assetPattern);
        assertNull(s.url);
    }

    @Test(expected = IllegalArgumentException.class)
    public void githubRejectsBadRepo() {
        ApkSource.github("not a repo", "x");
    }

    @Test(expected = IllegalArgumentException.class)
    public void githubRejectsNullPattern() {
        ApkSource.github("owner/repo", null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void githubRejectsEmptyPattern() {
        ApkSource.github("owner/repo", "");
    }

    @Test(expected = IllegalArgumentException.class)
    public void githubRejectsLongPattern() {
        ApkSource.github("owner/repo", new String(new char[201]).replace('\0', 'a'));
    }

    @Test(expected = IllegalArgumentException.class)
    public void githubRejectsBrokenRegex() {
        ApkSource.github("owner/repo", "(");
    }

    @Test
    public void fdroidReferenceIsShared() {
        assertEquals(ApkSource.Type.FDROID, ApkSource.fdroid().type);
        assertSame(ApkSource.fdroid(), ApkSource.fdroid());
    }
}
