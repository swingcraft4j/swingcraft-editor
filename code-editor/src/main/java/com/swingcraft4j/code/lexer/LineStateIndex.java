package com.swingcraft4j.code.lexer;

/** The lexer state at the start of each line of a document, as far as it is known. */
public interface LineStateIndex {

    /** State at the start of the line, or {@code -1} when it has not been computed yet. */
    int startState(int line);

    /** Stops any work still going on for this index. */
    void dispose();
}
