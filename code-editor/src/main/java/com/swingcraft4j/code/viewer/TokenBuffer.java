package com.swingcraft4j.code.viewer;

import com.swingcraft4j.code.lexer.TokenSink;
import com.swingcraft4j.code.lexer.TokenType;

import java.util.Arrays;

/** Reusable store for the tokens of one line, read back in increasing index order. */
final class TokenBuffer implements TokenSink {

    private int[] starts = new int[64];
    private int[] ends = new int[64];
    private TokenType[] types = new TokenType[64];
    private int count;
    private int cursor;

    void clear() {
        count = 0;
        cursor = 0;
    }

    @Override
    public void token(int start, int length, TokenType type) {
        if (count == starts.length) {
            starts = Arrays.copyOf(starts, count * 2);
            ends = Arrays.copyOf(ends, count * 2);
            types = Arrays.copyOf(types, count * 2);
        }
        starts[count] = start;
        ends[count] = start + length;
        types[count] = type;
        count++;
    }

    /** Type of the char at the index; indexes must not decrease between calls. */
    TokenType typeAt(int index) {
        while (cursor < count && ends[cursor] <= index) {
            cursor++;
        }
        return cursor < count && starts[cursor] <= index ? types[cursor] : TokenType.TEXT;
    }
}
