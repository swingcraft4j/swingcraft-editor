package com.swingcraft4j.code.folding;

import com.swingcraft4j.code.text.TextModel;

import java.util.Arrays;

/**
 * Finds the indent guides of the lines of a document: the lines that run down from a line to
 * where the lines indented under it end, as from an opening brace to its closing one. A line
 * has one guide for each line above that it is indented under, at the column where that line
 * begins. A blank line has those of the lines around it, so that a guide is not broken by it.
 * <p>
 * Like the folds, the guides go by indentation alone. The lines are asked about from the top
 * down: {@link #startAt} once, then {@link #next} for each line in turn.
 */
public final class IndentGuides {

    /** How far up the lines a line is indented under are looked for; one further away gets no guide. */
    private static final int MAX_LINES_UP = 5_000;
    /** A run of blank lines longer than this ends the search for the line after them. */
    private static final int MAX_BLANK_LINES = 200;

    private final TextModel model;
    private final int tabSize;
    /** Where the last line that was not blank begins, and before it the lines it is indented under, outermost first. */
    private int[] columns = new int[16];
    /** The line each of them is: the line a guide starts under. */
    private int[] lines = new int[16];
    private int size;

    public IndentGuides(TextModel model, int tabSize) {
        this.model = model;
        this.tabSize = tabSize;
    }

    /** Makes the line the one that {@link #next} is asked about first. */
    public void startAt(int line) {
        size = 0;
        int limit = Integer.MAX_VALUE;
        for (int previous = line - 1; previous >= Math.max(0, line - MAX_LINES_UP) && limit > 0; previous--) {
            int indent = IndentFolding.indent(model, previous, tabSize);
            if (indent >= 0 && indent < limit) {
                push(indent, previous);
                limit = indent;
            }
        }
        // they were found innermost first
        for (int i = 0, j = size - 1; i < j; i++, j--) {
            int column = columns[i];
            columns[i] = columns[j];
            columns[j] = column;
            int owner = lines[i];
            lines[i] = lines[j];
            lines[j] = owner;
        }
    }

    /**
     * The number of guides of a line, which must come after the one asked about before. Their
     * columns are {@link #column} of 0 up to that number, until the next line is asked about.
     */
    public int next(int line) {
        int indent = IndentFolding.indent(model, line, tabSize);
        if (indent >= 0) {
            while (size > 0 && columns[size - 1] >= indent) {
                size--;
            }
            int count = size;
            push(indent, line);
            return count;
        }
        // A blank line: the guides of the line before it or of the one after it, whichever has more.
        int following = -1;
        int limit = Math.min(model.lineCount(), line + 1 + MAX_BLANK_LINES);
        for (int next = line + 1; next < limit && following < 0; next++) {
            following = IndentFolding.indent(model, next, tabSize);
        }
        int count = 0;
        while (count < size && columns[count] < following) {
            count++;
        }
        return Math.max(size - 1, count);
    }

    /** The column of a guide of the line asked about last, counted in cells from the start of the line. */
    public int column(int guide) {
        return columns[guide];
    }

    /** The line a guide of the line asked about last starts under: the one whose lines it runs beside. */
    public int line(int guide) {
        return lines[guide];
    }

    /**
     * The line that starts the block a line is in, which is the line the innermost guide around
     * it starts under, or -1 at the top level. A line that has lines indented under it starts
     * its own block, and a line that ends one, as a closing bracket does, is in the block it ends.
     */
    public static int blockStart(TextModel model, int line, int tabSize) {
        int indent = IndentFolding.indent(model, line, tabSize);
        if (indent < 0) {
            // a blank line is in the block of its innermost guide
            IndentGuides guides = new IndentGuides(model, tabSize);
            guides.startAt(line);
            int count = guides.next(line);
            return count > 0 ? guides.line(count - 1) : -1;
        }
        if (IndentFolding.isFoldStart(model, line, tabSize)) {
            return line;
        }
        // Right below lines indented further the line ends their block, which the first line above
        // that is indented the same started. Otherwise it is in the block of the first line above
        // that is indented less, with the lines beside it and their blocks in between.
        boolean belowDeeper = false;
        boolean beside = false;
        for (int previous = line - 1; previous >= Math.max(0, line - MAX_LINES_UP); previous--) {
            int other = IndentFolding.indent(model, previous, tabSize);
            if (other < 0) {
                continue;
            }
            if (other < indent) {
                return previous;
            }
            if (!beside) {
                if (other > indent) {
                    belowDeeper = true;
                } else if (belowDeeper) {
                    return previous;
                } else {
                    beside = true;
                }
            }
        }
        return -1;
    }

    private void push(int column, int line) {
        if (size == columns.length) {
            columns = Arrays.copyOf(columns, size * 2);
            lines = Arrays.copyOf(lines, size * 2);
        }
        columns[size] = column;
        lines[size++] = line;
    }
}
