package com.swingcraft4j.code.diff;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Finds the lines in which two versions of a text differ, as {@code git diff} does: the fewest
 * lines deleted and inserted that turn the original into the revised version.
 * <p>
 * It is the algorithm of Myers, in the form that needs memory in proportion to the length of
 * the texts only, so also two large files that have little in common are compared without
 * running out of memory. Lines are compared whole, with {@link String#equals}.
 * <pre>{@code
 * List<Change> changes = Diff.compare(originalLines, revisedLines);
 * }</pre>
 * {@link UnifiedDiff} writes the outcome as the text that {@code git diff} prints.
 */
public final class Diff {

    private Diff() {
    }

    /** The changes that turn the original lines into the revised lines, in order; empty where they are equal. */
    public static List<Change> compare(List<String> original, List<String> revised) {
        // Each distinct line becomes a number, which is quicker to compare than its text.
        Map<String, Integer> ids = new HashMap<>();
        int[] a = new int[original.size()];
        for (int i = 0; i < a.length; i++) {
            a[i] = ids.computeIfAbsent(original.get(i), line -> ids.size());
        }
        int[] b = new int[revised.size()];
        for (int i = 0; i < b.length; i++) {
            b[i] = ids.computeIfAbsent(revised.get(i), line -> ids.size());
        }
        boolean[] deleted = new boolean[a.length];
        boolean[] inserted = new boolean[b.length];
        // A line the other text has nowhere is changed whatever else is, and leaving it out of
        // the search keeps two texts that have little in common from taking long.
        boolean[] inA = new boolean[ids.size()];
        boolean[] inB = new boolean[ids.size()];
        for (int id : a) {
            inA[id] = true;
        }
        for (int id : b) {
            inB[id] = true;
        }
        int[] keptA = keep(a, inB, deleted);
        int[] keptB = keep(b, inA, inserted);
        boolean[] keptDeleted = new boolean[keptA.length];
        boolean[] keptInserted = new boolean[keptB.length];
        int[] lineA = lines(keptA, a);
        int[] lineB = lines(keptB, b);
        int max = keptA.length + keptB.length + 2;
        new Search(lineA, lineB, keptDeleted, keptInserted, max).diff(0, lineA.length, 0, lineB.length);
        for (int i = 0; i < keptA.length; i++) {
            deleted[keptA[i]] = keptDeleted[i];
        }
        for (int j = 0; j < keptB.length; j++) {
            inserted[keptB[j]] = keptInserted[j];
        }
        return changes(deleted, inserted);
    }

    /** The indexes of the lines the other text has too; the others are marked as changed. */
    private static int[] keep(int[] lines, boolean[] inOther, boolean[] changed) {
        int[] kept = new int[lines.length];
        int count = 0;
        for (int i = 0; i < lines.length; i++) {
            if (inOther[lines[i]]) {
                kept[count++] = i;
            } else {
                changed[i] = true;
            }
        }
        return Arrays.copyOf(kept, count);
    }

    private static int[] lines(int[] indexes, int[] lines) {
        int[] result = new int[indexes.length];
        for (int i = 0; i < indexes.length; i++) {
            result[i] = lines[indexes[i]];
        }
        return result;
    }

    /** Joins the lines marked as deleted and inserted into runs, each run of either side with the run next to it. */
    private static List<Change> changes(boolean[] deleted, boolean[] inserted) {
        List<Change> changes = new ArrayList<>();
        int i = 0;
        int j = 0;
        while (i < deleted.length || j < inserted.length) {
            if ((i < deleted.length && deleted[i]) || (j < inserted.length && inserted[j])) {
                int startI = i;
                int startJ = j;
                while (i < deleted.length && deleted[i]) {
                    i++;
                }
                while (j < inserted.length && inserted[j]) {
                    j++;
                }
                changes.add(new Change(startI, i - startI, startJ, j - startJ));
            } else {
                // a line both have
                i++;
                j++;
            }
        }
        return changes;
    }

    /** The search for the shortest edit, which divides the texts at the middle of the edit until what is left is plain. */
    private static final class Search {

        /** A diagonal no path can reach without leaving the texts; far enough below 0 that adding 1 keeps it there. */
        private static final int DEAD = Integer.MIN_VALUE / 4;

        private final int[] a;
        private final int[] b;
        private final boolean[] deleted;
        private final boolean[] inserted;
        /** The furthest x reached on each diagonal, forward from the start and backward from the end; offset by {@link #offset}. */
        private final int[] forward;
        private final int[] backward;
        private final int offset;
        /** The snake in the middle: where it starts and ends, set by {@link #middleSnake}. */
        private int snakeStartX;
        private int snakeStartY;
        private int snakeEndX;
        private int snakeEndY;

        Search(int[] a, int[] b, boolean[] deleted, boolean[] inserted, int max) {
            this.a = a;
            this.b = b;
            this.deleted = deleted;
            this.inserted = inserted;
            forward = new int[2 * max + 1];
            backward = new int[2 * max + 1];
            offset = max;
        }

        void diff(int aStart, int aEnd, int bStart, int bEnd) {
            while (aStart < aEnd && bStart < bEnd && a[aStart] == b[bStart]) {
                aStart++;
                bStart++;
            }
            while (aStart < aEnd && bStart < bEnd && a[aEnd - 1] == b[bEnd - 1]) {
                aEnd--;
                bEnd--;
            }
            if (aStart == aEnd) {
                for (int j = bStart; j < bEnd; j++) {
                    inserted[j] = true;
                }
                return;
            }
            if (bStart == bEnd) {
                for (int i = aStart; i < aEnd; i++) {
                    deleted[i] = true;
                }
                return;
            }
            middleSnake(aStart, aEnd, bStart, bEnd);
            // the snake is of lines both have; what is before it and after it is searched apart
            int startX = snakeStartX;
            int startY = snakeStartY;
            int endX = snakeEndX;
            int endY = snakeEndY;
            diff(aStart, aStart + startX, bStart, bStart + startY);
            diff(aStart + endX, aEnd, bStart + endY, bEnd);
        }

        /**
         * Searches from both ends at once until the paths meet, and keeps the snake where they
         * do, in coordinates from the start of the part searched. Neither part is empty, and
         * they neither start nor end with a line they share.
         */
        private void middleSnake(int aStart, int aEnd, int bStart, int bEnd) {
            int n = aEnd - aStart;
            int m = bEnd - bStart;
            int delta = n - m;
            boolean odd = (delta & 1) != 0;
            int limit = (n + m + 1) / 2;
            for (int d = 0; d <= limit; d++) {
                for (int k = -d; k <= d; k += 2) {
                    int x = furthest(forward, k, d, n, m);
                    if (x == DEAD) {
                        forward[offset + k] = DEAD;
                        continue;
                    }
                    int y = x - k;
                    int startX = x;
                    int startY = y;
                    while (x < n && y < m && a[aStart + x] == b[bStart + y]) {
                        x++;
                        y++;
                    }
                    forward[offset + k] = x;
                    int c = delta - k;
                    if (odd && c >= -(d - 1) && c <= d - 1 && x + backward[offset + c] >= n) {
                        setSnake(startX, startY, x, y);
                        return;
                    }
                }
                for (int c = -d; c <= d; c += 2) {
                    // x and y are counted back from the end
                    int x = furthest(backward, c, d, n, m);
                    if (x == DEAD) {
                        backward[offset + c] = DEAD;
                        continue;
                    }
                    int y = x - c;
                    int startX = x;
                    int startY = y;
                    while (x < n && y < m && a[aEnd - 1 - x] == b[bEnd - 1 - y]) {
                        x++;
                        y++;
                    }
                    backward[offset + c] = x;
                    int k = delta - c;
                    if (!odd && k >= -d && k <= d && x + forward[offset + k] >= n) {
                        setSnake(n - x, m - y, n - startX, m - startY);
                        return;
                    }
                }
            }
            throw new IllegalStateException("the searches did not meet");
        }

        /**
         * Where a path of {@code d} edits on diagonal {@code k} starts its run of shared lines:
         * one line further than the path on the diagonal to the left, or as far as the path on
         * the diagonal to the right, whichever goes further without leaving the texts; or
         * {@link #DEAD} where neither can.
         */
        private int furthest(int[] reached, int k, int d, int n, int m) {
            if (d == 0) {
                return 0;
            }
            int right = k == -d ? DEAD : reached[offset + k - 1] + 1;
            if (right > n) {
                right = DEAD;
            }
            int down = k == d ? DEAD : reached[offset + k + 1];
            if (down - k > m) {
                down = DEAD;
            }
            int x = Math.max(right, down);
            return x < 0 ? DEAD : x;
        }

        private void setSnake(int startX, int startY, int endX, int endY) {
            snakeStartX = startX;
            snakeStartY = startY;
            snakeEndX = endX;
            snakeEndY = endY;
        }
    }
}
