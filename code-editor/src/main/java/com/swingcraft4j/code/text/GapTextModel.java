package com.swingcraft4j.code.text;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * {@link EditableTextModel} backed by a gap buffer: the chars sit in one array with a gap at
 * the place of the last edit, so typing costs no more than moving the gap to the caret.
 * <p>
 * The line starts are kept in a second gap buffer. Lines before its gap store their start
 * offset; lines after it store their distance from the end of the text, which an edit before
 * them does not change.
 */
public final class GapTextModel implements EditableTextModel {

    private static final int MIN_GROWTH = 1024;

    private char[] chars;
    private int gapStart;
    private int gapEnd;
    private int length;

    private int[] lines;
    private int lineGapStart;
    private int lineGapEnd;

    private final List<TextListener> listeners = new CopyOnWriteArrayList<>();

    public GapTextModel() {
        this("");
    }

    public GapTextModel(CharSequence text) {
        this(toChars(text), text.length());
    }

    /** A model holding a copy of another model's text. */
    public static GapTextModel copyOf(TextModel model) {
        int n = model.length();
        char[] chars = new char[n + growth(n)];
        model.getChars(0, n, chars, 0);
        return new GapTextModel(chars, n);
    }

    private static char[] toChars(CharSequence text) {
        int n = text.length();
        char[] chars = new char[n + growth(n)];
        if (text instanceof String string) {
            string.getChars(0, n, chars, 0);
        } else {
            for (int i = 0; i < n; i++) {
                chars[i] = text.charAt(i);
            }
        }
        return chars;
    }

    private static int growth(int size) {
        return Math.max(MIN_GROWTH, size / 8);
    }

    private GapTextModel(char[] chars, int length) {
        this.chars = chars;
        this.length = length;
        gapStart = length;
        gapEnd = chars.length;
        int count = 1;
        for (int i = 0; i < length; i++) {
            if (endsLine(i)) {
                count++;
            }
        }
        lines = new int[count + growth(count)];
        lineGapStart = 1;
        for (int i = 0; i < length; i++) {
            if (endsLine(i)) {
                lines[lineGapStart++] = i + 1;
            }
        }
        lineGapEnd = lines.length;
    }

    /** Whether the char at the offset is the last char of a line terminator. */
    private boolean endsLine(int offset) {
        char c = charAt(offset);
        return c == '\n' || (c == '\r' && (offset + 1 == length || charAt(offset + 1) != '\n'));
    }

    @Override
    public int length() {
        return length;
    }

    @Override
    public int lineCount() {
        return lines.length - (lineGapEnd - lineGapStart);
    }

    @Override
    public int lineStart(int line) {
        return line < lineGapStart ? lines[line] : length - lines[line + (lineGapEnd - lineGapStart)];
    }

    @Override
    public int lineEnd(int line) {
        if (line + 1 == lineCount()) {
            return length;
        }
        int end = lineStart(line + 1) - 1;
        if (charAt(end) == '\n' && end > lineStart(line) && charAt(end - 1) == '\r') {
            end--;
        }
        return end;
    }

    @Override
    public int lineOfOffset(int offset) {
        int low = 0;
        int high = lineCount() - 1;
        while (low < high) {
            int middle = (low + high + 1) >>> 1;
            if (lineStart(middle) <= offset) {
                low = middle;
            } else {
                high = middle - 1;
            }
        }
        return low;
    }

    @Override
    public char charAt(int offset) {
        return chars[offset < gapStart ? offset : offset + (gapEnd - gapStart)];
    }

    @Override
    public void getChars(int start, int end, char[] dst, int dstStart) {
        int beforeGap = Math.min(end, gapStart) - start;
        if (beforeGap > 0) {
            System.arraycopy(chars, start, dst, dstStart, beforeGap);
            start += beforeGap;
            dstStart += beforeGap;
        }
        if (start < end) {
            System.arraycopy(chars, start + (gapEnd - gapStart), dst, dstStart, end - start);
        }
    }

    @Override
    public void replace(int start, int end, CharSequence text) {
        if (start < 0 || end < start || end > length) {
            throw new IndexOutOfBoundsException("[" + start + ", " + end + ") in a text of length " + length);
        }
        int inserted = text.length();
        if (start == end && inserted == 0) {
            return;
        }
        // The lines that may change. An edit at the very start of a line can also change the
        // line before it, by completing or breaking up a \r\n pair.
        int firstLine = lineOfOffset(start);
        if (firstLine > 0 && start == lineStart(firstLine)) {
            firstLine--;
        }
        int lastLine = lineOfOffset(end);
        int oldLineCount = lineCount();
        boolean reachesEnd = lastLine + 1 == oldLineCount;
        int regionStart = lineStart(firstLine);
        int regionEnd = reachesEnd ? length : lineStart(lastLine + 1);

        // Converting the lines after the edit to end-relative must happen before the length changes.
        moveLineGap(lastLine + 1);

        moveGap(start);
        gapEnd += end - start;
        ensureGap(inserted);
        for (int i = 0; i < inserted; i++) {
            chars[gapStart++] = text.charAt(i);
        }
        int delta = inserted - (end - start);
        length += delta;
        regionEnd += delta;

        // Drop the starts of the lines inside the region and find them again.
        lineGapStart = firstLine + 1;
        for (int i = regionStart; i < regionEnd; i++) {
            // A terminator closing the region starts a line that is already stored after the
            // gap, unless the region runs to the end of the text.
            if (endsLine(i) && (i + 1 < regionEnd || reachesEnd)) {
                ensureLineGap();
                lines[lineGapStart++] = i + 1;
            }
        }

        TextChange change = new TextChange(start, end - start, inserted,
                firstLine, lastLine - firstLine, lineGapStart - 1 - firstLine);
        for (TextListener listener : listeners) {
            listener.textChanged(change);
        }
    }

    private void moveGap(int to) {
        if (to < gapStart) {
            int count = gapStart - to;
            System.arraycopy(chars, to, chars, gapEnd - count, count);
            gapStart -= count;
            gapEnd -= count;
        } else if (to > gapStart) {
            int count = to - gapStart;
            System.arraycopy(chars, gapEnd, chars, gapStart, count);
            gapStart += count;
            gapEnd += count;
        }
    }

    private void ensureGap(int needed) {
        if (gapEnd - gapStart >= needed) {
            return;
        }
        int tail = chars.length - gapEnd;
        char[] bigger = new char[chars.length + Math.max(needed, growth(chars.length))];
        System.arraycopy(chars, 0, bigger, 0, gapStart);
        System.arraycopy(chars, gapEnd, bigger, bigger.length - tail, tail);
        gapEnd = bigger.length - tail;
        chars = bigger;
    }

    /** Moves the line gap so that exactly {@code count} lines lie before it. */
    private void moveLineGap(int count) {
        while (lineGapStart > count) {
            lines[--lineGapEnd] = length - lines[--lineGapStart];
        }
        while (lineGapStart < count) {
            lines[lineGapStart++] = length - lines[lineGapEnd++];
        }
    }

    private void ensureLineGap() {
        if (lineGapEnd > lineGapStart) {
            return;
        }
        int tail = lines.length - lineGapEnd;
        int[] bigger = new int[lines.length + growth(lines.length)];
        System.arraycopy(lines, 0, bigger, 0, lineGapStart);
        System.arraycopy(lines, lineGapEnd, bigger, bigger.length - tail, tail);
        lineGapEnd = bigger.length - tail;
        lines = bigger;
    }

    @Override
    public void addTextListener(TextListener listener) {
        listeners.add(listener);
    }

    @Override
    public void removeTextListener(TextListener listener) {
        listeners.remove(listener);
    }

    @Override
    public String toString() {
        return getText(0, length);
    }
}
