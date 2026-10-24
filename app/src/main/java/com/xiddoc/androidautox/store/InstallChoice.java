package com.xiddoc.androidautox.store;

/** What the install list can offer for one app right now. */
public final class InstallChoice {

    public enum State {
        /** Not on the phone; can be installed. */
        AVAILABLE,
        /** Installed, and the catalog has a newer version. */
        UPDATE,
        /** Installed, but the catalog cannot say whether it is current (GitHub sources). */
        RECHECK,
        /** Installed and current. */
        INSTALLED,
        /** Only known through F-Droid and the index is not downloaded, so there is no download. */
        UNAVAILABLE
    }

    private InstallChoice() {}

    /** @param installedVersionCode version code now installed, -1 if not installed */
    public static State of(StoreApp app, long installedVersionCode) {
        if (app.source.type == ApkSource.Type.FDROID) return State.UNAVAILABLE;
        switch (UpdateCheck.status(app.versionCode, installedVersionCode)) {
            case NOT_INSTALLED:
                return State.AVAILABLE;
            case UPDATE_AVAILABLE:
                return State.UPDATE;
            case INSTALLED_VERSION_UNKNOWN:
                return State.RECHECK;
            default:
                return State.INSTALLED;
        }
    }

    /** True if ticking the app makes sense. */
    public static boolean selectable(State state) {
        return state == State.AVAILABLE || state == State.UPDATE || state == State.RECHECK;
    }
}
