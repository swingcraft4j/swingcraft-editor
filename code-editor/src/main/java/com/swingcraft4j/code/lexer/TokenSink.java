package com.swingcraft4j.code.lexer;

/** Receives the tokens of one line, in order and without overlaps. */
@FunctionalInterface
public interface TokenSink {

    /** @param start index into the char array passed to {@link Lexer#tokenize} */
    void token(int start, int length, TokenType type);
}
