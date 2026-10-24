package com.xiddoc.androidautox.store;

/**
 * Which packages the store will never install over or remove. A catalog (especially a
 * remote one) must not be able to point the store at the system, at Android Auto itself, at
 * the root manager, or at this app.
 */
public final class PackagePolicy {

    /** Package name of this app. */
    public static final String OWN_PACKAGE = "com.xiddoc.androidautox";

    private static final String[] PROTECTED_EXACT = {
            "android",
            "com.google.android.gms",
            "com.google.android.gsf",
            "com.google.android.projection.gearhead",
            "com.android.vending",
            "com.topjohnwu.magisk",
            "org.lsposed.manager",
            OWN_PACKAGE,
    };

    private PackagePolicy() {}

    /** True for packages the store must leave alone. */
    public static boolean isProtected(String packageName) {
        if (packageName.startsWith("com.android.") || packageName.startsWith("android.")) {
            return true;
        }
        for (String p : PROTECTED_EXACT) {
            if (p.equals(packageName)) return true;
        }
        return false;
    }

    /**
     * Whether the store may uninstall {@code packageName}: only apps it installed itself
     * ({@code installedByStore}), and never a protected package.
     */
    public static boolean canUninstall(String packageName, boolean installedByStore) {
        return installedByStore && !isProtected(packageName);
    }
}
