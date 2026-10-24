package com.xiddoc.androidautox.store;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;

/** An open HTTP response. Closing it releases the connection. */
public final class HttpResponse implements Closeable {

    /** Final URL after redirects. */
    public final String url;
    /** Length of {@link #body} in bytes, or -1 if not known (for example when compressed). */
    public final long contentLength;
    /** The response body, already decompressed. */
    public final InputStream body;
    private final Runnable release;

    public HttpResponse(String url, long contentLength, InputStream body, Runnable release) {
        this.url = url;
        this.contentLength = contentLength;
        this.body = body;
        this.release = release;
    }

    @Override
    public void close() throws IOException {
        try {
            body.close();
        } finally {
            release.run();
        }
    }
}
