package com.xiddoc.androidautox.store;

import java.util.List;

/** The store's memory of what it installed (persisted by the Android glue). */
public interface InstallRecords {

    /** The record for a package, or null if the store did not install it. */
    InstallRecord get(String packageName);

    /** Adds or replaces the record for {@code record.packageName}. */
    void put(InstallRecord record);

    void remove(String packageName);

    List<InstallRecord> all();
}
