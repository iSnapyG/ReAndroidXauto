package com.xiddoc.androidautox.store;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class StoreValidationTest {

    @Test
    public void packageNames() {
        assertTrue(StoreValidation.isPackageName("com.example.app"));
        assertTrue(StoreValidation.isPackageName("org.videolan.vlc"));
        assertFalse(StoreValidation.isPackageName(null));
        assertFalse(StoreValidation.isPackageName("nodots"));
        assertFalse(StoreValidation.isPackageName("com..x"));
        assertFalse(StoreValidation.isPackageName("1com.x"));
        assertFalse(StoreValidation.isPackageName("com.x;rm -rf /"));
        assertFalse(StoreValidation.isPackageName("a.b" + new String(new char[200]).replace('\0', 'c')));
    }

    @Test
    public void sha256Canonicalisation() {
        assertNull(StoreValidation.sha256OrNull(null));
        assertNull(StoreValidation.sha256OrNull("abc"));
        assertNull(StoreValidation.sha256OrNull(StoreFixtures.SIG_A + "00"));
        assertEquals(StoreFixtures.SIG_A, StoreValidation.sha256OrNull(StoreFixtures.SIG_A));
        assertEquals(StoreFixtures.SIG_A, StoreValidation.sha256OrNull("  " + StoreFixtures.SIG_A.toUpperCase() + " "));
        StringBuilder colons = new StringBuilder();
        for (int i = 0; i < 32; i++) colons.append(i == 0 ? "" : ":").append("AA");
        assertEquals(StoreFixtures.SIG_A, StoreValidation.sha256OrNull(colons.toString()));
    }

    @Test
    public void httpsUrls() {
        assertTrue(StoreValidation.isHttpsUrl("https://example.com/a.apk"));
        assertFalse(StoreValidation.isHttpsUrl(null));
        assertFalse(StoreValidation.isHttpsUrl("http://example.com/a.apk"));
        assertFalse(StoreValidation.isHttpsUrl("https:///nohost"));
        assertFalse(StoreValidation.isHttpsUrl("https://user:pw@example.com/"));
        assertFalse(StoreValidation.isHttpsUrl("https://exa mple.com/"));
        assertFalse(StoreValidation.isHttpsUrl("https://exa\u007fmple.com/"));
        assertFalse(StoreValidation.isHttpsUrl("https://[::1/"));
        assertFalse(StoreValidation.isHttpsUrl("https://example.com/" + new String(new char[2000]).replace('\0', 'x')));
    }

    @Test
    public void githubRepos() {
        assertTrue(StoreValidation.isGithubRepo("owner/repo"));
        assertTrue(StoreValidation.isGithubRepo("a-b.c/d_e"));
        assertFalse(StoreValidation.isGithubRepo(null));
        assertFalse(StoreValidation.isGithubRepo("owner"));
        assertFalse(StoreValidation.isGithubRepo("a/b/c"));
        assertFalse(StoreValidation.isGithubRepo("../repo"));
        assertFalse(StoreValidation.isGithubRepo("a..b/c"));
    }

    @Test
    public void displayText() {
        assertEquals("", StoreValidation.text(null, 10));
        assertEquals("a b c", StoreValidation.text("  a\t\nb\u0000   c ", 20));
        assertEquals("abc", StoreValidation.text("abcdef", 3));
        assertEquals("ab", StoreValidation.text("ab cd", 3));
        assertEquals("short", StoreValidation.text("short", 5));
    }
}
