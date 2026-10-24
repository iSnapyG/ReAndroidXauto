package com.xiddoc.androidautox.store;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.stream.JsonReader;

import org.junit.Test;

import java.io.IOException;
import java.io.StringReader;
import java.util.Arrays;

public class JsonReadTest {

    private static JsonReader reader(String json) {
        return new JsonReader(new StringReader(json));
    }

    @Test
    public void stringAcceptsStringsAndNumbers() throws IOException {
        assertEquals("x", JsonRead.string(reader("\"x\"")));
        assertEquals("12", JsonRead.string(reader("12")));
        assertNull(JsonRead.string(reader("true")));
        assertNull(JsonRead.string(reader("{\"a\":1}")));
        assertNull(JsonRead.string(reader("null")));
    }

    @Test
    public void numberFallsBack() throws IOException {
        assertEquals(12, JsonRead.number(reader("12"), -1));
        assertEquals(12, JsonRead.number(reader("\" 12 \""), -1));
        assertEquals(-1, JsonRead.number(reader("\"abc\""), -1));
        assertEquals(7, JsonRead.number(reader("[1]"), 7));
    }

    @Test
    public void booleans() throws IOException {
        assertTrue(JsonRead.bool(reader("true")));
        assertFalse(JsonRead.bool(reader("false")));
        assertFalse(JsonRead.bool(reader("\"true\"")));
    }

    @Test
    public void stringLists() throws IOException {
        assertEquals(Arrays.asList("a", "1"), JsonRead.strings(reader("[\"a\", 1, {\"x\":2}, null]"), 10));
        assertEquals(Arrays.asList("a", "b"), JsonRead.strings(reader("[\"a\",\"b\",\"c\"]"), 2));
        assertTrue(JsonRead.strings(reader("\"not a list\""), 10).isEmpty());
    }

    @Test
    public void localisedText() throws IOException {
        assertEquals("plain", JsonRead.localized(reader("\"plain\"")));
        assertNull(JsonRead.localized(reader("[1]")));
        assertEquals("us", JsonRead.localized(reader("{\"de\":\"d\",\"en-GB\":\"gb\",\"en-US\":\"us\"}")));
        assertEquals("gb1", JsonRead.localized(reader("{\"de\":\"d\",\"en-GB\":\"gb1\",\"en-AU\":\"gb2\"}")));
        assertEquals("d", JsonRead.localized(reader("{\"de\":\"d\",\"fr\":\"f\"}")));
        assertNull(JsonRead.localized(reader("{\"de\":{},\"en\":null}")));
    }
}
