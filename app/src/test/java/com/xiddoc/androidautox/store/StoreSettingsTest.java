package com.xiddoc.androidautox.store;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.xiddoc.androidautox.FakeSharedPreferences;

import org.junit.Test;

public class StoreSettingsTest {

    @Test
    public void fdroidIsOffUntilTheUserAllowsIt() {
        StoreSettings s = new StoreSettings(new FakeSharedPreferences());
        assertFalse(s.fdroidEnabled());
        s.setFdroidEnabled(true);
        assertTrue(s.fdroidEnabled());
        s.setFdroidEnabled(false);
        assertFalse(s.fdroidEnabled());
    }

    @Test
    public void catalogUrlMustBeHttps() {
        FakeSharedPreferences prefs = new FakeSharedPreferences();
        StoreSettings s = new StoreSettings(prefs);
        assertEquals("", s.remoteUrl());
        assertTrue(s.setRemoteUrl("  https://example.com/catalog.json "));
        assertEquals("https://example.com/catalog.json", s.remoteUrl());
        assertFalse(s.setRemoteUrl("http://example.com/catalog.json"));
        assertEquals("https://example.com/catalog.json", s.remoteUrl());   // unchanged
        assertTrue(s.setRemoteUrl(null));
        assertEquals("", s.remoteUrl());
        assertTrue(s.setRemoteUrl("https://example.com/c.json"));
        assertTrue(s.setRemoteUrl("   "));
        assertEquals("", s.remoteUrl());
    }

    @Test
    public void aDamagedStoredUrlReadsAsEmpty() {
        FakeSharedPreferences prefs = new FakeSharedPreferences();
        prefs.edit().putString(StoreSettings.KEY_REMOTE_URL, "ftp://nope").apply();
        assertEquals("", new StoreSettings(prefs).remoteUrl());
    }
}
