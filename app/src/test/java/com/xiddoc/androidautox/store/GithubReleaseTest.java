package com.xiddoc.androidautox.store;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.io.IOException;
import java.io.StringReader;

public class GithubReleaseTest {

    private static final String BASE = "https://github.com/owner/repo/releases/download/v1/";
    private static final String SHA = StoreFixtures.repeat('7');

    private static String asset(String name, String url, String extra) {
        return "{\"name\":\"" + name + "\",\"browser_download_url\":\"" + url + "\",\"size\":55" + extra + "}";
    }

    private static GithubRelease.Pick pick(String json, String pattern) throws IOException {
        return GithubRelease.pick(new StringReader(json), "owner/repo", pattern);
    }

    private static String release(String flags, String... assets) {
        StringBuilder sb = new StringBuilder("{\"tag_name\":\"v1.0\",\"name\":\"x\"," + flags + "\"assets\":[");
        for (int i = 0; i < assets.length; i++) sb.append(i == 0 ? "" : ",").append(assets[i]);
        return sb.append("]}").toString();
    }

    @Test
    public void picksTheFirstMatchingApk() throws IOException {
        String json = release("\"draft\":false,\"prerelease\":false,",
                asset("notes.txt", BASE + "notes.txt", ""),
                asset("app-x86.apk", BASE + "app-x86.apk", ""),
                asset("app-arm64.apk", BASE + "app-arm64.apk", ",\"digest\":\"sha256:" + SHA + "\""),
                asset("app-arm64-2.apk", BASE + "app-arm64-2.apk", ""));
        GithubRelease.Pick p = pick(json, "arm64");
        assertEquals(BASE + "app-arm64.apk", p.url);
        assertEquals(SHA, p.sha256);
        assertEquals(55, p.size);
        assertEquals("v1.0", p.tag);
    }

    @Test
    public void rejectsUnsuitableAssets() throws IOException {
        assertNull(pick(release("", asset("a.apk", "http://github.com/owner/repo/releases/download/v1/a.apk", "")), "a"));
        assertNull(pick(release("", asset("a.apk", "https://evil.example/owner/repo/releases/download/v1/a.apk", "")), "a"));
        assertNull(pick(release("", asset("a.apk", "https://github.com/other/repo/releases/download/v1/a.apk", "")), "a"));
        assertNull(pick(release("", "{\"name\":\"a.apk\"}"), "a"));
        assertNull(pick(release("", "{\"browser_download_url\":\"" + BASE + "a.apk\"}"), "a"));
        assertNull(pick(release("", "7"), "a"));
        assertNull(pick(release(""), "a"));
    }

    @Test
    public void unknownAssetFieldsAreIgnored() throws IOException {
        GithubRelease.Pick p = pick(release("", asset("a.apk", BASE + "a.apk",
                ",\"id\":9,\"content_type\":\"application/vnd.android.package-archive\",\"uploader\":{\"x\":1}")), "a");
        assertEquals(BASE + "a.apk", p.url);
        assertEquals(55, p.size);
    }

    @Test
    public void digestHandling() throws IOException {
        assertNull(pick(release("", asset("a.apk", BASE + "a.apk", ",\"digest\":\"sha1:" + SHA + "\"")), "a").sha256);
        assertNull(pick(release("", asset("a.apk", BASE + "a.apk", ",\"digest\":\"sha256:zzz\"")), "a").sha256);
        assertNull(pick(release("", asset("a.apk", BASE + "a.apk", "")), "a").sha256);
    }

    @Test
    public void ignoresDraftsAndPrereleases() throws IOException {
        String a = asset("a.apk", BASE + "a.apk", "");
        assertNull(pick(release("\"draft\":true,", a), "a"));
        assertNull(pick(release("\"prerelease\":true,", a), "a"));
    }

    @Test
    public void missingTagAndOtherKeys() throws IOException {
        String json = "{\"tag_name\":null,\"body\":{\"x\":1},\"assets\":3,\"assets\":["
                + asset("a.apk", BASE + "a.apk", "") + "]}";
        GithubRelease.Pick p = pick(json, "a");
        assertEquals("", p.tag);
    }

    @Test
    public void rejectsNonReleases() {
        for (String json : new String[]{"[]", "\"x\"", "{\"assets\":["}) {
            try {
                pick(json, "a");
                fail("expected IOException for " + json);
            } catch (IOException expected) {
                // ok
            }
        }
    }
}
