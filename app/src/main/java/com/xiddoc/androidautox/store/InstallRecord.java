package com.xiddoc.androidautox.store;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/** An app the store installed: enough to list it, update it and later remove it. */
public final class InstallRecord {

    public final String packageName;
    public final String name;
    public final long versionCode;
    public final String versionName;
    public final long installedAtMillis;

    public InstallRecord(String packageName, String name, long versionCode, String versionName,
                         long installedAtMillis) {
        if (!StoreValidation.isPackageName(packageName)) {
            throw new IllegalArgumentException("bad package name");
        }
        this.packageName = packageName;
        String cleanName = StoreValidation.text(name, 60);
        this.name = cleanName.isEmpty() ? packageName : cleanName;
        this.versionCode = versionCode;
        this.versionName = StoreValidation.text(versionName, 40);
        this.installedAtMillis = installedAtMillis;
    }

    /** Serialises records for storage in a single preference string. */
    public static String encode(Collection<InstallRecord> records) {
        JsonArray array = new JsonArray();
        for (InstallRecord r : records) {
            JsonObject o = new JsonObject();
            o.addProperty("package", r.packageName);
            o.addProperty("name", r.name);
            o.addProperty("versionCode", r.versionCode);
            o.addProperty("versionName", r.versionName);
            o.addProperty("at", r.installedAtMillis);
            array.add(o);
        }
        return array.toString();
    }

    /** Reads what {@link #encode} wrote; damaged input gives what could be read (maybe none). */
    public static List<InstallRecord> decode(String json) {
        List<InstallRecord> out = new ArrayList<InstallRecord>();
        if (json == null) return out;
        JsonReader r = new JsonReader(new StringReader(json));
        try {
            r.beginArray();
            while (r.hasNext()) {
                InstallRecord rec = readOne(r);
                if (rec != null) out.add(rec);
            }
            r.endArray();
        } catch (IOException e) {
            // keep what was read before the damage
        } catch (IllegalStateException e) {
            // not an array
        }
        return Collections.unmodifiableList(out);
    }

    private static InstallRecord readOne(JsonReader r) throws IOException {
        if (r.peek() != JsonToken.BEGIN_OBJECT) {
            r.skipValue();
            return null;
        }
        String pkg = null, name = null, versionName = null;
        long code = 0, at = 0;
        r.beginObject();
        while (r.hasNext()) {
            String key = r.nextName();
            if (key.equals("package")) pkg = JsonRead.string(r);
            else if (key.equals("name")) name = JsonRead.string(r);
            else if (key.equals("versionName")) versionName = JsonRead.string(r);
            else if (key.equals("versionCode")) code = JsonRead.number(r, 0);
            else if (key.equals("at")) at = JsonRead.number(r, 0);
            else r.skipValue();
        }
        r.endObject();
        if (!StoreValidation.isPackageName(pkg)) return null;
        return new InstallRecord(pkg, name, code, versionName, at);
    }
}
