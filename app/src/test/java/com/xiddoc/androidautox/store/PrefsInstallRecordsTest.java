package com.xiddoc.androidautox.store;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.xiddoc.androidautox.FakeSharedPreferences;

import org.junit.Test;

public class PrefsInstallRecordsTest {

    @Test
    public void recordsPersistAcrossInstances() {
        FakeSharedPreferences prefs = new FakeSharedPreferences();
        PrefsInstallRecords records = new PrefsInstallRecords(prefs);
        assertTrue(records.all().isEmpty());
        assertNull(records.get("org.example.a"));

        records.put(new InstallRecord("org.example.a", "A", 1, "1.0", 5));
        records.put(new InstallRecord("org.example.b", "B", 2, "2.0", 6));
        records.put(new InstallRecord("org.example.a", "A", 3, "3.0", 7));      // replaces

        PrefsInstallRecords again = new PrefsInstallRecords(prefs);
        assertEquals(2, again.all().size());
        assertEquals(3, again.get("org.example.a").versionCode);
        assertEquals("B", again.get("org.example.b").name);

        again.remove("org.example.a");
        again.remove("org.example.never");
        assertNull(again.get("org.example.a"));
        assertEquals(1, new PrefsInstallRecords(prefs).all().size());
    }

    @Test
    public void damagedStorageReadsAsEmpty() {
        FakeSharedPreferences prefs = new FakeSharedPreferences();
        prefs.edit().putString(PrefsInstallRecords.KEY, "{{{").apply();
        assertTrue(new PrefsInstallRecords(prefs).all().isEmpty());
    }
}
