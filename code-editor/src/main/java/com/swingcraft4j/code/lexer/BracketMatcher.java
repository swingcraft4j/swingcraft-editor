package com.swingcraft4j.code.lexer;

import com.swingcraft4j.code.text.TextModel;

import java.util.Arrays;

/**
 * Finds the partner of a round, square or curly bracket. Brackets inside strings and comments
 * are not counted when a lexer and its line states are available. An instance is used by one
 * thread at a time.
 */
public final class BracketMatcher {

    private static final String BRACKETS = "()[]{}";

    private char[] buffer = new char[256];
    private boolean[] ignored = new boolean[256];
    private final TokenSink marker = (start, length, type) -> {
        if (type == TokenType.STRING || type == TokenType.COMMENT || type == TokenType.DOC_COMMENT) {
            Arrays.fill(ignored, start, start + length, true);
        }
    };

    public static boolean isBracket(char c) {
        return BRACKETS.indexOf(c) >= 0;
    }

    /**
     * The offset of the bracket matching the one at {@code offset}, or -1 when the char there
     * is not a bracket, sits in a string or comment, or has no partner within reach.
     *
     * @param lexer    used to tell code from strings and comments, or null to count every bracket
     * @param states   the line states for the lexer, or null
     * @param maxLines how many lines to search before giving up
     */
    public int match(TextModel model, int offset, Lexer lexer, LineStateIndex states, int maxLines) {
        int kind = BRACKETS.indexOf(model.charAt(offset));
        if (kind < 0) {
            return -1;
        }
        boolean forward = kind % 2 == 0;
        char same = BRACKETS.charAt(kind);
        char partner = BRACKETS.charAt(forward ? kind + 1 : kind - 1);
        int step = forward ? 1 : -1;

        int line = model.lineOfOffset(offset);
        int index = offset - model.lineStart(line);
        int depth = 0;
        for (int searched = 0; searched < maxLines && line >= 0 && line < model.lineCount(); searched++, line += step) {
            int length = load(model, line, lexer, states);
            if (searched == 0 && ignored[index]) {
                return -1;
            }
            int i = searched == 0 ? index : forward ? 0 : length - 1;
            for (; i >= 0 && i < length; i += step) {
                if (ignored[i]) {
                    continue;
                }
                if (buffer[i] == same) {
                    depth++;
                } else if (buffer[i] == partner && --depth == 0) {
                    return model.lineStart(line) + i;
                }
            }
        }
        return -1;
    }

    /** Reads a line into the buffer and marks the chars that lie in strings and comments. */
    private int load(TextModel model, int line, Lexer lexer, LineStateIndex states) {
        int start = model.lineStart(line);
        int length = model.lineLength(line);
        if (length >= buffer.length) {
            buffer = new char[Math.max(length + 1, buffer.length * 2)];
            ignored = new boolean[buffer.length];
        }
        model.getChars(start, start + length, buffer, 0);
        Arrays.fill(ignored, 0, length + 1, false);
        int state = lexer != null && states != null ? states.startState(line) : -1;
        if (state >= 0) {
            lexer.tokenize(buffer, 0, length, state, marker);
        }
        return length;
    }
}
