package com.xiddoc.androidautox.store;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class InstallRecordTest {

    @Test
    public void constructorCleansAndDefaults() {
        InstallRecord r = new InstallRecord("org.example.app", "  ", 3, " 1.0\n", 99);
        assertEquals("org.example.app", r.name);
        assertEquals("1.0", r.versionName);
        assertEquals(3, r.versionCode);
        assertEquals(99, r.installedAtMillis);
        assertEquals("Named", new InstallRecord("org.example.app", "Named", 0, null, 0).name);
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructorRejectsBadPackage() {
        new InstallRecord("bad", "n", 1, "1", 1);
    }

    @Test
    public void roundTrip() {
        List<InstallRecord> in = Arrays.asList(
                new InstallRecord("org.example.a", "A", 1, "1.0", 10),
                new InstallRecord("org.example.b", "B", 2, "2.0", 20));
        List<InstallRecord> out = InstallRecord.decode(InstallRecord.encode(in));
        assertEquals(2, out.size());
        assertEquals("org.example.b", out.get(1).packageName);
        assertEquals("B", out.get(1).name);
        assertEquals(2, out.get(1).versionCode);
        assertEquals("2.0", out.get(1).versionName);
        assertEquals(20, out.get(1).installedAtMillis);
    }

    @Test
    public void emptyListRoundTrips() {
        assertEquals("[]", InstallRecord.encode(Collections.<InstallRecord>emptyList()));
        assertTrue(InstallRecord.decode("[]").isEmpty());
    }

    @Test
    public void decodeToleratesDamage() {
        assertTrue(InstallRecord.decode(null).isEmpty());
        assertTrue(InstallRecord.decode("").isEmpty());
        assertTrue(InstallRecord.decode("not json").isEmpty());
        assertTrue(InstallRecord.decode("{}").isEmpty());
        // bad entries are skipped, unknown keys ignored, a truncated tail keeps what came before
        String json = "[3,{\"package\":\"bad\"},{\"package\":\"org.example.a\",\"extra\":[1],\"versionCode\":\"7\"},"
                + "{\"package\":";
        List<InstallRecord> out = InstallRecord.decode(json);
        assertEquals(1, out.size());
        assertEquals(7, out.get(0).versionCode);
    }
}
