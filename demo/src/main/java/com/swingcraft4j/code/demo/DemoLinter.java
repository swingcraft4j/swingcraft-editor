package com.swingcraft4j.code.demo;

import com.swingcraft4j.code.lexer.Language;
import com.swingcraft4j.code.marker.Marker;
import com.swingcraft4j.code.text.TextModel;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Set;

/**
 * A stand-in for a real parser or linter, to show markers in the demos. It knows nothing of
 * the languages beyond what a string and a line comment look like in a few of them, so it
 * can be fooled; a real application would hand over what its compiler reports.
 */
final class DemoLinter {

    /** Longer documents are left alone: this runs on the event dispatch thread. */
    private static final int MAX_LENGTH = 400_000;
    private static final int MAX_LINE_LENGTH = 120;
    /** The languages whose brackets are checked: those with {@code //} comments and quoted strings. */
    private static final Set<String> BRACKET_LANGUAGES = Set.of("java", "javascript", "typescript", "json");

    private DemoLinter() {
    }

    /** Notes on a document: brackets without a partner, lines that are too long, blanks at line ends and TODOs. */
    static List<Marker> check(TextModel model, Language language) {
        List<Marker> markers = new ArrayList<>();
        if (model.length() > MAX_LENGTH) {
            return markers;
        }
        boolean brackets = language != null && BRACKET_LANGUAGES.contains(language.id());
        Deque<Integer> open = new ArrayDeque<>();
        for (int line = 0; line < model.lineCount(); line++) {
            int start = model.lineStart(line);
            String text = model.getText(start, model.lineEnd(line));
            int todo = text.indexOf("TODO");
            if (todo >= 0) {
                markers.add(Marker.info(start + todo, start + text.length(), "Something is left to do here"));
            }
            if (text.length() > MAX_LINE_LENGTH) {
                markers.add(Marker.warning(start + MAX_LINE_LENGTH, start + text.length(),
                        "Line is longer than " + MAX_LINE_LENGTH + " characters (" + text.length() + ")"));
            }
            int trimmed = text.stripTrailing().length();
            if (trimmed < text.length() && trimmed > 0) {
                markers.add(Marker.warning(start + trimmed, start + text.length(), "Trailing whitespace"));
            }
            if (brackets) {
                checkBrackets(model, text, start, open, markers);
            }
        }
        for (int offset : open) {
            markers.add(Marker.error(offset, offset + 1, "'" + model.charAt(offset) + "' is never closed"));
        }
        return markers;
    }

    private static void checkBrackets(TextModel model, String text, int lineStart, Deque<Integer> open, List<Marker> markers) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '/' && i + 1 < text.length() && text.charAt(i + 1) == '/') {
                return; // the rest of the line is a comment
            }
            if (c == '"' || c == '\'') {
                // skip a string, which ends with the line at the latest
                for (i++; i < text.length() && text.charAt(i) != c; i++) {
                    if (text.charAt(i) == '\\') {
                        i++;
                    }
                }
            } else if ("([{".indexOf(c) >= 0) {
                open.push(lineStart + i);
            } else if (")]}".indexOf(c) >= 0) {
                char wanted = "([{".charAt(")]}".indexOf(c));
                if (open.isEmpty()) {
                    markers.add(Marker.error(lineStart + i, lineStart + i + 1, "'" + c + "' has no opening bracket"));
                } else {
                    char found = model.charAt(open.pop());
                    if (found != wanted) {
                        markers.add(Marker.error(lineStart + i, lineStart + i + 1,
                                "'" + c + "' does not match '" + found + "'"));
                    }
                }
            }
        }
    }
}
