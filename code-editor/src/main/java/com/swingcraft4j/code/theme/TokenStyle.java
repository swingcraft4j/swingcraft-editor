package com.swingcraft4j.code.theme;

import java.awt.Color;
import java.awt.Font;

/**
 * How one kind of token is drawn.
 *
 * @param color     text colour
 * @param fontStyle {@link Font#PLAIN}, {@link Font#BOLD}, {@link Font#ITALIC} or bold and italic combined
 */
public record TokenStyle(Color color, int fontStyle) {

    public static TokenStyle of(Color color) {
        return new TokenStyle(color, Font.PLAIN);
    }

    public static TokenStyle of(int rgb) {
        return new TokenStyle(new Color(rgb), Font.PLAIN);
    }

    public static TokenStyle italic(int rgb) {
        return new TokenStyle(new Color(rgb), Font.ITALIC);
    }
}
