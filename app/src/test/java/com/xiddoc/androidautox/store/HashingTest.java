package com.xiddoc.androidautox.store;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.Test;

public class HashingTest {

    @Test
    public void hexOfKnownInput() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                Hashing.sha256Hex(StoreFixtures.bytes("abc")));
        assertEquals("00ff80", Hashing.hex(new byte[]{0, (byte) 0xff, (byte) 0x80}));
    }

    @Test
    public void missingAlgorithmIsReported() {
        String saved = Hashing.algorithm;
        Hashing.algorithm = "NO-SUCH-DIGEST";
        try {
            Hashing.sha256();
            fail("expected IllegalStateException");
        } catch (IllegalStateException expected) {
            // ok
        } finally {
            Hashing.algorithm = saved;
        }
        assertEquals(32, Hashing.sha256().getDigestLength());
    }
}
