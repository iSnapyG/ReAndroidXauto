package com.xiddoc.androidautox.store;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.regex.Pattern;

/**
 * Input checks shared by everything in the store. Every string that ends up in a URL, a shell
 * command or the UI passes through here first, so the rest of the store can assume it is well
 * formed.
 */
public final class StoreValidation {

    private static final Pattern PACKAGE =
            Pattern.compile("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+");
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern GITHUB_REPO =
            Pattern.compile("[A-Za-z0-9_.-]{1,100}/[A-Za-z0-9_.-]{1,100}");

    private StoreValidation() {}

    /** A plausible Android package name, safe to put in a shell command. */
    public static boolean isPackageName(String s) {
        return s != null && s.length() <= 200 && PACKAGE.matcher(s).matches();
    }

    /**
     * Canonical (lowercase, no colons) form of a SHA-256 hex string, or null if it is not one.
     * Accepts the colon-separated form that keytool prints.
     */
    public static String sha256OrNull(String s) {
        if (s == null) return null;
        String hex = s.trim().replace(":", "").toLowerCase(java.util.Locale.ROOT);
        return SHA256.matcher(hex).matches() ? hex : null;
    }

    /** An absolute https URL with a host, no embedded credentials and no whitespace. */
    public static boolean isHttpsUrl(String s) {
        if (s == null || s.length() > 2000) return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c <= ' ' || c == 0x7f) return false;
        }
        try {
            URI uri = new URI(s);
            return "https".equals(uri.getScheme())
                    && uri.getHost() != null
                    && uri.getUserInfo() == null;
        } catch (URISyntaxException e) {
            return false;
        }
    }

    /** {@code owner/name} as used by GitHub. */
    public static boolean isGithubRepo(String s) {
        return s != null && GITHUB_REPO.matcher(s).matches() && !s.contains("..");
    }

    /**
     * Display text: control characters removed, whitespace collapsed and trimmed, cut to
     * {@code max} characters. Null becomes the empty string.
     */
    public static String text(String s, int max) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            sb.append(Character.isISOControl(c) ? ' ' : c);
        }
        String out = sb.toString().trim().replaceAll("\\s+", " ");
        return out.length() > max ? out.substring(0, max).trim() : out;
    }
}
