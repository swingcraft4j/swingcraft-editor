package com.swingcraft4j.code.format;

import java.util.List;

/** The lines of a text, for the formatters that work a line at a time. */
final class TextLines {

    private TextLines() {
    }

    /** Splits a text into its lines and what ends each of them, which is nothing for the last. */
    static void split(String text, List<String> lines, List<String> terminators) {
        int start = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\n' || c == '\r') {
                int end = c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n' ? i + 2 : i + 1;
                lines.add(text.substring(start, i));
                terminators.add(text.substring(i, end));
                start = end;
                i = end - 1;
            }
        }
        lines.add(text.substring(start));
        terminators.add("");
    }
}
