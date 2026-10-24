package com.xiddoc.androidautox.store;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import java.io.IOException;
import java.io.Reader;
import java.util.regex.Pattern;

/** Picks the APK to install from GitHub's {@code releases/latest} JSON. */
public final class GithubRelease {

    private GithubRelease() {}

    /** The chosen release asset. */
    public static final class Pick {
        public final String url;
        /** SHA-256 GitHub computed for the asset, or null if the response had none. */
        public final String sha256;
        public final long size;
        public final String tag;

        Pick(String url, String sha256, long size, String tag) {
            this.url = url;
            this.sha256 = sha256;
            this.size = size;
            this.tag = tag;
        }
    }

    /**
     * @param repo         {@code owner/name}; the download URL must live under it
     * @param assetPattern regex searched for in each asset's file name
     * @return the first matching {@code .apk} asset, or null if there is none (or the release
     *         is a draft or pre-release)
     */
    public static Pick pick(Reader in, String repo, String assetPattern) throws IOException {
        Pattern pattern = Pattern.compile(assetPattern);
        String prefix = "https://github.com/" + repo + "/releases/download/";
        JsonReader r = new JsonReader(in);
        String tag = "";
        boolean unfinished = false;
        Pick pick = null;
        try {
            r.beginObject();
            while (r.hasNext()) {
                String key = r.nextName();
                if (key.equals("tag_name")) {
                    String t = JsonRead.string(r);
                    tag = t == null ? "" : t;
                } else if (key.equals("draft") || key.equals("prerelease")) {
                    if (JsonRead.bool(r)) unfinished = true;
                } else if (key.equals("assets") && r.peek() == JsonToken.BEGIN_ARRAY) {
                    r.beginArray();
                    while (r.hasNext()) {
                        Pick candidate = readAsset(r, pattern, prefix);
                        if (pick == null) pick = candidate;
                    }
                    r.endArray();
                } else {
                    r.skipValue();
                }
            }
            r.endObject();
        } catch (IllegalStateException e) {
            throw new StoreFormatException("not a GitHub release");
        }
        if (unfinished || pick == null) return null;
        return new Pick(pick.url, pick.sha256, pick.size, StoreValidation.text(tag, 60));
    }

    /** The asset if it is a matching APK under the repo's download URL, else null. */
    private static Pick readAsset(JsonReader r, Pattern pattern, String prefix) throws IOException {
        if (r.peek() != JsonToken.BEGIN_OBJECT) {
            r.skipValue();
            return null;
        }
        String name = null, url = null, digest = null;
        long size = 0;
        r.beginObject();
        while (r.hasNext()) {
            String key = r.nextName();
            if (key.equals("name")) name = JsonRead.string(r);
            else if (key.equals("browser_download_url")) url = JsonRead.string(r);
            else if (key.equals("digest")) digest = JsonRead.string(r);
            else if (key.equals("size")) size = JsonRead.number(r, 0);
            else r.skipValue();
        }
        r.endObject();
        if (name == null || url == null) return null;
        if (!name.endsWith(".apk") || !pattern.matcher(name).find()) return null;
        if (!StoreValidation.isHttpsUrl(url) || !url.regionMatches(true, 0, prefix, 0, prefix.length())) {
            return null;
        }
        String sha = digest != null && digest.startsWith("sha256:")
                ? StoreValidation.sha256OrNull(digest.substring("sha256:".length()))
                : null;
        return new Pick(url, sha, size, "");
    }
}
