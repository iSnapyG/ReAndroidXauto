package com.xiddoc.androidautox;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Plain-JUnit coverage for {@link TweakCompatibility}. */
public class TweakCompatibilityTest {

    private static FlagSpec aa(String name) {
        return FlagSpec.bool(FlagSpec.PKG_GEARHEAD, name, true);
    }

    private static FlagSpec car(String name) {
        return FlagSpec.bool(FlagSpec.PKG_CAR, name, true);
    }

    private static Set<String> present(String... names) {
        return new HashSet<String>(Arrays.asList(names));
    }

    // ------------------------------------------------------------ gearheadNames

    @Test
    public void gearheadNames_collectsOnlyCheckableNamesWithoutDuplicates() {
        List<FlagSpec> one = Arrays.asList(aa("A__one"), car("Car__skipped"), aa("A__two"));
        List<FlagSpec> two = Arrays.asList(aa("A__one"), FlagSpec.remove(FlagSpec.PKG_GEARHEAD, "A__removed"));
        Collection<List<FlagSpec>> lists = Arrays.asList(one, null, two);
        assertEquals(Arrays.asList("A__one", "A__two"),
                new ArrayList<String>(TweakCompatibility.gearheadNames(lists)));
    }

    @Test
    public void gearheadNames_noLists_isEmpty() {
        assertTrue(TweakCompatibility.gearheadNames(Collections.<List<FlagSpec>>emptyList()).isEmpty());
    }

    // ------------------------------------------------------------ evaluate

    @Test
    public void evaluate_allFound_isSupported() {
        TweakCompatibility.Result r = TweakCompatibility.evaluate("k",
                Arrays.asList(aa("A__one"), aa("A__two")), present("A__one", "A__two", "Other__x"));
        assertEquals(TweakCompatibility.Status.SUPPORTED, r.status);
        assertEquals("k", r.key);
        assertEquals(2, r.found);
        assertEquals(2, r.total);
        assertTrue(r.missing.isEmpty());
    }

    @Test
    public void evaluate_someFound_isPartialAndListsTheMissingOnesInOrder() {
        TweakCompatibility.Result r = TweakCompatibility.evaluate("k",
                Arrays.asList(aa("A__gone1"), aa("A__here"), aa("A__gone2")), present("A__here"));
        assertEquals(TweakCompatibility.Status.PARTIAL, r.status);
        assertEquals(1, r.found);
        assertEquals(3, r.total);
        assertEquals(Arrays.asList("A__gone1", "A__gone2"), r.missing);
    }

    @Test
    public void evaluate_noneFound_isMissing() {
        TweakCompatibility.Result r = TweakCompatibility.evaluate("k",
                Arrays.asList(aa("A__gone1"), aa("A__gone2")), present("Unrelated__x"));
        assertEquals(TweakCompatibility.Status.MISSING, r.status);
        assertEquals(0, r.found);
        assertEquals(2, r.total);
    }

    @Test
    public void evaluate_nothingCheckable_isUnknown() {
        TweakCompatibility.Result onlyCar = TweakCompatibility.evaluate("k",
                Arrays.asList(car("Car__one")), present("Car__one"));
        assertEquals(TweakCompatibility.Status.UNKNOWN, onlyCar.status);
        assertEquals(0, onlyCar.total);

        TweakCompatibility.Result onlyRemove = TweakCompatibility.evaluate("k",
                Arrays.asList(FlagSpec.remove(FlagSpec.PKG_GEARHEAD, "A__x")), present("A__x"));
        assertEquals(TweakCompatibility.Status.UNKNOWN, onlyRemove.status);

        assertEquals(TweakCompatibility.Status.UNKNOWN,
                TweakCompatibility.evaluate("k", Collections.<FlagSpec>emptyList(), present()).status);
        assertEquals(TweakCompatibility.Status.UNKNOWN,
                TweakCompatibility.evaluate("k", null, present()).status);
    }

    @Test
    public void evaluate_ignoresCarFlagsAndCountsAFlagListedTwiceOnce() {
        TweakCompatibility.Result r = TweakCompatibility.evaluate("k",
                Arrays.asList(aa("A__one"), aa("A__one"), car("Car__ignored")), present("A__one"));
        assertEquals(TweakCompatibility.Status.SUPPORTED, r.status);
        assertEquals(1, r.total);
    }

    // ------------------------------------------------------------ sortForDisplay

    @Test
    public void sortForDisplay_putsWorstFirstThenAlphabetical() {
        List<TweakCompatibility.Result> results = new ArrayList<TweakCompatibility.Result>();
        results.add(TweakCompatibility.evaluate("zz_ok", Arrays.asList(aa("A__a")), present("A__a")));
        results.add(TweakCompatibility.evaluate("aa_ok", Arrays.asList(aa("A__a")), present("A__a")));
        results.add(TweakCompatibility.evaluate("unk", Arrays.asList(car("C__a")), present()));
        results.add(TweakCompatibility.evaluate("partial",
                Arrays.asList(aa("A__a"), aa("A__b")), present("A__a")));
        results.add(TweakCompatibility.evaluate("gone_b", Arrays.asList(aa("A__b")), present()));
        results.add(TweakCompatibility.evaluate("gone_a", Arrays.asList(aa("A__a")), present()));

        TweakCompatibility.sortForDisplay(results);

        List<String> keys = new ArrayList<String>();
        for (TweakCompatibility.Result r : results) keys.add(r.key);
        assertEquals(Arrays.asList("gone_a", "gone_b", "partial", "unk", "aa_ok", "zz_ok"), keys);
    }
}
