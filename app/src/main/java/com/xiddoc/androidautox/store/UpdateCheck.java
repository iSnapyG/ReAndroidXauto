package com.xiddoc.androidautox.store;

/** Compares what the catalog offers with what is installed. */
public final class UpdateCheck {

    public enum Status {
        NOT_INSTALLED,
        CURRENT,
        UPDATE_AVAILABLE,
        /** Installed, but the catalog does not say which version is newest (GitHub sources). */
        INSTALLED_VERSION_UNKNOWN
    }

    private UpdateCheck() {}

    /**
     * @param catalogVersionCode   newest version code the catalog knows, 0 if unknown
     * @param installedVersionCode version code now installed, -1 if not installed
     */
    public static Status status(long catalogVersionCode, long installedVersionCode) {
        if (installedVersionCode < 0) return Status.NOT_INSTALLED;
        if (catalogVersionCode <= 0) return Status.INSTALLED_VERSION_UNKNOWN;
        return catalogVersionCode > installedVersionCode ? Status.UPDATE_AVAILABLE : Status.CURRENT;
    }
}
