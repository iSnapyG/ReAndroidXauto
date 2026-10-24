package com.xiddoc.androidautox.store;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/** Where an app's APK comes from. Instances are validated when they are created. */
public final class ApkSource {

    public enum Type {
        /** A fixed https URL whose SHA-256 is known up front. */
        DIRECT,
        /** The newest release of a GitHub repository, picked by an asset-name pattern. */
        GITHUB,
        /** Looked up by package name in the downloaded F-Droid index. */
        FDROID
    }

    private static final ApkSource FDROID_REF = new ApkSource(Type.FDROID, null, null, 0, null, null);

    public final Type type;
    /** DIRECT only. */
    public final String url;
    /** DIRECT only: lowercase hex SHA-256 of the APK. */
    public final String sha256;
    /** DIRECT only: size in bytes, or 0 when unknown. */
    public final long size;
    /** GITHUB only: {@code owner/name}. */
    public final String repo;
    /** GITHUB only: regex searched for in release asset names. */
    public final String assetPattern;

    private ApkSource(Type type, String url, String sha256, long size, String repo, String assetPattern) {
        this.type = type;
        this.url = url;
        this.sha256 = sha256;
        this.size = size;
        this.repo = repo;
        this.assetPattern = assetPattern;
    }

    public static ApkSource direct(String url, String sha256, long size) {
        if (!StoreValidation.isHttpsUrl(url)) throw new IllegalArgumentException("url must be https");
        String hash = StoreValidation.sha256OrNull(sha256);
        if (hash == null) throw new IllegalArgumentException("sha256 must be 64 hex characters");
        if (size < 0) throw new IllegalArgumentException("negative size");
        return new ApkSource(Type.DIRECT, url, hash, size, null, null);
    }

    public static ApkSource github(String repo, String assetPattern) {
        if (!StoreValidation.isGithubRepo(repo)) throw new IllegalArgumentException("bad github repo");
        if (assetPattern == null || assetPattern.isEmpty() || assetPattern.length() > 200) {
            throw new IllegalArgumentException("bad asset pattern");
        }
        try {
            Pattern.compile(assetPattern);
        } catch (PatternSyntaxException e) {
            throw new IllegalArgumentException("asset pattern is not a valid regex");
        }
        return new ApkSource(Type.GITHUB, null, null, 0, repo, assetPattern);
    }

    public static ApkSource fdroid() {
        return FDROID_REF;
    }
}
