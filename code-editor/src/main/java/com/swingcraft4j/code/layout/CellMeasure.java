package com.swingcraft4j.code.layout;

/**
 * Tells how wide a cluster of chars outside the basic Latin range is. A cluster is a base
 * char with the marks and joined chars that are drawn as one unit with it; see
 * {@link Cells#clusterEnd}.
 */
@FunctionalInterface
public interface CellMeasure {

    /** An estimate from the kind of char alone, for use where no font is at hand. */
    CellMeasure ESTIMATE = (text, start, end) -> Cells.width(text[start]);

    /**
     * The width of the cluster {@code text[start, end)} in cells, which need not be a whole
     * number; zero for a cluster with no width of its own.
     */
    double clusterAdvance(char[] text, int start, int end);

    /**
     * Whether a char of the basic Latin range takes exactly one cell, as in a monospaced font.
     * If not, those chars are measured like the others, as clusters with the marks that follow
     * them, and nothing is rounded to whole cells.
     */
    default boolean monospaced() {
        return true;
    }
}
