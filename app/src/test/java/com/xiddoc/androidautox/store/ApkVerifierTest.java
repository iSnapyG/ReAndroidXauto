package com.xiddoc.androidautox.store;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class ApkVerifierTest {

    private static final StoreApp APP = StoreFixtures.direct("org.example.app", StoreFixtures.SIG_C,
            StoreFixtures.SIG_A, StoreFixtures.SIG_B);
    private static final String HASH = StoreFixtures.repeat('5');

    private static ApkVerifier.Facts facts(long size, String sha, String pkg, long code, String... signers) {
        return new ApkVerifier.Facts(size, sha, pkg, code, Arrays.asList(signers));
    }

    private static ApkVerifier.Verdict verify(String expected, long installed, ApkVerifier.Facts f) {
        return ApkVerifier.verify(APP, expected, 1000, installed, f);
    }

    @Test
    public void acceptsAGoodApk() {
        ApkVerifier.Facts f = facts(10, HASH, "org.example.app", 5, StoreFixtures.SIG_A);
        assertEquals(ApkVerifier.Verdict.OK, verify(HASH, -1, f));
        assertEquals(ApkVerifier.Verdict.OK, verify(null, -1, f));        // no hash promised
        assertEquals(ApkVerifier.Verdict.OK, verify(HASH, 5, f));         // same version: reinstall
        assertEquals(ApkVerifier.Verdict.OK, verify(HASH, 4, f));         // upgrade
        assertEquals(ApkVerifier.Verdict.OK, verify(HASH, -1,
                facts(10, HASH, "org.example.app", 5, StoreFixtures.SIG_A, StoreFixtures.SIG_B)));
    }

    @Test
    public void rejectsEachProblem() {
        assertEquals(ApkVerifier.Verdict.TOO_LARGE,
                verify(HASH, -1, facts(1001, HASH, "org.example.app", 5, StoreFixtures.SIG_A)));
        assertEquals(ApkVerifier.Verdict.HASH_MISMATCH,
                verify(StoreFixtures.repeat('6'), -1, facts(10, HASH, "org.example.app", 5, StoreFixtures.SIG_A)));
        assertEquals(ApkVerifier.Verdict.WRONG_PACKAGE,
                verify(HASH, -1, facts(10, HASH, "org.other.app", 5, StoreFixtures.SIG_A)));
        assertEquals(ApkVerifier.Verdict.UNSIGNED,
                verify(HASH, -1, facts(10, HASH, "org.example.app", 5)));
        assertEquals(ApkVerifier.Verdict.SIGNER_MISMATCH,
                verify(HASH, -1, facts(10, HASH, "org.example.app", 5, StoreFixtures.SIG_C)));
        assertEquals(ApkVerifier.Verdict.SIGNER_MISMATCH,   // one good signer is not enough
                verify(HASH, -1, facts(10, HASH, "org.example.app", 5, StoreFixtures.SIG_A, StoreFixtures.SIG_C)));
        assertEquals(ApkVerifier.Verdict.DOWNGRADE,
                verify(HASH, 6, facts(10, HASH, "org.example.app", 5, StoreFixtures.SIG_A)));
    }

    @Test
    public void noPinMeansNoInstall() {
        StoreApp unpinned = new StoreApp("n", "", "org.example.app", StoreApp.Kind.OTHER,
                StoreApp.Origin.CATALOG, Collections.<String>emptyList(), Collections.<String>emptyList(),
                ApkSource.fdroid(), "", 0);
        assertEquals(ApkVerifier.Verdict.NO_SIGNER_PIN, ApkVerifier.verify(unpinned, null, 1000, -1,
                facts(10, HASH, "org.example.app", 5, StoreFixtures.SIG_A)));
    }

    @Test
    public void factsCanonicaliseAndDropGarbageFingerprints() {
        ApkVerifier.Facts f = facts(1, HASH, "p.q", 1, StoreFixtures.SIG_A.toUpperCase(), "garbage");
        assertEquals(Collections.singletonList(StoreFixtures.SIG_A), f.signers);
    }
}
