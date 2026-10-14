package com.swingcraft4j.code.diff;

/** How finely the changes within a line are found, in {@link InlineDiff} and {@link DiffHighlighter}. */
public enum Granularity {
    /** Not at all: a changed line is changed as a whole. */
    LINE,
    /**
     * By word, as {@code git diff --word-diff} does: a word, a run of blanks or a sign that
     * differs is changed whole, so {@code count} to {@code total} is one change, not a few letters.
     */
    WORD,
    /** By char: {@code abc} to {@code abcd} is the {@code d} put in. */
    CHAR
}
