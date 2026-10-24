package com.xiddoc.androidautox.store;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Forgiving scalar readers on top of Gson's streaming {@link JsonReader}: a value of an
 * unexpected type is skipped and reported as absent instead of throwing, so one odd field
 * cannot take down a whole 60 MB index.
 */
final class JsonRead {

    private JsonRead() {}

    /** A string or number as text; anything else is skipped and gives null. */
    static String string(JsonReader r) throws IOException {
        JsonToken t = r.peek();
        if (t == JsonToken.STRING || t == JsonToken.NUMBER) return r.nextString();
        r.skipValue();
        return null;
    }

    static long number(JsonReader r, long fallback) throws IOException {
        String s = string(r);
        if (s == null) return fallback;
        try {
            return Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    static boolean bool(JsonReader r) throws IOException {
        if (r.peek() == JsonToken.BOOLEAN) return r.nextBoolean();
        r.skipValue();
        return false;
    }

    /** An array of strings (at most {@code max} kept); anything else gives an empty list. */
    static List<String> strings(JsonReader r, int max) throws IOException {
        List<String> out = new ArrayList<String>();
        if (r.peek() != JsonToken.BEGIN_ARRAY) {
            r.skipValue();
            return out;
        }
        r.beginArray();
        while (r.hasNext()) {
            String s = string(r);
            if (s != null && out.size() < max) out.add(s);
        }
        r.endArray();
        return out;
    }

    /**
     * Localised text: either a plain string or a {@code {"en-US": "..."}} map. Prefers
     * {@code en-US}, then any other English variant, then whatever comes first.
     */
    static String localized(JsonReader r) throws IOException {
        JsonToken t = r.peek();
        if (t == JsonToken.STRING) return r.nextString();
        if (t != JsonToken.BEGIN_OBJECT) {
            r.skipValue();
            return null;
        }
        String exact = null;
        String english = null;
        String first = null;
        r.beginObject();
        while (r.hasNext()) {
            String locale = r.nextName();
            String value = string(r);
            if (value == null) continue;
            if (first == null) first = value;
            if (locale.equals("en-US")) {
                exact = value;
            } else if (locale.startsWith("en") && english == null) {
                english = value;
            }
        }
        r.endObject();
        if (exact != null) return exact;
        return english != null ? english : first;
    }
}
