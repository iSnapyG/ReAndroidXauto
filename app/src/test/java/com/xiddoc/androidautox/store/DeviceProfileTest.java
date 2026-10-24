package com.xiddoc.androidautox.store;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class DeviceProfileTest {

    private final DeviceProfile device = new DeviceProfile(34, Arrays.asList("arm64-v8a", "armeabi-v7a"));

    @Test
    public void sdkRange() {
        assertTrue(device.accepts(21, 0, null));
        assertTrue(device.accepts(34, 34, null));
        assertFalse(device.accepts(35, 0, null));
        assertFalse(device.accepts(21, 30, null));
    }

    @Test
    public void cpuTypes() {
        assertTrue(device.accepts(0, 0, null));
        assertTrue(device.accepts(0, 0, Collections.<String>emptyList()));
        assertTrue(device.accepts(0, 0, Arrays.asList("x86_64", "arm64-v8a")));
        assertFalse(device.accepts(0, 0, Arrays.asList("x86_64", "x86")));
    }
}
