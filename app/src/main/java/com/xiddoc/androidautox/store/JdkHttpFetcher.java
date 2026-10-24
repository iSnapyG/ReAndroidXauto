package com.xiddoc.androidautox.store;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.zip.GZIPInputStream;

/**
 * {@link HttpFetcher} on {@link HttpURLConnection}. Redirects are followed by hand so that
 * every hop is checked: only https is ever contacted (plain http to the loopback address is
 * allowed solely for tests).
 */
public final class JdkHttpFetcher implements HttpFetcher {

    static final int MAX_REDIRECTS = 5;
    private static final int CONNECT_TIMEOUT_MS = 15_000;
    private static final int READ_TIMEOUT_MS = 30_000;

    private final boolean allowLoopbackHttp;

    public JdkHttpFetcher() {
        this(false);
    }

    /** @param allowLoopbackHttp test hook: also accept {@code http://127.0.0.1:...} */
    JdkHttpFetcher(boolean allowLoopbackHttp) {
        this.allowLoopbackHttp = allowLoopbackHttp;
    }

    @Override
    public HttpResponse get(String url, String accept) throws IOException {
        String current = url;
        for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
            if (!allowed(current)) throw new IOException("refusing to fetch a non-https address");
            final HttpURLConnection conn = (HttpURLConnection) toUrl(current).openConnection();
            conn.setInstanceFollowRedirects(false);
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setRequestProperty("User-Agent", "AndroidAutoX-Store/1");
            conn.setRequestProperty("Accept", accept);
            conn.setRequestProperty("Accept-Encoding", "gzip");
            try {
                int status = conn.getResponseCode();
                if (status / 100 == 2) {
                    return open(current, conn);
                }
                if (status == 301 || status == 302 || status == 303 || status == 307 || status == 308) {
                    current = redirectTarget(current, conn.getHeaderField("Location"));
                    conn.disconnect();
                    continue;
                }
                throw new IOException("HTTP " + status);
            } catch (IOException e) {
                conn.disconnect();
                throw e;
            }
        }
        throw new IOException("too many redirects");
    }

    private static HttpResponse open(String url, final HttpURLConnection conn) throws IOException {
        InputStream raw = conn.getInputStream();
        boolean gzip = "gzip".equalsIgnoreCase(conn.getContentEncoding());
        long length = gzip ? -1 : conn.getContentLengthLong();
        InputStream body = gzip ? new GZIPInputStream(raw) : raw;
        return new HttpResponse(url, length, body, new Runnable() {
            @Override
            public void run() {
                conn.disconnect();
            }
        });
    }

    private static URL toUrl(String address) throws IOException {
        try {
            return new URI(address).toURL();
        } catch (URISyntaxException e) {
            throw new IOException("bad address");
        }
    }

    private static String redirectTarget(String base, String location) throws IOException {
        if (location == null) throw new IOException("redirect without a location");
        try {
            return new URI(base).resolve(location).toString();
        } catch (URISyntaxException | IllegalArgumentException e) {
            throw new IOException("bad redirect location");
        }
    }

    boolean allowed(String url) {
        if (StoreValidation.isHttpsUrl(url)) return true;
        return allowLoopbackHttp && url.startsWith("http://127.0.0.1:");
    }
}
