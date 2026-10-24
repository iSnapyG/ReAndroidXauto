package com.xiddoc.androidautox.store;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class PmCommandsTest {

    @Test
    public void installCommand() {
        assertEquals("pm install -r -S 1234 < /data/user/0/x/cache/store/a.b.apk",
                PmCommands.install("/data/user/0/x/cache/store/a.b.apk", 1234));
    }

    @Test
    public void installRejectsUnsafeInput() {
        String[] paths = {null, "relative.apk", "/a b.apk", "/a;rm.apk", "/a$(x).apk", "/a.txt",
                "/a/../b.apk", "/a`x`.apk"};
        for (String path : paths) {
            try {
                PmCommands.install(path, 10);
                fail("expected IllegalArgumentException for " + path);
            } catch (IllegalArgumentException expected) {
                // ok
            }
        }
        try {
            PmCommands.install("/a.apk", 0);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // ok
        }
    }

    @Test
    public void uninstallCommand() {
        assertEquals("pm uninstall org.example.app", PmCommands.uninstall("org.example.app"));
        try {
            PmCommands.uninstall("org.example.app; reboot");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // ok
        }
    }

    @Test
    public void successDetection() {
        assertTrue(PmCommands.succeeded(Arrays.asList("noise", "  Success  ")));
        assertFalse(PmCommands.succeeded(Arrays.asList("Failure [X]", "Successful?")));
        assertFalse(PmCommands.succeeded(Collections.<String>emptyList()));
    }

    @Test
    public void failureReasons() {
        assertEquals("INSTALL_FAILED_VERSION_DOWNGRADE", PmCommands.failureReason(
                Arrays.asList("Performing Streamed Install", "Failure [INSTALL_FAILED_VERSION_DOWNGRADE]")));
        assertEquals("Failure without brackets", PmCommands.failureReason(
                Collections.singletonList("Failure without brackets")));
        assertEquals("Failure [unterminated", PmCommands.failureReason(
                Collections.singletonList("Failure [unterminated")));
        assertEquals("second", PmCommands.failureReason(
                Arrays.asList("Failure []", "Failure [second]")));
        assertEquals("unknown", PmCommands.failureReason(Arrays.asList("whatever", "")));
    }
}
