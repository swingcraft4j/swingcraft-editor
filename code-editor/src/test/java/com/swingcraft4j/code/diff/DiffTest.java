package com.swingcraft4j.code.diff;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiffTest {

    @Test
    void equalTextsHaveNoChanges() {
        assertEquals(List.of(), Diff.compare(List.of("a", "b"), List.of("a", "b")));
        assertEquals(List.of(), Diff.compare(List.of(), List.of()));
    }

    @Test
    void findsAnInsertADeleteAndAReplace() {
        assertEquals(List.of(new Change(1, 0, 1, 1)), Diff.compare(List.of("a", "c"), List.of("a", "b", "c")));
        assertEquals(List.of(new Change(1, 1, 1, 0)), Diff.compare(List.of("a", "b", "c"), List.of("a", "c")));
        assertEquals(List.of(new Change(1, 1, 1, 1)), Diff.compare(List.of("a", "b", "c"), List.of("a", "x", "c")));
        assertEquals(List.of(new Change(0, 0, 0, 2)), Diff.compare(List.of(), List.of("a", "b")));
        assertEquals(List.of(new Change(0, 2, 0, 0)), Diff.compare(List.of("a", "b"), List.of()));
    }

    @Test
    void keepsTheLinesBothHaveBetweenChanges() {
        assertEquals(List.of(new Change(0, 1, 0, 1), new Change(3, 0, 3, 1)),
                Diff.compare(List.of("a", "b", "c"), List.of("x", "b", "c", "d")));
    }

    @Test
    void makesTheFewestEditsAndTheirChangesTurnOneTextIntoTheOther() {
        Random random = new Random(42);
        for (int round = 0; round < 2000; round++) {
            List<String> a = randomLines(random);
            List<String> b = randomLines(random);
            List<Change> changes = Diff.compare(a, b);
            assertEquals(b, apply(a, b, changes), a + " -> " + b);
            int edits = changes.stream().mapToInt(change -> change.originalCount() + change.revisedCount()).sum();
            assertEquals(a.size() + b.size() - 2 * longestCommon(a, b), edits, a + " -> " + b);
            for (int i = 1; i < changes.size(); i++) {
                assertTrue(changes.get(i).originalStart() > changes.get(i - 1).originalEnd(), "changes touch: " + changes);
            }
        }
    }

    @Test
    void comparesLargeTextsThatHaveLittleInCommon() {
        List<String> a = new ArrayList<>();
        List<String> b = new ArrayList<>();
        for (int i = 0; i < 20_000; i++) {
            a.add("a" + i);
            b.add(i % 100 == 0 ? "a" + i : "b" + i);
        }
        List<Change> changes = Diff.compare(a, b);
        assertEquals(b, apply(a, b, changes));
    }

    @Test
    void comparesTextsThatHaveTheSameLinesInAnotherOrder() {
        List<String> a = new ArrayList<>();
        for (int i = 0; i < 3000; i++) {
            a.add("line " + i);
        }
        List<String> b = new ArrayList<>(a);
        Collections.reverse(b);
        List<Change> changes = Diff.compare(a, b);
        assertEquals(b, apply(a, b, changes));
        int edits = changes.stream().mapToInt(change -> change.originalCount() + change.revisedCount()).sum();
        assertEquals(2 * 3000 - 2, edits);
    }

    /** Few distinct lines, so that the texts share many of them in many ways. */
    private static List<String> randomLines(Random random) {
        List<String> lines = new ArrayList<>();
        int size = random.nextInt(12);
        for (int i = 0; i < size; i++) {
            lines.add(String.valueOf((char) ('a' + random.nextInt(4))));
        }
        return lines;
    }

    private static List<String> apply(List<String> a, List<String> b, List<Change> changes) {
        List<String> result = new ArrayList<>();
        int i = 0;
        for (Change change : changes) {
            result.addAll(a.subList(i, change.originalStart()));
            result.addAll(b.subList(change.revisedStart(), change.revisedEnd()));
            i = change.originalEnd();
        }
        result.addAll(a.subList(i, a.size()));
        return result;
    }

    private static int longestCommon(List<String> a, List<String> b) {
        int[][] length = new int[a.size() + 1][b.size() + 1];
        for (int i = a.size() - 1; i >= 0; i--) {
            for (int j = b.size() - 1; j >= 0; j--) {
                length[i][j] = a.get(i).equals(b.get(j)) ? length[i + 1][j + 1] + 1 : Math.max(length[i + 1][j], length[i][j + 1]);
            }
        }
        return length[0][0];
    }
}
