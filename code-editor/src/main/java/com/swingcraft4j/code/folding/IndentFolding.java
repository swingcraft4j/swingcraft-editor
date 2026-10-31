package com.swingcraft4j.code.folding;

import com.swingcraft4j.code.text.TextModel;

import java.util.Arrays;

/**
 * Finds foldable regions from indentation alone, which works for any language that is
 * indented consistently: a line starts a region when the next non-blank line is indented
 * further, and the region runs to the last line indented further than its start. For code
 * with braces this leaves the line of the closing brace visible below a collapsed block.
 */
public final class IndentFolding {

    /** A run of blank lines longer than this ends the search for the line after them. */
    private static final int MAX_BLANK_LINES = 200;
    private static final int MAX_LINES_UP = 100_000;

    private IndentFolding() {
    }

    /** The indentation of a line in cells, or -1 for a line with nothing but blanks. */
    public static int indent(TextModel model, int line, int tabSize) {
        int end = model.lineEnd(line);
        int cell = 0;
        for (int i = model.lineStart(line); i < end; i++) {
            char c = model.charAt(i);
            if (c == ' ') {
                cell++;
            } else if (c == '\t') {
                cell += tabSize - cell % tabSize;
            } else {
                return cell;
            }
        }
        return -1;
    }

    /** Whether the line starts a region that can be collapsed. */
    public static boolean isFoldStart(TextModel model, int line, int tabSize) {
        int indent = indent(model, line, tabSize);
        if (indent < 0) {
            return false;
        }
        int limit = Math.min(model.lineCount(), line + 1 + MAX_BLANK_LINES);
        for (int next = line + 1; next < limit; next++) {
            int other = indent(model, next, tabSize);
            if (other >= 0) {
                return other > indent;
            }
        }
        return false;
    }

    /** The last line of the region starting at the line, or the line itself when it starts none. */
    public static int foldEnd(TextModel model, int line, int tabSize) {
        int indent = indent(model, line, tabSize);
        int end = line;
        if (indent < 0) {
            return end;
        }
        int lineCount = model.lineCount();
        for (int next = line + 1; next < lineCount; next++) {
            int other = indent(model, next, tabSize);
            if (other < 0) {
                continue; // blank lines belong to the region only if indented lines follow
            }
            if (other <= indent) {
                break;
            }
            end = next;
        }
        return end;
    }

    /** The line starting the innermost region around a line, or -1 when it is at the top level. */
    public static int enclosingFoldStart(TextModel model, int line, int tabSize) {
        int indent = indent(model, line, tabSize);
        if (indent < 0) {
            indent = Integer.MAX_VALUE;
        }
        for (int previous = line - 1; previous >= Math.max(0, line - MAX_LINES_UP); previous--) {
            int other = indent(model, previous, tabSize);
            if (other >= 0 && other < indent) {
                return previous;
            }
        }
        return -1;
    }

    /**
     * The lines starting the regions around a line, the outermost first. A blank line counts as
     * part of what follows it, as {@link #foldEnd} leaves the blank lines at the end of a region out.
     */
    public static int[] enclosingFoldStarts(TextModel model, int line, int tabSize) {
        int limit = indent(model, line, tabSize);
        int lineCount = model.lineCount();
        for (int next = line + 1; limit < 0 && next < Math.min(lineCount, line + 1 + MAX_BLANK_LINES); next++) {
            limit = indent(model, next, tabSize);
        }
        int[] found = new int[16];
        int count = 0;
        for (int previous = line - 1; limit > 0 && previous >= Math.max(0, line - MAX_LINES_UP); previous--) {
            int other = indent(model, previous, tabSize);
            if (other >= 0 && other < limit) {
                if (count == found.length) {
                    found = Arrays.copyOf(found, count * 2);
                }
                found[count++] = previous;
                limit = other;
            }
        }
        int[] outermostFirst = new int[count];
        for (int i = 0; i < count; i++) {
            outermostFirst[i] = found[count - 1 - i];
        }
        return outermostFirst;
    }

    /**
     * The line that names a region: the line it starts at, or where that holds only an opening
     * brace, the line before it at the same indentation, as the signature of a method is.
     */
    public static int foldHeader(TextModel model, int line, int tabSize) {
        int indent = indent(model, line, tabSize);
        if (indent < 0 || !isOnlyOpeningBrace(model, line)) {
            return line;
        }
        for (int previous = line - 1; previous >= Math.max(0, line - MAX_BLANK_LINES); previous--) {
            int other = indent(model, previous, tabSize);
            if (other >= 0) {
                return other == indent && canNameBlock(model, previous) ? previous : line;
            }
        }
        return line;
    }

    /** Whether a line is not complete in itself: one ending in a bracket or a separator, as the end of the object before in a list, names nothing after it. */
    private static boolean canNameBlock(TextModel model, int line) {
        for (int i = model.lineEnd(line) - 1; i >= model.lineStart(line); i--) {
            char c = model.charAt(i);
            if (c != ' ' && c != '\t' && c != '\r') {
                return ",;{}[]".indexOf(c) < 0;
            }
        }
        return false;
    }

    private static boolean isOnlyOpeningBrace(TextModel model, int line) {
        int end = model.lineEnd(line);
        int braces = 0;
        for (int i = model.lineStart(line); i < end; i++) {
            char c = model.charAt(i);
            if (c == '{') {
                braces++;
            } else if (c != ' ' && c != '\t' && c != '\r') {
                return false;
            }
        }
        return braces == 1;
    }
}
