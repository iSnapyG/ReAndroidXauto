package com.xiddoc.androidautox.store;

import java.util.List;
import java.util.Map;

/** How a batch install went. */
public final class BatchSummary {

    /** Installed without a problem. */
    public final int ok;
    /** Tried and failed. */
    public final int failed;
    /** Never ran, or was cancelled. */
    public final int notRun;

    private BatchSummary(int ok, int failed, int notRun) {
        this.ok = ok;
        this.failed = failed;
        this.notRun = notRun;
    }

    public static BatchSummary of(List<StoreApp> apps, Map<String, StoreInstaller.Result> results) {
        int ok = 0, failed = 0, notRun = 0;
        for (StoreApp a : apps) {
            StoreInstaller.Result r = results.get(a.packageName);
            if (r == null || r.outcome == StoreInstaller.Outcome.CANCELLED) notRun++;
            else if (r.ok()) ok++;
            else failed++;
        }
        return new BatchSummary(ok, failed, notRun);
    }
}
