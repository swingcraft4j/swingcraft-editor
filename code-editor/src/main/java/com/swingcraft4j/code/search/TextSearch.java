package com.swingcraft4j.code.search;

import com.swingcraft4j.code.text.CharArraySequence;
import com.swingcraft4j.code.text.TextModel;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * A compiled query that finds matches in a {@link TextModel}. Matching works line by line, so
 * a match never spans a line break and {@code ^} and {@code $} anchor to the line. Empty
 * matches are skipped. An instance is used by one thread at a time.
 */
public final class TextSearch {

    /** Offsets of a match in the model. */
    public record Match(int start, int end) {
    }

    @FunctionalInterface
    public interface MatchSink {
        void match(int start, int end);
    }

    private final String query;
    private final boolean wholeWord;
    /** Set for a regex query; literal text is matched by a plain scan, which is much faster. */
    private final Matcher matcher;
    private final CharArraySequence line = new CharArraySequence();
    /** The query in lower and upper case; the same array twice when case matters. */
    private final char[] lower;
    private final char[] upper;
    private char[] buffer = new char[256];
    private int matchEnd;

    /**
     * @param wholeWord match only where the query is not part of a longer word
     * @param regex     treat the query as a regular expression instead of literal text
     * @throws PatternSyntaxException if a regex query is malformed
     */
    public TextSearch(String query, boolean matchCase, boolean wholeWord, boolean regex) {
        if (query.isEmpty()) {
            throw new IllegalArgumentException("query is empty");
        }
        this.query = query;
        this.wholeWord = wholeWord;
        if (regex) {
            String expression = wholeWord ? "\\b(?:" + query + ")\\b" : query;
            int flags = matchCase ? 0 : Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
            matcher = Pattern.compile(expression, flags).matcher(line);
            lower = upper = null;
        } else {
            matcher = null;
            lower = query.toCharArray();
            upper = matchCase ? lower : query.toCharArray();
            if (!matchCase) {
                for (int i = 0; i < lower.length; i++) {
                    lower[i] = Character.toLowerCase(lower[i]);
                    upper[i] = Character.toUpperCase(upper[i]);
                }
            }
        }
    }

    public String query() {
        return query;
    }

    /** Reports every match in {@code text[0, length)}, as indexes into the array. */
    public void findInLine(char[] text, int length, MatchSink sink) {
        for (int start = find(text, length, 0); start >= 0; start = find(text, length, matchEnd)) {
            sink.match(start, matchEnd);
        }
    }

    /** The first match starting at or after the offset, or null. */
    public Match findNext(TextModel model, int from) {
        from = Math.max(0, Math.min(from, model.length()));
        int lineCount = model.lineCount();
        for (int index = model.lineOfOffset(from); index < lineCount; index++) {
            int lineStart = model.lineStart(index);
            int length = load(model, index);
            int start = find(buffer, length, Math.max(0, Math.min(from - lineStart, length)));
            if (start >= 0) {
                return new Match(lineStart + start, lineStart + matchEnd);
            }
        }
        return null;
    }

    /** The last match ending at or before the offset, or null. */
    public Match findPrevious(TextModel model, int before) {
        before = Math.max(0, Math.min(before, model.length()));
        for (int index = model.lineOfOffset(before); index >= 0; index--) {
            int lineStart = model.lineStart(index);
            int length = load(model, index);
            int limit = Math.min(before - lineStart, length);
            int lastStart = -1;
            int lastEnd = -1;
            for (int start = find(buffer, length, 0); start >= 0 && matchEnd <= limit; start = find(buffer, length, matchEnd)) {
                lastStart = start;
                lastEnd = matchEnd;
            }
            if (lastStart >= 0) {
                return new Match(lineStart + lastStart, lineStart + lastEnd);
            }
        }
        return null;
    }

    private int load(TextModel model, int index) {
        int start = model.lineStart(index);
        int length = model.lineLength(index);
        if (length > buffer.length) {
            buffer = new char[Math.max(length, buffer.length * 2)];
        }
        model.getChars(start, start + length, buffer, 0);
        return length;
    }

    /**
     * Finds the first non-empty match in {@code text[0, length)} starting at or after
     * {@code from}. Returns its start and leaves its end in {@link #matchEnd}, or returns -1.
     */
    private int find(char[] text, int length, int from) {
        return matcher != null ? findRegex(text, length, from) : findLiteral(text, length, from);
    }

    private int findRegex(char[] text, int length, int from) {
        line.set(text, length);
        while (from <= length && matcher.find(from)) {
            if (matcher.end() > matcher.start()) {
                matchEnd = matcher.end();
                return matcher.start();
            }
            from = matcher.end() + 1;
        }
        return -1;
    }

    private int findLiteral(char[] text, int length, int from) {
        int size = lower.length;
        char first = lower[0];
        char firstUpper = upper[0];
        candidates:
        for (int i = from, last = length - size; i <= last; i++) {
            char c = text[i];
            if (c != first && c != firstUpper) {
                continue;
            }
            for (int k = 1; k < size; k++) {
                char other = text[i + k];
                if (other != lower[k] && other != upper[k]) {
                    continue candidates;
                }
            }
            if (wholeWord && !isWholeWord(text, length, i, i + size)) {
                continue;
            }
            matchEnd = i + size;
            return i;
        }
        return -1;
    }

    /** A boundary is required only at an end of the query that is itself a word char. */
    private static boolean isWholeWord(char[] text, int length, int start, int end) {
        boolean joinedBefore = start > 0 && isWordChar(text[start]) && isWordChar(text[start - 1]);
        boolean joinedAfter = end < length && isWordChar(text[end - 1]) && isWordChar(text[end]);
        return !joinedBefore && !joinedAfter;
    }

    private static boolean isWordChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }
}
