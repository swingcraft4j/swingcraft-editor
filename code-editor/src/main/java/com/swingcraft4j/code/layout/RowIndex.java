package com.swingcraft4j.code.layout;

import com.swingcraft4j.code.text.TextModel;

import java.util.Arrays;
import java.util.BitSet;

/**
 * Maps document lines to visual rows. Without wrapping a row is a line; with wrapping a line
 * of {@code n} cells takes {@code ceil(n / wrapCells)} rows, so re-wrapping is one pass of
 * integer arithmetic over the per-line cell counts measured at construction.
 * <p>
 * The index describes the model as it was when measured. After the model changes, tell it
 * with {@link #linesReplaced} before using it again.
 */
public final class RowIndex {

    /**
     * Set on the cell count of a line containing tabs or chars that are not one cell wide; with
     * a measure that is not monospaced, that is every line with text on it.
     */
    private static final int COMPLEX = 0x8000_0000;

    private final TextModel model;
    private final int tabSize;
    private final CellMeasure measure;
    private int lineCount;
    /** Cell count per line, or null while every line has exactly one cell per char. */
    private int[] lineCells;
    private int maxCells;

    private int wrapCells;
    private boolean wordWrap;
    /** Scratch space: where the rows of the line last broken at words begin. */
    private double[] wordBreaks = new double[16];
    /** The lines hidden inside collapsed folds, which take no rows; null when there are none. */
    private BitSet hidden;
    /** First row of each line plus a final total, or null when every line takes exactly one row. */
    private int[] rowStarts;
    private int rowCount;

    private char[] buffer = new char[256];

    /** An index that estimates the width of text outside the basic Latin range. */
    public RowIndex(TextModel model, int tabSize) {
        this(model, tabSize, CellMeasure.ESTIMATE);
    }

    /** @param measure tells the width of text outside the basic Latin range, as from a font */
    public RowIndex(TextModel model, int tabSize, CellMeasure measure) {
        this.model = model;
        this.tabSize = tabSize;
        this.measure = measure;
        this.lineCount = model.lineCount();
        int max = 0;
        for (int line = 0; line < lineCount; line++) {
            int cells = measure(line);
            if (cells < 0 && lineCells == null) {
                lineCells = new int[lineCount];
                for (int i = 0; i < line; i++) {
                    lineCells[i] = model.lineLength(i);
                }
            }
            if (lineCells != null) {
                lineCells[line] = cells;
            }
            max = Math.max(max, cells & ~COMPLEX);
        }
        this.maxCells = max;
        this.rowCount = lineCount;
    }

    /** The cell count of a line as it is now in the model, with {@link #COMPLEX} set if it applies. */
    private int measure(int line) {
        int start = model.lineStart(line);
        int length = model.lineLength(line);
        if (length > buffer.length) {
            buffer = new char[Math.max(length, buffer.length * 2)];
        }
        model.getChars(start, start + length, buffer, 0);
        if (length > 0 && !measure.monospaced()) {
            return Cells.count(buffer, 0, length, tabSize, measure) | COMPLEX;
        }
        for (int i = 0; i < length; i++) {
            if (buffer[i] == '\t' || buffer[i] >= 0x300) {
                return Cells.count(buffer, 0, length, tabSize, measure) | COMPLEX;
            }
        }
        return length;
    }

    /**
     * Updates the index after the model replaced the lines {@code firstLine} to
     * {@code firstLine + removedLines} by the lines {@code firstLine} to
     * {@code firstLine + insertedLines}.
     */
    public void linesReplaced(int firstLine, int removedLines, int insertedLines) {
        int oldCount = lineCount;
        int newCount = model.lineCount();
        boolean rescanMax;
        if (lineCells == null) {
            // Until now a line's cells were its length; from here on they are stored.
            lineCells = new int[newCount + newCount / 8 + 16];
            for (int line = 0; line < newCount; line++) {
                if (line < firstLine || line > firstLine + insertedLines) {
                    lineCells[line] = model.lineLength(line);
                }
            }
            rescanMax = true;
        } else {
            rescanMax = false;
            for (int line = firstLine; line <= firstLine + removedLines; line++) {
                rescanMax |= (lineCells[line] & ~COMPLEX) == maxCells;
            }
            if (newCount > lineCells.length) {
                lineCells = Arrays.copyOf(lineCells, newCount + newCount / 8 + 16);
            }
            if (removedLines != insertedLines) {
                int oldTail = firstLine + removedLines + 1;
                System.arraycopy(lineCells, oldTail, lineCells, firstLine + insertedLines + 1, oldCount - oldTail);
            }
        }
        lineCount = newCount;
        int max = 0;
        for (int line = firstLine; line <= firstLine + insertedLines; line++) {
            lineCells[line] = measure(line);
            max = Math.max(max, lineCells[line] & ~COMPLEX);
        }
        if (max >= maxCells) {
            maxCells = max;
        } else if (rescanMax) {
            for (int line = 0; line < lineCount; line++) {
                max = Math.max(max, lineCells[line] & ~COMPLEX);
            }
            maxCells = max;
        }
        rebuildRows(firstLine);
    }

    /** Sets the row width in cells; zero or less turns wrapping off. */
    public void setWrapCells(int cells) {
        wrapCells = Math.max(0, cells);
        rebuildRows(0);
    }

    /** Sets the lines that take no rows because they are folded away; null or empty for none. */
    public void setHiddenLines(BitSet hiddenLines) {
        hidden = hiddenLines == null || hiddenLines.isEmpty() ? null : hiddenLines;
        rebuildRows(0);
    }

    public boolean isHidden(int line) {
        return hidden != null && hidden.get(line);
    }

    /** Recomputes the rows of the lines from {@code fromLine} on; the earlier ones must be current. */
    private void rebuildRows(int fromLine) {
        if (hidden == null && (wrapCells == 0 || maxCells <= wrapCells)) {
            rowStarts = null;
            rowCount = lineCount;
            return;
        }
        if (rowStarts == null) {
            rowStarts = new int[lineCount + 1];
            fromLine = 0;
        } else if (rowStarts.length < lineCount + 1) {
            rowStarts = Arrays.copyOf(rowStarts, lineCount + 1 + lineCount / 8);
        }
        int row = fromLine == 0 ? 0 : rowStarts[fromLine];
        for (int line = fromLine; line < lineCount; line++) {
            rowStarts[line] = row;
            if (hidden != null && hidden.get(line)) {
                continue;
            }
            int count = lineCells(line);
            if (wrapCells == 0 || count <= wrapCells) {
                row++;
            } else if (wordWrap) {
                row += breakAtWords(line); // only the lines that wrap are read
            } else {
                row += (count + wrapCells - 1) / wrapCells;
            }
        }
        rowStarts[lineCount] = row;
        rowCount = row;
    }

    public boolean isWordWrap() {
        return wordWrap;
    }

    /**
     * Sets whether a line that is too long is broken after the last blank that fits, rather
     * than after the last char. That reads better, but where the breaks fall is then no longer
     * plain arithmetic: every line that wraps has to be read to find them.
     */
    public void setWordWrap(boolean wordWrap) {
        this.wordWrap = wordWrap;
        rebuildRows(0);
    }

    /**
     * The cells at which the rows of a line begin when it is broken at words: the first is
     * zero, and there are {@link #rowsOf} of them.
     *
     * @param into an array to fill if it is long enough
     * @return the array filled, which is a longer one if {@code into} was too short
     */
    public double[] wordRowStarts(int line, double[] into) {
        int count = breakAtWords(line);
        double[] starts = into.length >= count ? into : new double[count + count / 2];
        System.arraycopy(wordBreaks, 0, starts, 0, count);
        return starts;
    }

    /**
     * Breaks a line into rows of at most the wrap width, each as long as possible but ending
     * after a blank where one fits; a word longer than a row is broken wherever the row ends.
     * Leaves where the rows begin in {@link #wordBreaks}.
     *
     * @return the number of rows
     */
    private int breakAtWords(int line) {
        int start = model.lineStart(line);
        int length = model.lineLength(line);
        if (length > buffer.length) {
            buffer = new char[Math.max(length, buffer.length * 2)];
        }
        model.getChars(start, start + length, buffer, 0);
        int count = 1;
        wordBreaks[0] = 0;
        double at = 0;
        double rowStart = 0;
        double lastBlank = -1; // where the text after the last blank of this row begins
        boolean inRun = false;
        boolean grid = measure.monospaced();
        int i = 0;
        while (i < length) {
            char c = buffer[i];
            int next = i + 1;
            double after;
            if (c == '\t' || (grid && c < 0x300)) {
                if (inRun) {
                    at = Cells.roundUp(at);
                    inRun = false;
                }
                after = c == '\t' ? Cells.tabStop(at, tabSize) : at + 1;
            } else {
                next = Cells.clusterEnd(buffer, i, length);
                after = at + measure.clusterAdvance(buffer, i, next);
                inRun = grid;
            }
            boolean blank = c == ' ' || c == '\t';
            // A piece that does not fit starts a new row, taken from after the last blank if
            // there was one; when even that leaves too much, the row breaks right before it.
            while (after > rowStart + wrapCells + 1e-6 && at > rowStart) {
                rowStart = !blank && lastBlank > rowStart ? lastBlank : at;
                lastBlank = -1;
                if (count == wordBreaks.length) {
                    wordBreaks = Arrays.copyOf(wordBreaks, count * 2);
                }
                wordBreaks[count++] = rowStart;
            }
            if (blank) {
                lastBlank = after;
            }
            at = after;
            i = next;
        }
        return count;
    }

    /** Row width in cells, or zero when wrapping is off. */
    public int wrapCells() {
        return wrapCells;
    }

    public int rowCount() {
        return rowCount;
    }

    /** Cell count of the widest line. */
    public int maxCells() {
        return maxCells;
    }

    public int lineCells(int line) {
        return lineCells != null ? lineCells[line] & ~COMPLEX : model.lineLength(line);
    }

    /** True when each char of the line occupies exactly one cell, so cell index equals char index. */
    public boolean isPlain(int line) {
        return lineCells == null || lineCells[line] >= 0;
    }

    public int firstRow(int line) {
        return rowStarts != null ? rowStarts[line] : line;
    }

    /** The number of rows the line takes; zero for a hidden line. */
    public int rowsOf(int line) {
        return rowStarts != null ? rowStarts[line + 1] - rowStarts[line] : 1;
    }

    public int lineAtRow(int row) {
        if (rowStarts == null) {
            return row;
        }
        // The last line starting at or before the row: hidden lines share the start of the
        // visible line after them, and that line is the one wanted.
        int low = 0;
        int high = lineCount - 1;
        while (low < high) {
            int middle = (low + high + 1) >>> 1;
            if (rowStarts[middle] <= row) {
                low = middle;
            } else {
                high = middle - 1;
            }
        }
        return low;
    }
}
