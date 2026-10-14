package com.swingcraft4j.code.viewer;

import java.awt.Color;
import java.util.Objects;

/**
 * A background laid behind a part of the text of a viewer, such as the lines a diff took away
 * and the words that changed in them. Unlike the selection it does not move with the caret; it
 * stays on its text when the text is edited, as a {@link com.swingcraft4j.code.marker.Marker} does.
 * <pre>{@code
 * viewer.setHighlights(List.of(
 *         Highlight.line(model.lineStart(4), model.lineEnd(4), theme.deletedLineBackground()),
 *         Highlight.text(model.lineStart(4) + 8, model.lineStart(4) + 13, theme.deletedTextBackground())));
 * }</pre>
 *
 * @param start the offset of the first char of the range
 * @param end   the offset just past the range
 * @param color the colour of the background
 * @param kind  whether whole lines or the chars of the range get the background
 */
public record Highlight(int start, int end, Color color, Kind kind) {

    /** What of the text a highlight covers. */
    public enum Kind {
        /**
         * Every row of the lines from the line of {@code start} to the line of {@code end}, from
         * edge to edge of the view, under the background of the chars.
         */
        LINE,
        /** The chars of the range, as the selection covers them. */
        TEXT
    }

    public Highlight {
        Objects.requireNonNull(color);
        Objects.requireNonNull(kind);
        if (start < 0 || end < start) {
            throw new IllegalArgumentException("not a range: [" + start + ", " + end + ")");
        }
    }

    /** The lines from the one that holds {@code start} to the one that holds {@code end}, whole and from edge to edge. */
    public static Highlight line(int start, int end, Color color) {
        return new Highlight(start, end, color, Kind.LINE);
    }

    /** The chars {@code [start, end)}. */
    public static Highlight text(int start, int end, Color color) {
        return new Highlight(start, end, color, Kind.TEXT);
    }
}
