package com.swingcraft4j.code.format;

/**
 * Lays the text of a language out afresh: its indentation, its spacing, its alignment. A
 * language gives its formatter with {@link com.swingcraft4j.code.lexer.Language#formatter()},
 * and an editor runs it on the whole text or on the selected lines.
 * <p>
 * A formatter never loses text. Where it cannot make sense of what it is given, as with a
 * bracket that is not closed, it returns the text unchanged.
 */
@FunctionalInterface
public interface Formatter {

    /**
     * Formats a whole text, or some whole lines of one.
     *
     * @return the formatted text, or {@code text} itself where there is nothing to change
     */
    String format(String text, FormatOptions options);
}
