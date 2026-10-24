package com.xiddoc.androidautox.store;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class InstallChoiceTest {

    private final StoreApp versioned =
            StoreFixtures.named("A", "org.example.a", "s", StoreApp.Kind.OTHER, 5);
    private final StoreApp unversioned = StoreFixtures.github("org.example.gh", StoreFixtures.SIG_A);
    private final StoreApp unresolved =
            StoreFixtures.fdroidRef("F", "org.example.f", StoreApp.Kind.CAR, StoreFixtures.SIG_A);

    @Test
    public void statesFollowTheInstalledVersion() {
        assertEquals(InstallChoice.State.AVAILABLE, InstallChoice.of(versioned, -1));
        assertEquals(InstallChoice.State.UPDATE, InstallChoice.of(versioned, 4));
        assertEquals(InstallChoice.State.INSTALLED, InstallChoice.of(versioned, 5));
        assertEquals(InstallChoice.State.INSTALLED, InstallChoice.of(versioned, 9));
    }

    @Test
    public void githubAppsCannotSayWhetherTheyAreCurrent() {
        assertEquals(InstallChoice.State.AVAILABLE, InstallChoice.of(unversioned, -1));
        assertEquals(InstallChoice.State.RECHECK, InstallChoice.of(unversioned, 3));
    }

    @Test
    public void unresolvedFdroidAppsHaveNoDownload() {
        assertEquals(InstallChoice.State.UNAVAILABLE, InstallChoice.of(unresolved, -1));
        assertEquals(InstallChoice.State.UNAVAILABLE, InstallChoice.of(unresolved, 7));
    }

    @Test
    public void onlyInstallableStatesCanBeTicked() {
        assertTrue(InstallChoice.selectable(InstallChoice.State.AVAILABLE));
        assertTrue(InstallChoice.selectable(InstallChoice.State.UPDATE));
        assertTrue(InstallChoice.selectable(InstallChoice.State.RECHECK));
        assertFalse(InstallChoice.selectable(InstallChoice.State.INSTALLED));
        assertFalse(InstallChoice.selectable(InstallChoice.State.UNAVAILABLE));
    }
}
