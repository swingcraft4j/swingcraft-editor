package com.swingcraft4j.code.text;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * Immutable {@link TextModel} backed by one flat array plus an array of line starts.
 * Text whose chars all fit in one byte is stored as bytes, halving its footprint.
 */
public final class ArrayTextModel implements TextModel {

    private final byte[] bytes;
    private final char[] chars;
    private final int length;
    private final int[] lineStarts;

    private ArrayTextModel(byte[] bytes, char[] chars, int length, int[] lineStarts) {
        this.bytes = bytes;
        this.chars = chars;
        this.length = length;
        this.lineStarts = lineStarts;
    }

    public static ArrayTextModel of(CharSequence text) {
        int n = text.length();
        boolean compact = true;
        int lines = 1;
        for (int i = 0; i < n; i++) {
            char c = text.charAt(i);
            if (c > 0xFF) {
                compact = false;
            } else if (c == '\n' || (c == '\r' && (i + 1 == n || text.charAt(i + 1) != '\n'))) {
                lines++;
            }
        }
        byte[] bytes = compact ? new byte[n] : null;
        char[] chars = compact ? null : new char[n];
        int[] lineStarts = new int[lines];
        int line = 1;
        for (int i = 0; i < n; i++) {
            char c = text.charAt(i);
            if (compact) {
                bytes[i] = (byte) c;
            } else {
                chars[i] = c;
            }
            if (c == '\n' || (c == '\r' && (i + 1 == n || text.charAt(i + 1) != '\n'))) {
                lineStarts[line++] = i + 1;
            }
        }
        return new ArrayTextModel(bytes, chars, n, lineStarts);
    }

    /** Reads and decodes a whole file. Safe to call off the event dispatch thread. */
    public static ArrayTextModel load(Path file, Charset charset) throws IOException {
        CharBuffer decoded = charset.decode(ByteBuffer.wrap(Files.readAllBytes(file)));
        if (decoded.length() > 0 && decoded.charAt(0) == '﻿') {
            decoded.get();
        }
        return of(decoded);
    }

    @Override
    public int length() {
        return length;
    }

    @Override
    public int lineCount() {
        return lineStarts.length;
    }

    @Override
    public int lineStart(int line) {
        return lineStarts[line];
    }

    @Override
    public int lineEnd(int line) {
        if (line + 1 == lineStarts.length) {
            return length;
        }
        int end = lineStarts[line + 1] - 1;
        if (charAt(end) == '\n' && end > lineStarts[line] && charAt(end - 1) == '\r') {
            end--;
        }
        return end;
    }

    @Override
    public int lineOfOffset(int offset) {
        int index = Arrays.binarySearch(lineStarts, offset);
        return index >= 0 ? index : -index - 2;
    }

    @Override
    public char charAt(int offset) {
        return bytes != null ? (char) (bytes[offset] & 0xFF) : chars[offset];
    }

    @Override
    public void getChars(int start, int end, char[] dst, int dstStart) {
        if (bytes == null) {
            System.arraycopy(chars, start, dst, dstStart, end - start);
            return;
        }
        for (int i = start; i < end; i++) {
            dst[dstStart++] = (char) (bytes[i] & 0xFF);
        }
    }
}
