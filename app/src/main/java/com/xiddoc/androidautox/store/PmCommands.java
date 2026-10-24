package com.xiddoc.androidautox.store;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Builds and reads the root-shell commands that install and remove apps. Everything that is
 * interpolated into a command is validated first, so a hostile catalog cannot inject shell.
 */
public final class PmCommands {

    private static final Pattern APK_PATH = Pattern.compile("/[A-Za-z0-9._/-]{1,300}\\.apk");

    private PmCommands() {}

    /**
     * {@code pm install} fed from the file on stdin (works for files the package installer
     * could not read directly, and needs no copy to /data/local/tmp).
     *
     * @throws IllegalArgumentException if the path or size is not acceptable
     */
    public static String install(String apkPath, long size) {
        if (apkPath == null || !APK_PATH.matcher(apkPath).matches() || apkPath.contains("..")) {
            throw new IllegalArgumentException("bad apk path");
        }
        if (size <= 0) throw new IllegalArgumentException("bad apk size");
        return "pm install -r -S " + size + " < " + apkPath;
    }

    public static String uninstall(String packageName) {
        if (!StoreValidation.isPackageName(packageName)) {
            throw new IllegalArgumentException("bad package name");
        }
        return "pm uninstall " + packageName;
    }

    /** True if {@code pm}'s output reports success. */
    public static boolean succeeded(List<String> output) {
        for (String line : output) {
            if (line.trim().equals("Success")) return true;
        }
        return false;
    }

    /**
     * A short reason taken from {@code pm}'s output, for example
     * {@code INSTALL_FAILED_VERSION_DOWNGRADE}; "unknown" when none can be found.
     */
    public static String failureReason(List<String> output) {
        for (String line : output) {
            String t = line.trim();
            if (!t.startsWith("Failure")) continue;
            int open = t.indexOf('[');
            int close = t.indexOf(']', open + 1);
            String reason = open >= 0 && close > open ? t.substring(open + 1, close) : t;
            String clean = StoreValidation.text(reason, 120);
            if (!clean.isEmpty()) return clean;
        }
        return "unknown";
    }
}
