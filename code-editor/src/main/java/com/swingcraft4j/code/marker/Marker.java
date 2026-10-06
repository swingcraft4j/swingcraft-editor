package com.swingcraft4j.code.marker;

import java.util.Objects;

/**
 * A note attached to a range of a document, such as an error found by a compiler. A viewer
 * underlines the range with a wavy line, marks its line in the gutter and shows the message
 * in a tooltip.
 *
 * @param start    the offset of the first char of the range
 * @param end      the offset just past the range; equal to {@code start} to mark a position
 * @param severity how serious it is, which decides the colour
 * @param message  what is shown in the tooltip
 */
public record Marker(int start, int end, Severity severity, String message) {

    public Marker {
        Objects.requireNonNull(severity);
        Objects.requireNonNull(message);
        if (start < 0 || end < start) {
            throw new IllegalArgumentException("not a range: [" + start + ", " + end + ")");
        }
    }

    public static Marker error(int start, int end, String message) {
        return new Marker(start, end, Severity.ERROR, message);
    }

    public static Marker warning(int start, int end, String message) {
        return new Marker(start, end, Severity.WARNING, message);
    }

    public static Marker info(int start, int end, String message) {
        return new Marker(start, end, Severity.INFO, message);
    }
}
