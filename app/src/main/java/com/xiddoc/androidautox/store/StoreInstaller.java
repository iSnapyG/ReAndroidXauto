package com.xiddoc.androidautox.store;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.List;
import java.util.function.LongSupplier;

/**
 * Downloads, checks and silently installs (or removes) apps through the root shell.
 *
 * <p>Order of play for an install: refuse protected packages and a missing root; work out
 * which file to fetch; download it with a size cap while hashing it; read the APK's package,
 * version and signing certificates; run {@link ApkVerifier}; only then hand the file to
 * {@code pm install}. The downloaded file is deleted afterwards whatever happens.
 */
public final class StoreInstaller {

    public static final long MAX_APK_BYTES = 400L * 1024 * 1024;
    static final long MAX_RELEASE_JSON_BYTES = 2L * 1024 * 1024;

    public enum Stage { RESOLVING, DOWNLOADING, VERIFYING, INSTALLING, REMOVING }

    public enum Outcome {
        OK,
        NO_ROOT,
        INVALID,
        PROTECTED,
        /** The app is only known through F-Droid and the index is not downloaded. */
        NOT_RESOLVED,
        /** No usable download could be found for the app. */
        NO_DOWNLOAD,
        DOWNLOAD_FAILED,
        TOO_LARGE,
        HASH_MISMATCH,
        WRONG_PACKAGE,
        NO_SIGNER_PIN,
        UNSIGNED,
        SIGNER_MISMATCH,
        DOWNGRADE,
        UNREADABLE_APK,
        INSTALL_FAILED,
        UNINSTALL_FAILED,
        NOT_INSTALLED_BY_STORE,
        CANCELLED
    }

    public static final class Result {
        public final Outcome outcome;
        /** Extra detail, for example the reason {@code pm} gave; may be empty. */
        public final String detail;

        Result(Outcome outcome, String detail) {
            this.outcome = outcome;
            this.detail = detail;
        }

        public boolean ok() {
            return outcome == Outcome.OK;
        }
    }

    /** Receives progress and can cancel. */
    public interface Listener {
        /** @param percent 0-100, or -1 when the size is not known */
        void onStage(Stage stage, int percent);

        boolean cancelled();
    }

    private final HttpFetcher http;
    private final File workDir;
    private final ApkInspector inspector;
    private final InstalledApps installed;
    private final RootShell shell;
    private final InstallRecords records;
    private final LongSupplier clock;
    private final long maxApkBytes;

    public StoreInstaller(HttpFetcher http, File workDir, ApkInspector inspector,
                          InstalledApps installed, RootShell shell, InstallRecords records,
                          LongSupplier clock) {
        this(http, workDir, inspector, installed, shell, records, clock, MAX_APK_BYTES);
    }

    /** Test hook: a smaller size limit. */
    StoreInstaller(HttpFetcher http, File workDir, ApkInspector inspector,
                   InstalledApps installed, RootShell shell, InstallRecords records,
                   LongSupplier clock, long maxApkBytes) {
        this.maxApkBytes = maxApkBytes;
        this.http = http;
        this.workDir = workDir;
        this.inspector = inspector;
        this.installed = installed;
        this.shell = shell;
        this.records = records;
        this.clock = clock;
    }

    public Result install(StoreApp app, Listener listener) {
        if (PackagePolicy.isProtected(app.packageName)) return result(Outcome.PROTECTED);
        if (!shell.isRoot()) return result(Outcome.NO_ROOT);

        listener.onStage(Stage.RESOLVING, -1);
        String url;
        String expectedSha;
        long expectedSize;
        String versionLabel = app.versionName;
        if (app.source.type == ApkSource.Type.FDROID) {
            return result(Outcome.NOT_RESOLVED);
        } else if (app.source.type == ApkSource.Type.DIRECT) {
            url = app.source.url;
            expectedSha = app.source.sha256;
            expectedSize = app.source.size;
        } else {
            GithubRelease.Pick pick;
            try {
                pick = latestGithub(app.source);
            } catch (IOException e) {
                return new Result(Outcome.NO_DOWNLOAD, message(e));
            }
            if (pick == null) return result(Outcome.NO_DOWNLOAD);
            url = pick.url;
            expectedSha = pick.sha256;
            expectedSize = pick.size;
            versionLabel = pick.tag;
        }

        File apk = new File(workDir, app.packageName + ".apk");
        try {
            Files.createDirectories(workDir.toPath());
            return downloadAndInstall(app, url, expectedSha, expectedSize, versionLabel, apk, listener);
        } catch (InterruptedIOException e) {
            return result(Outcome.CANCELLED);
        } catch (Streams.TooLargeException e) {
            return result(Outcome.TOO_LARGE);
        } catch (IOException e) {
            return new Result(Outcome.DOWNLOAD_FAILED, message(e));
        } finally {
            //noinspection ResultOfMethodCallIgnored
            apk.delete();
        }
    }

    public Result uninstall(String packageName, Listener listener) {
        if (!StoreValidation.isPackageName(packageName)) return result(Outcome.INVALID);
        InstallRecord record = records.get(packageName);
        if (PackagePolicy.isProtected(packageName)) return result(Outcome.PROTECTED);
        if (!PackagePolicy.canUninstall(packageName, record != null)) {
            return result(Outcome.NOT_INSTALLED_BY_STORE);
        }
        if (installed.versionCode(packageName) < 0) {
            records.remove(packageName); // already gone; just forget it
            return result(Outcome.OK);
        }
        if (!shell.isRoot()) return result(Outcome.NO_ROOT);
        listener.onStage(Stage.REMOVING, -1);
        List<String> out = shell.run(PmCommands.uninstall(packageName));
        if (!PmCommands.succeeded(out)) {
            return new Result(Outcome.UNINSTALL_FAILED, PmCommands.failureReason(out));
        }
        records.remove(packageName);
        return result(Outcome.OK);
    }

    private Result downloadAndInstall(StoreApp app, String url, String expectedSha, long expectedSize,
                                      String versionLabel, File apk, final Listener listener)
            throws IOException {
        listener.onStage(Stage.DOWNLOADING, 0);
        MessageDigest digest = Hashing.sha256();
        long size;
        HttpResponse response = http.get(url, "application/vnd.android.package-archive, */*");
        try {
            final long total = response.contentLength > 0 ? response.contentLength : expectedSize;
            OutputStream out = new BufferedOutputStream(Files.newOutputStream(apk.toPath()));
            try {
                size = Streams.copyBounded(response.body, out, maxApkBytes, digest,
                        new Streams.Progress() {
                            @Override
                            public boolean update(long bytesSoFar) {
                                int percent = total > 0 ? (int) Math.min(99, bytesSoFar * 100 / total) : -1;
                                listener.onStage(Stage.DOWNLOADING, percent);
                                return !listener.cancelled();
                            }
                        });
            } finally {
                out.close();
            }
        } finally {
            response.close();
        }

        listener.onStage(Stage.VERIFYING, -1);
        ApkInspector.Info info = inspector.inspect(apk);
        if (info == null) return result(Outcome.UNREADABLE_APK);
        ApkVerifier.Facts facts = new ApkVerifier.Facts(size, Hashing.hex(digest.digest()),
                info.packageName, info.versionCode, info.signers);
        ApkVerifier.Verdict verdict = ApkVerifier.verify(app, expectedSha, maxApkBytes,
                installed.versionCode(app.packageName), facts);
        if (verdict != ApkVerifier.Verdict.OK) return result(Outcome.valueOf(verdict.name()));

        listener.onStage(Stage.INSTALLING, -1);
        List<String> output = shell.run(PmCommands.install(apk.getAbsolutePath(), size));
        if (!PmCommands.succeeded(output)) {
            return new Result(Outcome.INSTALL_FAILED, PmCommands.failureReason(output));
        }
        records.put(new InstallRecord(app.packageName, app.name, info.versionCode, versionLabel,
                clock.getAsLong()));
        return result(Outcome.OK);
    }

    private GithubRelease.Pick latestGithub(ApkSource source) throws IOException {
        String api = "https://api.github.com/repos/" + source.repo + "/releases/latest";
        HttpResponse response = http.get(api, "application/vnd.github+json");
        try {
            byte[] json = Streams.readBounded(response.body, MAX_RELEASE_JSON_BYTES);
            return GithubRelease.pick(
                    new InputStreamReader(new java.io.ByteArrayInputStream(json), StandardCharsets.UTF_8),
                    source.repo, source.assetPattern);
        } finally {
            response.close();
        }
    }

    private static String message(IOException e) {
        return StoreValidation.text(e.getClass().getSimpleName() + ": " + e.getMessage(), 120);
    }

    /** A result with no detail ({@code OK} included). */
    private static Result result(Outcome outcome) {
        return new Result(outcome, "");
    }
}
