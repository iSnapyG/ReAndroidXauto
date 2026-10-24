package com.xiddoc.androidautox.store;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class StoreCatalogParserTest {

    private static final String SIG = StoreFixtures.SIG_A;
    private static final String SHA = StoreFixtures.SIG_B;

    private static StoreCatalogParser.Result parse(String json, int max) throws IOException {
        return StoreCatalogParser.parse(new StringReader(json), StoreApp.Origin.CATALOG, max);
    }

    private static void assertFormatError(String json) {
        try {
            parse(json, 10);
            fail("expected IOException for " + json);
        } catch (IOException expected) {
            // ok
        }
    }

    @Test
    public void readsEverySourceType() throws IOException {
        String json = "{\"version\":1,\"extra\":{\"x\":[1]},\"apps\":["
                + "{\"name\":\"Pod\",\"summary\":\"Podcasts\",\"package\":\"de.danoeh.antennapod\",\"kind\":\"car\","
                + "\"categories\":[\"Podcast\"],\"signer\":[\"" + SIG + "\"],\"fdroid\":true,\"unknown\":[1,2]},"
                + "{\"name\":\"Gh\",\"package\":\"org.example.gh\",\"kind\":\"autox\",\"signer\":[\"" + SIG + "\"],"
                + "\"github\":\"owner/repo\",\"asset\":\"arm64.*\\\\.apk\"},"
                + "{\"name\":\"Fixed\",\"package\":\"org.example.fixed\",\"kind\":\"other\",\"signer\":[\"" + SIG + "\"],"
                + "\"url\":\"https://example.com/a.apk\",\"sha256\":\"" + SHA + "\",\"size\":99,"
                + "\"versionName\":\"1.2\",\"versionCode\":12},"
                + "{\"package\":\"org.example.nokind\",\"signer\":[\"" + SIG + "\"],\"fdroid\":true}"
                + "]}";
        StoreCatalogParser.Result r = parse(json, 10);
        assertEquals(0, r.skipped);
        assertEquals(4, r.apps.size());
        StoreApp pod = r.apps.get(0);
        assertEquals(StoreApp.Kind.CAR, pod.kind);
        assertEquals(ApkSource.Type.FDROID, pod.source.type);
        assertEquals("Podcasts", pod.summary);
        assertEquals(Collections.singletonList("Podcast"), pod.categories);
        assertEquals(StoreApp.Kind.AUTOX, r.apps.get(1).kind);
        assertEquals("arm64.*\\.apk", r.apps.get(1).source.assetPattern);
        StoreApp fixed = r.apps.get(2);
        assertEquals(StoreApp.Kind.OTHER, fixed.kind);
        assertEquals(99, fixed.source.size);
        assertEquals("1.2", fixed.versionName);
        assertEquals(12, fixed.versionCode);
        assertEquals(StoreApp.Kind.OTHER, r.apps.get(3).kind);
    }

    @Test
    public void originIsStampedByTheCaller() throws IOException {
        String json = "{\"version\":1,\"apps\":[{\"package\":\"org.example.a\",\"signer\":[\"" + SIG
                + "\"],\"fdroid\":true}]}";
        StoreCatalogParser.Result r = StoreCatalogParser.parse(new StringReader(json),
                StoreApp.Origin.FDROID, 10);
        assertEquals(StoreApp.Origin.FDROID, r.apps.get(0).origin);
    }

    @Test
    public void invalidAppsAreSkippedNotFatal() throws IOException {
        String ok = "{\"package\":\"org.example.ok\",\"signer\":[\"" + SIG + "\"],\"fdroid\":true}";
        String json = "{\"version\":1,\"apps\":["
                + "5,"                                                                     // not an object
                + "{\"package\":\"org.example.nosource\",\"signer\":[\"" + SIG + "\"]},"   // no source
                + "{\"package\":\"org.example.two\",\"signer\":[\"" + SIG + "\"],\"fdroid\":true,"
                + "\"github\":\"owner/repo\",\"asset\":\"x\"},"                            // two sources
                + "{\"package\":\"org.example.kind\",\"kind\":\"weird\",\"signer\":[\"" + SIG + "\"],\"fdroid\":true},"
                + "{\"package\":\"bad\",\"signer\":[\"" + SIG + "\"],\"fdroid\":true},"   // bad package
                + "{\"package\":\"org.example.nopin\",\"fdroid\":false,\"url\":\"https://e.com/a.apk\","
                + "\"sha256\":\"" + SHA + "\"},"                                           // no signer pin
                + "{\"package\":\"org.example.badflag\",\"signer\":[\"" + SIG + "\"],\"fdroid\":\"yes\"},"
                + ok + "," + ok.replace("ok", "over") + "]}";
        StoreCatalogParser.Result r = parse(json, 1);
        assertEquals(1, r.apps.size());
        assertEquals("org.example.ok", r.apps.get(0).packageName);
        assertEquals(8, r.skipped);
    }

    @Test
    public void notACatalogIsAnError() {
        assertFormatError("{\"apps\":[]}");                       // no version
        assertFormatError("{\"version\":2,\"apps\":[]}");         // wrong version
        assertFormatError("{\"version\":1}");                     // no apps
        assertFormatError("{\"version\":1,\"apps\":5}");          // apps is not a list
        assertFormatError("[1,2]");                               // not an object
        assertFormatError("\"text\"");
        assertFormatError("{\"version\":1,\"apps\":[");           // truncated
        assertFormatError("");
    }

    @Test
    public void writeThenReadRoundTrips() throws IOException {
        StoreApp app = new StoreApp("Fixed", "Sum", "org.example.fixed", StoreApp.Kind.CAR,
                StoreApp.Origin.FDROID, Arrays.asList("A", "B"), Collections.singletonList(SIG),
                ApkSource.direct("https://example.com/a.apk", SHA, 77), "3.1", 31);
        StringWriter out = new StringWriter();
        StoreCatalogParser.write(Collections.singletonList(app), out);
        StoreCatalogParser.Result r = StoreCatalogParser.parse(new StringReader(out.toString()),
                StoreApp.Origin.FDROID, 10);
        assertEquals(1, r.apps.size());
        StoreApp back = r.apps.get(0);
        assertEquals(app.name, back.name);
        assertEquals(app.summary, back.summary);
        assertEquals(app.kind, back.kind);
        assertEquals(app.categories, back.categories);
        assertEquals(app.signers, back.signers);
        assertEquals(app.source.url, back.source.url);
        assertEquals(app.source.sha256, back.source.sha256);
        assertEquals(77, back.source.size);
        assertEquals(31, back.versionCode);
        assertEquals("3.1", back.versionName);
    }

    @Test
    public void writeRejectsSourcesItCannotRepresent() throws IOException {
        List<StoreApp> apps = Collections.singletonList(StoreFixtures.github("org.example.gh", SIG));
        try {
            StoreCatalogParser.write(apps, new StringWriter());
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("fixed-URL"));
        }
    }
}
