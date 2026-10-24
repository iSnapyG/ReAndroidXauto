package com.xiddoc.androidautox.store;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Shared builders and fakes for the store tests. */
final class StoreFixtures {

    static final String SIG_A = repeat('a');
    static final String SIG_B = repeat('b');
    static final String SIG_C = repeat('c');

    private StoreFixtures() {}

    static String repeat(char c) {
        char[] chars = new char[64];
        Arrays.fill(chars, c);
        return new String(chars);
    }

    static StoreApp direct(String pkg, String sha256, String... signers) {
        return new StoreApp(pkg, "summary of " + pkg, pkg, StoreApp.Kind.OTHER, StoreApp.Origin.CATALOG,
                Collections.<String>emptyList(), Arrays.asList(signers),
                ApkSource.direct("https://example.com/" + pkg + ".apk", sha256, 0), "1.0", 1);
    }

    static StoreApp github(String pkg, String... signers) {
        return new StoreApp(pkg, "", pkg, StoreApp.Kind.OTHER, StoreApp.Origin.CATALOG,
                Collections.<String>emptyList(), Arrays.asList(signers),
                ApkSource.github("owner/repo", "\\.apk$"), "", 0);
    }

    static StoreApp fdroidRef(String name, String pkg, StoreApp.Kind kind, String... signers) {
        return new StoreApp(name, "", pkg, kind, StoreApp.Origin.CATALOG,
                Collections.<String>emptyList(), Arrays.asList(signers), ApkSource.fdroid(), "", 0);
    }

    static StoreApp named(String name, String pkg, String summary, StoreApp.Kind kind,
                          long versionCode, String... categories) {
        return new StoreApp(name, summary, pkg, kind, StoreApp.Origin.CATALOG,
                Arrays.asList(categories), Collections.singletonList(SIG_A),
                ApkSource.direct("https://example.com/" + pkg + ".apk", SIG_B, 0), "v", versionCode);
    }

    static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    static InputStream stream(String s) {
        return new ByteArrayInputStream(bytes(s));
    }

    /** In-memory {@link HttpFetcher}: serves registered bodies, 404s for anything else. */
    static final class FakeHttp implements HttpFetcher {
        final Map<String, byte[]> bodies = new HashMap<String, byte[]>();
        final Map<String, Long> lengths = new HashMap<String, Long>();
        final List<String> requested = new ArrayList<String>();
        final List<String> accepts = new ArrayList<String>();

        FakeHttp serve(String url, byte[] body) {
            bodies.put(url, body);
            return this;
        }

        FakeHttp serve(String url, String body) {
            return serve(url, bytes(body));
        }

        @Override
        public HttpResponse get(String url, String accept) throws IOException {
            requested.add(url);
            accepts.add(accept);
            byte[] body = bodies.get(url);
            if (body == null) throw new IOException("HTTP 404");
            Long length = lengths.get(url);
            return new HttpResponse(url, length != null ? length : body.length,
                    new ByteArrayInputStream(body), new Runnable() {
                        @Override
                        public void run() {}
                    });
        }
    }

    /** An in-memory {@link InstallRecords}. */
    static final class MemoryRecords implements InstallRecords {
        final Map<String, InstallRecord> map = new java.util.LinkedHashMap<String, InstallRecord>();

        @Override
        public InstallRecord get(String packageName) {
            return map.get(packageName);
        }

        @Override
        public void put(InstallRecord record) {
            map.put(record.packageName, record);
        }

        @Override
        public void remove(String packageName) {
            map.remove(packageName);
        }

        @Override
        public List<InstallRecord> all() {
            return new ArrayList<InstallRecord>(map.values());
        }
    }

    /** A small but realistic F-Droid index-v2 document. */
    static String fdroidIndex() {
        return "{\"repo\":{\"name\":{\"en-US\":\"F-Droid\"}},\"packages\":{"
                + "\"org.example.player\":{\"metadata\":{\"name\":{\"en-US\":\"Player\"},"
                + "\"summary\":{\"en-US\":\"Plays things\"},\"categories\":[\"Multimedia\"],\"added\":1},"
                + "\"versions\":{"
                + "\"h1\":{\"file\":{\"name\":\"/org.example.player_1.apk\",\"sha256\":\"" + repeat('1') + "\",\"size\":100},"
                + "\"manifest\":{\"versionName\":\"1.0\",\"versionCode\":1,\"usesSdk\":{\"minSdkVersion\":21},"
                + "\"signer\":{\"sha256\":[\"" + SIG_A + "\"]}}},"
                + "\"h2\":{\"file\":{\"name\":\"/org.example.player_2.apk\",\"sha256\":\"" + repeat('2') + "\",\"size\":200},"
                + "\"manifest\":{\"versionName\":\"2.0\",\"versionCode\":2,\"usesSdk\":{\"minSdkVersion\":21},"
                + "\"signer\":{\"sha256\":[\"" + SIG_A + "\"]}}}}},"
                + "\"org.example.maps\":{\"metadata\":{\"name\":{\"en-US\":\"Maps\"},\"categories\":[\"Navigation\"]},"
                + "\"versions\":{\"h3\":{\"file\":{\"name\":\"/org.example.maps_5.apk\",\"sha256\":\"" + repeat('3') + "\",\"size\":300},"
                + "\"manifest\":{\"versionName\":\"5.0\",\"versionCode\":5,\"signer\":{\"sha256\":[\"" + SIG_B + "\"]}}}}}"
                + "}}";
    }
}
