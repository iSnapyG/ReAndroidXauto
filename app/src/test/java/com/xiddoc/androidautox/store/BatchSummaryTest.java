package com.xiddoc.androidautox.store;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class BatchSummaryTest {

    @Test
    public void countsEachKindOfOutcome() {
        StoreApp a = StoreFixtures.named("A", "org.example.a", "s", StoreApp.Kind.CAR, 1);
        StoreApp b = StoreFixtures.named("B", "org.example.b", "s", StoreApp.Kind.CAR, 1);
        StoreApp c = StoreFixtures.named("C", "org.example.c", "s", StoreApp.Kind.CAR, 1);
        StoreApp d = StoreFixtures.named("D", "org.example.d", "s", StoreApp.Kind.CAR, 1);

        Map<String, StoreInstaller.Result> results = new HashMap<String, StoreInstaller.Result>();
        results.put("org.example.a", new StoreInstaller.Result(StoreInstaller.Outcome.OK, ""));
        results.put("org.example.b", new StoreInstaller.Result(StoreInstaller.Outcome.SIGNER_MISMATCH, ""));
        results.put("org.example.c", new StoreInstaller.Result(StoreInstaller.Outcome.CANCELLED, ""));

        BatchSummary s = BatchSummary.of(Arrays.asList(a, b, c, d), results);

        assertEquals(1, s.ok);
        assertEquals(1, s.failed);
        assertEquals(2, s.notRun);
    }

    @Test
    public void emptyBatchIsAllZero() {
        BatchSummary s = BatchSummary.of(Collections.<StoreApp>emptyList(),
                Collections.<String, StoreInstaller.Result>emptyMap());
        assertEquals(0, s.ok + s.failed + s.notRun);
    }
}
