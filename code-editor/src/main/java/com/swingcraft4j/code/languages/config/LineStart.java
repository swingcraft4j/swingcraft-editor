package com.swingcraft4j.code.languages.config;

/** What the languages of this package share. */
final class LineStart {

    /**
     * Put in front of a pattern, lets it match only what is the first thing on its line: there is
     * nothing but blanks between it and the start of the line.
     */
    static final String ONLY = "(?<![^\\s][ \\t]{0,200})";

    private LineStart() {
    }
}
