package com.xiddoc.androidautox.store;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.security.MessageDigest;

public class StreamsTest {

    private static byte[] data(int n) {
        byte[] b = new byte[n];
        for (int i = 0; i < n; i++) b[i] = (byte) i;
        return b;
    }

    @Test
    public void readBoundedReturnsEverythingUnderTheLimit() throws IOException {
        assertArrayEquals(data(100), Streams.readBounded(new ByteArrayInputStream(data(100)), 100));
    }

    @Test(expected = Streams.TooLargeException.class)
    public void readBoundedStopsAtTheLimit() throws IOException {
        Streams.readBounded(new ByteArrayInputStream(data(101)), 100);
    }

    @Test
    public void copyReportsProgressAndHashes() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        MessageDigest md = Hashing.sha256();
        final long[] last = {0};
        long n = Streams.copyBounded(new ByteArrayInputStream(data(40000)), out, 1 << 20, md,
                new Streams.Progress() {
                    @Override
                    public boolean update(long bytesSoFar) {
                        last[0] = bytesSoFar;
                        return true;
                    }
                });
        assertEquals(40000, n);
        assertEquals(40000, last[0]);
        assertEquals(Hashing.sha256Hex(data(40000)), Hashing.hex(md.digest()));
        assertArrayEquals(data(40000), out.toByteArray());
    }

    @Test
    public void copyCanBeCancelled() throws IOException {
        try {
            Streams.copyBounded(new ByteArrayInputStream(data(10)), new ByteArrayOutputStream(), 100, null,
                    new Streams.Progress() {
                        @Override
                        public boolean update(long bytesSoFar) {
                            return false;
                        }
                    });
            fail("expected InterruptedIOException");
        } catch (InterruptedIOException expected) {
            // ok
        }
    }

    @Test
    public void boundedStreamCountsBothReadForms() throws IOException {
        InputStream ok = new Streams.Bounded(new ByteArrayInputStream(data(3)), 3);
        assertEquals(0, ok.read());
        assertEquals(0, ok.read(new byte[0], 0, 0));      // a zero-length read counts nothing
        assertEquals(2, ok.read(new byte[5], 0, 5));
        assertEquals(-1, ok.read());                      // end of stream is not counted
        assertEquals(-1, ok.read(new byte[5], 0, 5));

        try {
            new Streams.Bounded(new ByteArrayInputStream(data(5)), 4).read(new byte[10], 0, 10);
            fail("expected TooLargeException");
        } catch (Streams.TooLargeException expected) {
            // ok
        }
        InputStream single = new Streams.Bounded(new ByteArrayInputStream(data(5)), 1);
        single.read();
        try {
            single.read();
            fail("expected TooLargeException");
        } catch (Streams.TooLargeException expected) {
            // ok
        }
    }
}
