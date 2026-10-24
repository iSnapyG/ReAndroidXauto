package com.xiddoc.androidautox.store;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.io.IOException;
import java.io.StringReader;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class FdroidIndexParserTest {

    private static final DeviceProfile DEVICE = new DeviceProfile(34, Arrays.asList("arm64-v8a"));
    private static final String SHA = StoreFixtures.repeat('9');
    private static final String SIG = StoreFixtures.SIG_A;

    private static List<StoreApp> parse(String json, int max) throws IOException {
        return FdroidIndexParser.parse(new StringReader(json), DEVICE, max);
    }

    /** One package entry with a single version; {@code manifest} and {@code file} are raw JSON. */
    private static String pkg(String name, String versionBody) {
        return "\"" + name + "\":{\"metadata\":{\"name\":\"N\"},\"versions\":{\"h\":" + versionBody + "}}";
    }

    private static String version(String file, String manifest) {
        return "{\"file\":" + file + ",\"manifest\":" + manifest + "}";
    }

    private static String goodFile(String name) {
        return "{\"name\":\"" + name + "\",\"sha256\":\"" + SHA + "\",\"size\":10,\"x\":1}";
    }

    private static String goodManifest(String extra) {
        return "{\"versionName\":\"1\",\"versionCode\":3,\"signer\":{\"sha256\":[\"" + SIG + "\"]}" + extra + "}";
    }

    private static List<StoreApp> single(String versionBody) throws IOException {
        return parse("{\"packages\":{" + pkg("org.example.a", versionBody) + "}}", 10);
    }

    @Test
    public void picksNewestInstallableVersionAndMetadata() throws IOException {
        List<StoreApp> apps = parse(StoreFixtures.fdroidIndex(), 10);
        assertEquals(2, apps.size());
        StoreApp player = apps.get(0);
        assertEquals("org.example.player", player.packageName);
        assertEquals("Player", player.name);
        assertEquals("Plays things", player.summary);
        assertEquals(Collections.singletonList("Multimedia"), player.categories);
        assertEquals(StoreApp.Origin.FDROID, player.origin);
        assertEquals(StoreApp.Kind.OTHER, player.kind);
        assertEquals(2, player.versionCode);
        assertEquals("2.0", player.versionName);
        assertEquals("https://f-droid.org/repo/org.example.player_2.apk", player.source.url);
        assertEquals(StoreFixtures.repeat('2'), player.source.sha256);
        assertEquals(200, player.source.size);
        assertEquals(Collections.singletonList(SIG), player.signers);
        assertEquals("org.example.maps", apps.get(1).packageName);
    }

    @Test
    public void newestVersionWinsWhateverTheOrder() throws IOException {
        String newer = version(goodFile("/a_5.apk"), codeManifest(5));
        String older = version(goodFile("/a_2.apk"), codeManifest(2));
        String json = "{\"packages\":{\"org.example.a\":{\"metadata\":{\"name\":\"N\"},\"versions\":{"
                + "\"h1\":" + newer + ",\"h2\":" + older + "}}}}";
        List<StoreApp> apps = parse(json, 10);
        assertEquals(1, apps.size());
        assertEquals(5, apps.get(0).versionCode);
    }

    private static String codeManifest(int code) {
        return "{\"versionName\":\"v" + code + "\",\"versionCode\":" + code
                + ",\"signer\":{\"sha256\":[\"" + SIG + "\"]}}";
    }

    @Test
    public void respectsMaxApps() throws IOException {
        assertEquals(1, parse(StoreFixtures.fdroidIndex(), 1).size());
    }

    @Test
    public void filtersByDevice() throws IOException {
        String x86 = version(goodFile("/x86.apk"), goodManifest(",\"nativecode\":[\"x86_64\"]"));
        String arm = version(goodFile("/arm.apk").replace("\"size\":10", "\"size\":11"),
                "{\"versionName\":\"1\",\"versionCode\":2,\"nativecode\":[\"arm64-v8a\"],"
                        + "\"usesSdk\":{\"minSdkVersion\":21,\"maxSdkVersion\":40,\"targetSdkVersion\":34},"
                        + "\"signer\":{\"sha256\":[\"" + SIG + "\"]}}");
        String json = "{\"packages\":{\"org.example.a\":{\"versions\":{\"x\":" + x86 + ",\"y\":" + arm + "}}}}";
        List<StoreApp> apps = parse(json, 10);
        assertEquals(1, apps.size());
        assertEquals("https://f-droid.org/repo/arm.apk", apps.get(0).source.url);
        assertEquals("org.example.a", apps.get(0).name);
    }

    @Test
    public void skipsVersionsThatCannotBeInstalled() throws IOException {
        String signer = "\"signer\":{\"sha256\":[\"" + SIG + "\"]}";
        assertTrue(single(version("null", goodManifest(""))).isEmpty());                          // no file
        assertTrue(single(version(goodFile("../evil.apk"), goodManifest(""))).isEmpty());       // bad name
        assertTrue(single(version(goodFile("/a.apk").replace(SHA, "zz"), goodManifest(""))).isEmpty());
        assertTrue(single(version(goodFile("/a.apk"),
                "{\"versionCode\":3}")).isEmpty());                                              // no signer
        assertTrue(single(version(goodFile("/a.apk"),
                "{\"versionCode\":0," + signer + "}")).isEmpty());                              // no version code
        assertTrue(single(version(goodFile("/a.apk"),
                goodManifest(",\"usesSdk\":{\"minSdkVersion\":99}"))).isEmpty());               // too new
        assertTrue(single("5").isEmpty());                                                       // not an object
        assertEquals(1, single(version(goodFile("/a.apk"), goodManifest(""))).size());
    }

    @Test
    public void toleratesOddShapes() throws IOException {
        String signer = "\"signer\":{\"sha256\":[\"" + SIG + "\"]}";
        // file / manifest / usesSdk / signer that are not objects, plus unknown keys
        String body = "{\"file\":5,\"manifest\":6,\"other\":[1]}";
        assertTrue(single(body).isEmpty());
        String manifest = "{\"versionCode\":3,\"usesSdk\":7,\"signer\":8,\"unknown\":1}";
        assertTrue(single(version(goodFile("/a.apk"), manifest)).isEmpty());
        String manifest2 = "{\"versionCode\":3,\"usesSdk\":{\"other\":1}," + signer.replace("sha256", "ignored")
                .replace("[\"" + SIG + "\"]", "1") + ",\"signer\":{\"x\":1,\"sha256\":[\"" + SIG + "\"]}}";
        assertEquals(1, single(version(goodFile("/a.apk"), manifest2)).size());
        // metadata and versions that are not objects, a package with no versions
        String json = "{\"packages\":{\"org.example.a\":{\"metadata\":3,\"versions\":4,\"x\":1},"
                + "\"org.example.b\":9,\"org.example.c\":{\"versions\":{}}}}";
        assertTrue(parse(json, 10).isEmpty());
    }

    @Test
    public void metadataFields() throws IOException {
        String json = "{\"packages\":{\"org.example.a\":{\"metadata\":{\"name\":{\"fr\":\"Nom\"},"
                + "\"summary\":\"S\",\"categories\":[\"X\"],\"license\":\"GPL\"},\"versions\":{\"h\":"
                + version(goodFile("/a.apk"), goodManifest("")) + "}}}}";
        StoreApp app = parse(json, 10).get(0);
        assertEquals("Nom", app.name);
        assertEquals("S", app.summary);
        assertEquals(Collections.singletonList("X"), app.categories);
    }

    @Test
    public void invalidPackageNamesAreDropped() throws IOException {
        String json = "{\"packages\":{" + pkg("not a package", version(goodFile("/a.apk"), goodManifest(""))) + "}}";
        assertTrue(parse(json, 10).isEmpty());
    }

    @Test
    public void rejectsDocumentsThatAreNotIndexes() {
        for (String json : new String[]{"{\"repo\":{}}", "{\"packages\":3}", "[]", "\"x\"", "{\"packages\":{"}) {
            try {
                parse(json, 10);
                fail("expected IOException for " + json);
            } catch (IOException expected) {
                // ok
            }
        }
    }
}
