package com.swingcraft4j.code.lexer;

/**
 * Tokenizes source text one line at a time.
 * <p>
 * Whatever must carry over a line break, such as being inside a block comment, is encoded in
 * an int state: the lexer receives the state at the start of a line and returns the state at
 * its end. A lexer keeps nothing else between calls, so any line can be tokenized on its own
 * once its start state is known. One instance is used by one thread at a time.
 */
public interface Lexer {

    /** State at the start of a document. */
    int INITIAL_STATE = 0;

    /**
     * Tokenizes {@code text[start, end)}, which is one line without its terminator. Chars not
     * covered by a reported token are treated as {@link TokenType#TEXT}.
     *
     * @param state the state at the start of the line
     * @return the state at the end of the line; never negative
     */
    int tokenize(char[] text, int start, int end, int state, TokenSink sink);
}
