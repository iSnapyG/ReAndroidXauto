package com.xiddoc.androidautox.store;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * What the phone can run: its Android version and CPU types. Used to ignore APK builds that
 * would not install here (for example VLC publishes one APK per CPU type, and the highest
 * version code belongs to the x86_64 build).
 */
public final class DeviceProfile {

    public final int sdkInt;
    public final List<String> abis;

    public DeviceProfile(int sdkInt, List<String> abis) {
        this.sdkInt = sdkInt;
        this.abis = Collections.unmodifiableList(new ArrayList<String>(abis));
    }

    /**
     * @param minSdk     the APK's minimum Android version (0 if unspecified)
     * @param maxSdk     the APK's maximum Android version, or 0 for none
     * @param nativeCode the CPU types the APK contains code for; null or empty means it is
     *                   CPU independent
     */
    public boolean accepts(int minSdk, int maxSdk, List<String> nativeCode) {
        if (minSdk > sdkInt) return false;
        if (maxSdk > 0 && sdkInt > maxSdk) return false;
        if (nativeCode == null || nativeCode.isEmpty()) return true;
        for (String abi : nativeCode) {
            if (abis.contains(abi)) return true;
        }
        return false;
    }
}
