package com.swingcraft4j.code.autocomplete;

import java.awt.Color;
import java.util.Locale;

/** What a suggestion is; it decides the icon shown beside it. */
public enum CompletionKind {

    KEYWORD('K', 0xC0692B),
    SNIPPET('S', 0x8E5BB5),
    /** A word found in the document, about which nothing more is known. */
    WORD('W', 0x8A8F98),
    METHOD('M', 0xC2477A),
    FIELD('F', 0x3D7FC4),
    VARIABLE('V', 0x4A90A4),
    CLASS('C', 0x2E9A6B),
    OTHER('•', 0x8A8F98);

    private final char letter;
    private final Color color;

    CompletionKind(char letter, int rgb) {
        this.letter = letter;
        this.color = new Color(rgb);
    }

    /** The letter shown in the icon. */
    public char letter() {
        return letter;
    }

    /** The colour of the icon; chosen to stand out on light and on dark backgrounds. */
    public Color color() {
        return color;
    }

    /** The name shown beside a suggestion that has no detail of its own. */
    public String label() {
        return name().toLowerCase(Locale.ROOT);
    }
}
