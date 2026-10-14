package com.swingcraft4j.code.diff;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InlineDiffTest {

    @Test
    void byCharFindsTheCharsPutInAndTakenAway() {
        assertEquals(List.of(new Change(3, 0, 3, 1)), InlineDiff.compare("abc", "abcd", Granularity.CHAR));
        assertEquals(List.of(new Change(1, 1, 1, 0)), InlineDiff.compare("abc", "ac", Granularity.CHAR));
        assertEquals(List.of(), InlineDiff.compare("same", "same", Granularity.CHAR));
    }

    @Test
    void byWordChangesAWordWhole() {
        assertEquals(List.of(new Change(4, 5, 4, 5)), InlineDiff.compare("int count;", "int total;", Granularity.WORD));
        assertEquals(List.of("int", " ", "count", ";", "  ", "a_1", "."),
                InlineDiff.units("int count;  a_1.", Granularity.WORD));
        // by char, the letters both words have are kept
        List<Change> chars = InlineDiff.compare("int count;", "int total;", Granularity.CHAR);
        assertEquals("int total;", apply("int count;", "int total;", chars));
        assertEquals(true, chars.stream().mapToInt(Change::originalCount).sum() < 5);
    }

    private static String apply(String a, String b, List<Change> changes) {
        StringBuilder result = new StringBuilder();
        int i = 0;
        for (Change change : changes) {
            result.append(a, i, change.originalStart()).append(b, change.revisedStart(), change.revisedEnd());
            i = change.originalEnd();
        }
        return result.append(a.substring(i)).toString();
    }

    @Test
    void byLineChangesTheLineWhole() {
        assertEquals(List.of(new Change(0, 3, 0, 4)), InlineDiff.compare("abc", "abcd", Granularity.LINE));
    }

    @Test
    void comparesLongLinesWhole() {
        String a = "x".repeat(InlineDiff.MAX_UNITS);
        String b = a + "y";
        assertEquals(List.of(new Change(0, a.length(), 0, b.length())), InlineDiff.compare(a, b, Granularity.CHAR));
    }
}
