package com.xiddoc.androidautox.store;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Decides whether a downloaded APK may be installed. Nothing is installed unless every check
 * passes; the checks run in an order that reports the most useful reason first.
 */
public final class ApkVerifier {

    public enum Verdict {
        OK,
        /** Bigger than the allowed maximum. */
        TOO_LARGE,
        /** Not the file the catalog or index promised. */
        HASH_MISMATCH,
        /** The APK is for a different app than the one requested. */
        WRONG_PACKAGE,
        /** The catalog gives no signing certificate to check against. */
        NO_SIGNER_PIN,
        /** The APK carries no readable signature. */
        UNSIGNED,
        /** Signed by a certificate other than the pinned one(s). */
        SIGNER_MISMATCH,
        /** Older than the version already installed. */
        DOWNGRADE
    }

    /** What was found out about the downloaded file. */
    public static final class Facts {
        public final long size;
        public final String sha256;
        public final String packageName;
        public final long versionCode;
        public final List<String> signers;

        public Facts(long size, String sha256, String packageName, long versionCode,
                     List<String> signers) {
            this.size = size;
            this.sha256 = sha256;
            this.packageName = packageName;
            this.versionCode = versionCode;
            List<String> canon = new ArrayList<String>();
            for (String s : signers) {
                String hex = StoreValidation.sha256OrNull(s);
                if (hex != null) canon.add(hex);
            }
            this.signers = Collections.unmodifiableList(canon);
        }
    }

    private ApkVerifier() {}

    /**
     * @param app                  what was requested; its {@code signers} are the pins
     * @param expectedSha256       the hash promised for this exact file, or null if none is known
     * @param maxBytes             size limit
     * @param installedVersionCode version code now installed, or -1 if not installed
     */
    public static Verdict verify(StoreApp app, String expectedSha256, long maxBytes,
                                 long installedVersionCode, Facts facts) {
        if (facts.size > maxBytes) return Verdict.TOO_LARGE;
        if (expectedSha256 != null && !expectedSha256.equals(facts.sha256)) {
            return Verdict.HASH_MISMATCH;
        }
        if (!app.packageName.equals(facts.packageName)) return Verdict.WRONG_PACKAGE;
        if (app.signers.isEmpty()) return Verdict.NO_SIGNER_PIN;
        if (facts.signers.isEmpty()) return Verdict.UNSIGNED;
        for (String signer : facts.signers) {
            if (!app.signers.contains(signer)) return Verdict.SIGNER_MISMATCH;
        }
        if (installedVersionCode >= 0 && facts.versionCode < installedVersionCode) {
            return Verdict.DOWNGRADE;
        }
        return Verdict.OK;
    }
}
