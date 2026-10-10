package com.swingcraft4j.code.format;

/**
 * How a {@link Formatter} is to lay text out. An editor fills this in from its own settings,
 * so that formatted text is indented as typed text is.
 *
 * @param tabSize       the cells of a tab stop, and of one level of indentation
 * @param useTabs       whether a level of indentation is a tab char instead of spaces
 * @param lineSeparator what ends a line that the formatter adds
 */
public record FormatOptions(int tabSize, boolean useTabs, String lineSeparator) {

    /** Options for a text whose lines end with a line feed. */
    public FormatOptions(int tabSize, boolean useTabs) {
        this(tabSize, useTabs, "\n");
    }

    /** The white space that indents a line by a number of levels. */
    public String indent(int levels) {
        return useTabs ? "\t".repeat(levels) : " ".repeat(levels * tabSize);
    }
}
