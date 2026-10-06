package com.swingcraft4j.code.layout;

/**
 * Display positions measured in monospace cells. A char of the basic Latin range takes one
 * cell and a tab runs to the next tab stop. A run of other text is laid out a cluster at a
 * time at the widths a {@link CellMeasure} gives, which need not be whole cells; the run as a
 * whole is rounded up, so the text after it is back on the grid.
 * <p>
 * With a measure that is not {@linkplain CellMeasure#monospaced() monospaced} there is no
 * grid: every cluster but a tab has the width the measure gives, and a cell is only the unit.
 */
public final class Cells {

    private static final char KHMER_COENG = '្';
    /** A run this little over a whole number of cells does not take another cell. */
    private static final double SLACK = 0.05;

    private Cells() {
    }

    /**
     * An estimate of the width of a char from its kind alone: two cells for East Asian wide
     * chars, none for combining marks, one otherwise. Not for tabs, whose width depends on
     * their column.
     */
    public static int width(char c) {
        if (c < 0x300) {
            return 1;
        }
        if (c >= 0x1100 && isWide(c)) {
            return 2;
        }
        if (Character.isHighSurrogate(c)) {
            return 2;
        }
        if (Character.isLowSurrogate(c)) {
            return 0;
        }
        int type = Character.getType(c);
        if (type == Character.NON_SPACING_MARK || type == Character.ENCLOSING_MARK || type == Character.FORMAT) {
            return 0;
        }
        return 1;
    }

    private static boolean isWide(char c) {
        return c <= 0x115F
                || (c >= 0x2E80 && c <= 0xA4CF && c != 0x303F)
                || (c >= 0xAC00 && c <= 0xD7A3)
                || (c >= 0xF900 && c <= 0xFAFF)
                || (c >= 0xFE30 && c <= 0xFE4F)
                || (c >= 0xFF00 && c <= 0xFF60)
                || (c >= 0xFFE0 && c <= 0xFFE6);
    }

    /**
     * Whether a char is drawn as one unit with the char before it: the second half of a
     * surrogate pair, a combining mark, a joiner, or a Khmer letter written below the previous
     * one after the sign that calls for it.
     */
    public static boolean isClusterContinuation(char previous, char c) {
        if (c < 0x300) {
            return false;
        }
        if (Character.isLowSurrogate(c)) {
            return Character.isHighSurrogate(previous);
        }
        int type = Character.getType(c);
        if (type == Character.NON_SPACING_MARK || type == Character.ENCLOSING_MARK
                || type == Character.COMBINING_SPACING_MARK) {
            return true;
        }
        if (c == '‌' || c == '‍') {
            return true;
        }
        return previous == KHMER_COENG && c >= 0x1780 && c <= 0x17B3;
    }

    /** The index just past the cluster starting at {@code index}. */
    public static int clusterEnd(char[] text, int index, int end) {
        int next = index + 1;
        while (next < end && isClusterContinuation(text[next - 1], text[next])) {
            next++;
        }
        return next;
    }

    /** The cells a finished run takes when it ends at the given position. */
    public static int roundUp(double position) {
        return (int) Math.ceil(position - SLACK);
    }

    /**
     * The position, in cells from the start of the line, just after {@code text[start, end)},
     * where {@code start} is the start of a line. It is a whole number except inside or right
     * at the end of a run of text outside the basic Latin range.
     */
    public static double position(char[] text, int start, int end, int tabSize, CellMeasure measure) {
        boolean grid = measure.monospaced();
        double at = 0;
        boolean inRun = false;
        int i = start;
        while (i < end) {
            char c = text[i];
            if (c == '\t' || (grid && c < 0x300)) {
                if (inRun) {
                    at = roundUp(at);
                    inRun = false;
                }
                at = c == '\t' ? tabStop(at, tabSize) : at + 1;
                i++;
            } else {
                int next = clusterEnd(text, i, end);
                at += measure.clusterAdvance(text, i, next);
                inRun = grid;
                i = next;
            }
        }
        return at;
    }

    /** The position a tab at the given position runs to: the next multiple of the tab size. */
    public static double tabStop(double position, int tabSize) {
        return (Math.floor(position / tabSize + 1e-6) + 1) * tabSize;
    }

    /** Whole cells occupied by {@code text[start, end)}, where {@code start} is the start of a line. */
    public static int count(char[] text, int start, int end, int tabSize, CellMeasure measure) {
        return roundUp(position(text, start, end, tabSize, measure));
    }

    /** As {@link #count(char[], int, int, int, CellMeasure)} with estimated cluster widths. */
    public static int count(char[] text, int start, int end, int tabSize) {
        return count(text, start, end, tabSize, CellMeasure.ESTIMATE);
    }

    /**
     * Index of the first char of the tab, char or cluster covering a position of a line
     * starting at {@code start}, or {@code end} when the position lies past the last char.
     */
    public static int indexAtCell(char[] text, int start, int end, double cell, int tabSize, CellMeasure measure) {
        return indexAt(text, start, end, cell, tabSize, measure, false);
    }

    /** As {@link #indexAtCell(char[], int, int, double, int, CellMeasure)} with estimated cluster widths. */
    public static int indexAtCell(char[] text, int start, int end, int cell, int tabSize) {
        return indexAtCell(text, start, end, cell, tabSize, CellMeasure.ESTIMATE);
    }

    /**
     * Index of the boundary between tabs, chars or clusters that is nearest to a position of
     * a line starting at {@code start}: where a caret goes for a click at that position.
     */
    public static int indexNear(char[] text, int start, int end, double position, int tabSize, CellMeasure measure) {
        return indexAt(text, start, end, position, tabSize, measure, true);
    }

    private static int indexAt(char[] text, int start, int end, double position, int tabSize, CellMeasure measure,
                               boolean nearest) {
        boolean grid = measure.monospaced();
        double at = 0;
        boolean inRun = false;
        int i = start;
        while (i < end) {
            char c = text[i];
            int next = i + 1;
            double after;
            if (c == '\t' || (grid && c < 0x300)) {
                if (inRun) {
                    at = roundUp(at);
                    inRun = false;
                    if (position < at) {
                        return i; // in the gap that rounds the run up to whole cells
                    }
                }
                after = c == '\t' ? tabStop(at, tabSize) : at + 1;
            } else {
                next = clusterEnd(text, i, end);
                after = at + measure.clusterAdvance(text, i, next);
                inRun = grid;
            }
            if (position < (nearest ? (at + after) / 2 : after)) {
                return i;
            }
            at = after;
            i = next;
        }
        return end;
    }
}
