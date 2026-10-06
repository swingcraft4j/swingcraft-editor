package com.swingcraft4j.code.text;

/**
 * Read access to a document as a sequence of UTF-16 chars split into lines.
 * <p>
 * Lines are separated by {@code \n}, {@code \r\n} or {@code \r}. A model always has at least
 * one line; an empty document is a single empty line.
 */
public interface TextModel {

    /** Total number of chars, line terminators included. */
    int length();

    int lineCount();

    /** Offset of the first char of the line. */
    int lineStart(int line);

    /** Offset just past the last char of the line, excluding its terminator. */
    int lineEnd(int line);

    default int lineLength(int line) {
        return lineEnd(line) - lineStart(line);
    }

    /** The line containing the offset. An offset inside a terminator belongs to the line it ends. */
    int lineOfOffset(int offset);

    char charAt(int offset);

    void getChars(int start, int end, char[] dst, int dstStart);

    default String getText(int start, int end) {
        char[] chars = new char[end - start];
        getChars(start, end, chars, 0);
        return new String(chars);
    }
}
