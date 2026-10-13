package com.swingcraft4j.code.lexer;

import com.swingcraft4j.code.format.FormatOptions;
import com.swingcraft4j.code.format.Formatter;
import com.swingcraft4j.code.lexer.OverlayLanguage.Overlay;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Formats the text of an {@link OverlayLanguage} with a formatter of its base language, which
 * knows nothing of the syntax laid over it and would take {@code {{name}}} for two objects.
 * So each match of an overlay pattern is put away first, with a word in its place that the
 * formatter leaves whole, and is put back into the text that the formatter returns.
 */
final class OverlayFormatter implements Formatter {

    /** A match of an overlay pattern: where it lies, and the pattern it is of, by its place in the list. */
    private record Match(int start, int end, int pattern) {
    }

    private final Formatter formatter;
    private final List<Overlay> overlays;

    OverlayFormatter(Formatter formatter, List<Overlay> overlays) {
        this.formatter = formatter;
        this.overlays = overlays;
    }

    @Override
    public String format(String text, FormatOptions options) {
        List<Match> matches = matches(text);
        if (matches.isEmpty()) {
            return formatter.format(text, options);
        }
        // a word that the text does not have, so that no other part of it is taken for a match
        String word = "__overlay";
        while (text.contains(word)) {
            word += "_";
        }
        StringBuilder plain = new StringBuilder(text.length() + matches.size() * 8);
        int position = 0;
        for (int i = 0; i < matches.size(); i++) {
            Match match = matches.get(i);
            plain.append(text, position, match.start()).append(word).append('_').append(i).append("__");
            position = match.end();
        }
        plain.append(text, position, text.length());

        String given = plain.toString();
        String formatted = formatter.format(given, options);
        if (formatted == given || formatted.equals(given)) {
            return text;
        }
        Matcher words = Pattern.compile(Pattern.quote(word) + "_(\\d+)__").matcher(formatted);
        StringBuilder out = new StringBuilder(formatted.length() + text.length() - given.length());
        int next = 0;
        position = 0;
        while (words.find()) {
            // the formatter moved, changed or lost a word: the text stays as it was
            if (next == matches.size() || !words.group(1).equals(String.valueOf(next))) {
                return text;
            }
            Match match = matches.get(next++);
            out.append(formatted, position, words.start()).append(text, match.start(), match.end());
            position = words.end();
        }
        if (next != matches.size()) {
            return text;
        }
        return out.append(formatted, position, formatted.length()).toString();
    }

    /**
     * The matches of the overlay patterns as the lexer finds them: each within one line, and
     * of two that overlap the one that starts first, or the one whose pattern was added first.
     */
    private List<Match> matches(String text) {
        List<Match> found = new ArrayList<>();
        for (int p = 0; p < overlays.size(); p++) {
            Matcher matcher = overlays.get(p).pattern().matcher(text);
            int lineStart = 0;
            while (lineStart <= text.length()) {
                int lineEnd = lineStart;
                while (lineEnd < text.length() && text.charAt(lineEnd) != '\n' && text.charAt(lineEnd) != '\r') {
                    lineEnd++;
                }
                matcher.region(lineStart, lineEnd);
                while (matcher.find()) {
                    if (matcher.end() > matcher.start()) {
                        found.add(new Match(matcher.start(), matcher.end(), p));
                    }
                }
                lineStart = lineEnd + 1;
            }
        }
        found.sort(Comparator.comparingInt(Match::start).thenComparingInt(Match::pattern));
        List<Match> matches = new ArrayList<>(found.size());
        int end = 0;
        for (Match match : found) {
            if (match.start() >= end) {
                matches.add(match);
                end = match.end();
            }
        }
        return matches;
    }
}
