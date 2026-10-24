package com.xiddoc.androidautox.store;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PackagePolicyTest {

    @Test
    public void protectedPackages() {
        String[] protectedNames = {"android", "android.foo", "com.android.settings", "com.google.android.gms",
                "com.google.android.gsf", "com.google.android.projection.gearhead", "com.android.vending",
                "com.topjohnwu.magisk", "org.lsposed.manager", PackagePolicy.OWN_PACKAGE};
        for (String name : protectedNames) {
            assertTrue(name, PackagePolicy.isProtected(name));
        }
        assertFalse(PackagePolicy.isProtected("org.videolan.vlc"));
        assertFalse(PackagePolicy.isProtected("com.google.android.apps.maps"));
    }

    @Test
    public void uninstallRules() {
        assertTrue(PackagePolicy.canUninstall("org.videolan.vlc", true));
        assertFalse(PackagePolicy.canUninstall("org.videolan.vlc", false));
        assertFalse(PackagePolicy.canUninstall("com.android.settings", true));
    }
}
