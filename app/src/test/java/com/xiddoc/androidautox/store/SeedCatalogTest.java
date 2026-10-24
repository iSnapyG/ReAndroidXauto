package com.xiddoc.androidautox.store;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Set;

/** The catalog that ships inside the app must be valid and safe. */
public class SeedCatalogTest {

    private static final File SEED = new File("src/main/assets/store/seed-catalog.json");

    @Test
    public void shippedSeedParsesCompletely() throws IOException {
        StoreCatalogParser.Result r = StoreCatalogParser.parse(
                new InputStreamReader(Files.newInputStream(SEED.toPath()), StandardCharsets.UTF_8),
                StoreApp.Origin.CATALOG, CatalogLoader.MAX_SEED_APPS);
        assertEquals("every entry must be valid", 0, r.skipped);
        assertFalse(r.apps.isEmpty());

        Set<String> seen = new HashSet<String>();
        boolean car = false;
        boolean autox = false;
        for (StoreApp app : r.apps) {
            assertTrue(app.packageName, seen.add(app.packageName));
            assertFalse(app.packageName, PackagePolicy.isProtected(app.packageName));
            assertFalse(app.packageName, app.signers.isEmpty());
            assertEquals(app.packageName, ApkSource.Type.FDROID, app.source.type);
            car |= app.kind == StoreApp.Kind.CAR;
            autox |= app.kind == StoreApp.Kind.AUTOX;
        }
        assertTrue(car);
        assertTrue(autox);
    }
}
