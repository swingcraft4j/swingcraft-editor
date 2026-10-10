package com.swingcraft4j.code.lexer;

import java.util.List;

/**
 * Text that is no language: nothing in it is highlighted. A viewer shows such text with no
 * language at all; this one is what an {@link OverlayLanguage} is laid over where the text
 * around the syntax of one's own is plain, as a URL or a message with placeholders in it is:
 * <pre>{@code
 * Language template = OverlayLanguage.over(new PlainTextLanguage())
 *         .pattern(TokenType.VARIABLE, "\\{\\{[^{}]*\\}\\}")
 *         .build();
 * }</pre>
 * It is not among the languages that {@link Languages} finds, which are those to choose from
 * for a file.
 */
public final class PlainTextLanguage implements Language {

    @Override
    public String id() {
        return "text";
    }

    @Override
    public String displayName() {
        return "Plain Text";
    }

    @Override
    public List<String> fileExtensions() {
        return List.of();
    }

    @Override
    public Lexer createLexer() {
        return (text, start, end, state, sink) -> Lexer.INITIAL_STATE;
    }
}
