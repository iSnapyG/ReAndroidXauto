package com.xiddoc.androidautox.store;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Locale;
import java.util.zip.GZIPOutputStream;

/**
 * Talks to a tiny loopback HTTP server built on plain sockets (the JDK's own http server is not
 * visible to Android unit tests).
 */
public class JdkHttpFetcherTest {

    private ServerSocket server;
    private Thread acceptor;
    private String base;
    private volatile String seenAccept;
    private volatile String seenAgent;
    private volatile String seenEncoding;

    @Before
    public void start() throws IOException {
        server = new ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"));
        base = "http://127.0.0.1:" + server.getLocalPort();
        acceptor = new Thread(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    Socket client;
                    try {
                        client = server.accept();
                    } catch (IOException closed) {
                        return;
                    }
                    try {
                        serve(client);
                    } catch (IOException ignored) {
                        // the client went away; the test will notice
                    } finally {
                        try {
                            client.close();
                        } catch (IOException ignored) {
                            // nothing more to do
                        }
                    }
                }
            }
        });
        acceptor.setDaemon(true);
        acceptor.start();
    }

    @After
    public void stop() throws IOException {
        server.close();
    }

    private void serve(Socket client) throws IOException {
        BufferedReader in = new BufferedReader(
                new InputStreamReader(client.getInputStream(), "ISO-8859-1"));
        String requestLine = in.readLine();
        if (requestLine == null) return;
        String path = requestLine.split(" ")[1];
        int query = path.indexOf('?');
        if (query >= 0) path = path.substring(0, query);
        String line;
        while ((line = in.readLine()) != null && !line.isEmpty()) {
            int colon = line.indexOf(':');
            String name = line.substring(0, colon).toLowerCase(Locale.ROOT);
            String value = line.substring(colon + 1).trim();
            if (name.equals("accept")) seenAccept = value;
            else if (name.equals("user-agent")) seenAgent = value;
            else if (name.equals("accept-encoding")) seenEncoding = value;
        }
        OutputStream out = client.getOutputStream();
        if (path.equals("/ok")) {
            send(out, 200, "hello".getBytes("UTF-8"), null, false);
        } else if (path.equals("/gz")) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            GZIPOutputStream gz = new GZIPOutputStream(bos);
            gz.write("zipped".getBytes("UTF-8"));
            gz.close();
            send(out, 200, bos.toByteArray(), null, true);
        } else if (path.equals("/badgz")) {
            send(out, 200, "plain text, not gzip".getBytes("UTF-8"), null, true);
        } else if (path.startsWith("/redir/")) {
            send(out, Integer.parseInt(path.substring("/redir/".length())), null, "/ok", false);
        } else if (path.equals("/loop")) {
            send(out, 302, null, "/loop", false);
        } else if (path.equals("/nolocation")) {
            send(out, 302, null, null, false);
        } else if (path.equals("/badlocation")) {
            send(out, 302, null, "a b", false);
        } else if (path.equals("/offsite")) {
            send(out, 302, null, "http://example.com/x", false);
        } else {
            send(out, 404, "nope".getBytes("UTF-8"), null, false);
        }
        out.flush();
    }

    private static void send(OutputStream out, int status, byte[] body, String location,
                             boolean gzip) throws IOException {
        StringBuilder head = new StringBuilder("HTTP/1.1 ").append(status).append(" X\r\n");
        if (location != null) head.append("Location: ").append(location).append("\r\n");
        if (gzip) head.append("Content-Encoding: gzip\r\n");
        head.append("Content-Length: ").append(body == null ? 0 : body.length).append("\r\n");
        head.append("Connection: close\r\n\r\n");
        out.write(head.toString().getBytes("ISO-8859-1"));
        if (body != null) out.write(body);
    }

    private static String read(HttpResponse r) throws IOException {
        InputStream in = r.body;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[256];
        int n;
        while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
        r.close();
        return out.toString("UTF-8");
    }

    private void assertFails(String path) {
        try {
            new JdkHttpFetcher(true).get(base + path, "*/*").close();
            fail("expected IOException for " + path);
        } catch (IOException expected) {
            // ok
        }
    }

    @Test
    public void fetchesAndSendsHeaders() throws IOException {
        HttpResponse r = new JdkHttpFetcher(true).get(base + "/ok", "text/test");
        assertEquals(5, r.contentLength);
        assertEquals(base + "/ok", r.url);
        assertEquals("hello", read(r));
        assertEquals("text/test", seenAccept);
        assertEquals("AndroidAutoX-Store/1", seenAgent);
        assertEquals("gzip", seenEncoding);
    }

    @Test
    public void decompressesGzip() throws IOException {
        HttpResponse r = new JdkHttpFetcher(true).get(base + "/gz", "*/*");
        assertEquals(-1, r.contentLength);
        assertEquals("zipped", read(r));
    }

    @Test
    public void followsEveryKindOfRedirect() throws IOException {
        for (int status : new int[]{301, 302, 303, 307, 308}) {
            HttpResponse r = new JdkHttpFetcher(true).get(base + "/redir/" + status, "*/*");
            assertEquals(base + "/ok", r.url);
            assertEquals("hello", read(r));
        }
    }

    @Test
    public void failuresAreIoExceptions() {
        assertFails("/missing");
        assertFails("/loop");
        assertFails("/nolocation");
        assertFails("/badlocation");
        assertFails("/offsite");
        assertFails("/badgz");
    }

    @Test
    public void productionFetcherRefusesAnythingButHttps() {
        String[] urls = {base + "/ok", "http://example.com/", "ftp://example.com/x", "file:///etc/passwd", "garbage"};
        for (String url : urls) {
            try {
                new JdkHttpFetcher().get(url, "*/*");
                fail("expected IOException for " + url);
            } catch (IOException expected) {
                assertTrue(expected.getMessage().contains("non-https"));
            }
        }
    }

    @Test
    public void addressRules() {
        JdkHttpFetcher production = new JdkHttpFetcher();
        JdkHttpFetcher testing = new JdkHttpFetcher(true);
        assertTrue(production.allowed("https://f-droid.org/repo/x.apk"));
        assertTrue(testing.allowed("https://f-droid.org/repo/x.apk"));
        assertFalse(production.allowed("http://127.0.0.1:80/x"));
        assertTrue(testing.allowed("http://127.0.0.1:80/x"));
        assertFalse(testing.allowed("http://example.com/x"));
    }

    @Test
    public void unparseableLoopbackAddressIsRejected() {
        try {
            new JdkHttpFetcher(true).get("http://127.0.0.1:1/a b", "*/*");
            fail("expected IOException");
        } catch (IOException expected) {
            assertEquals("bad address", expected.getMessage());
        }
    }
}
