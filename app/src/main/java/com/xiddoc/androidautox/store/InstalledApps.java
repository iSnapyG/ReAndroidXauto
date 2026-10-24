package com.xiddoc.androidautox.store;

/** Asks the phone what is installed. */
public interface InstalledApps {

    /** The installed version code of a package, or -1 if it is not installed. */
    long versionCode(String packageName);
}
