package com.xiddoc.androidautox.store;

import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.security.MessageDigest;

/** Size-limited stream helpers: nothing from the network is read without a cap. */
final class Streams {

    /** More data arrived than the caller allowed. */
    static final class TooLargeException extends IOException {
        private static final long serialVersionUID = 1L;

        TooLargeException() {
            super("response is larger than allowed");
        }
    }

    /** Called with the running byte count; return false to cancel. */
    interface Progress {
        boolean update(long bytesSoFar);
    }

    private Streams() {}

    static byte[] readBounded(InputStream in, long max) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        copyBounded(in, out, max, null, null);
        return out.toByteArray();
    }

    /**
     * Copies {@code in} to {@code out}, feeding {@code digest} (if any) and reporting to
     * {@code progress} (if any).
     *
     * @throws TooLargeException        if more than {@code max} bytes arrive
     * @throws InterruptedIOException   if {@code progress} asks to cancel
     */
    static long copyBounded(InputStream in, OutputStream out, long max, MessageDigest digest,
                            Progress progress) throws IOException {
        byte[] buf = new byte[16 * 1024];
        long total = 0;
        int n;
        while ((n = in.read(buf)) != -1) {
            total += n;
            if (total > max) throw new TooLargeException();
            out.write(buf, 0, n);
            if (digest != null) digest.update(buf, 0, n);
            if (progress != null && !progress.update(total)) {
                throw new InterruptedIOException("cancelled");
            }
        }
        return total;
    }

    /** An {@link InputStream} that fails once more than {@code max} bytes have been read. */
    static final class Bounded extends FilterInputStream {
        private final long max;
        private long seen;

        Bounded(InputStream in, long max) {
            super(in);
            this.max = max;
        }

        @Override
        public int read() throws IOException {
            int b = super.read();
            if (b != -1) count(1);
            return b;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int n = super.read(b, off, len);
            if (n > 0) count(n);
            return n;
        }

        private void count(int n) throws IOException {
            seen += n;
            if (seen > max) throw new TooLargeException();
        }
    }
}
