package com.xiddoc.androidautox.store;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

public class HttpResponseTest {

    @Test
    public void closeClosesBodyAndReleases() throws IOException {
        final boolean[] released = {false};
        final boolean[] closed = {false};
        InputStream body = new ByteArrayInputStream(new byte[0]) {
            @Override
            public void close() {
                closed[0] = true;
            }
        };
        HttpResponse r = new HttpResponse("https://e.com/", 5, body, new Runnable() {
            @Override
            public void run() {
                released[0] = true;
            }
        });
        assertEquals("https://e.com/", r.url);
        assertEquals(5, r.contentLength);
        r.close();
        assertTrue(closed[0]);
        assertTrue(released[0]);
    }

    @Test
    public void releasesEvenWhenTheBodyFailsToClose() {
        final boolean[] released = {false};
        InputStream body = new InputStream() {
            @Override
            public int read() {
                return -1;
            }

            @Override
            public void close() throws IOException {
                throw new IOException("boom");
            }
        };
        HttpResponse r = new HttpResponse("u", -1, body, new Runnable() {
            @Override
            public void run() {
                released[0] = true;
            }
        });
        try {
            r.close();
            fail("expected IOException");
        } catch (IOException expected) {
            assertTrue(released[0]);
        }
    }
}
