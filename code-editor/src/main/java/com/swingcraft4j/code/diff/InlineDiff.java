package com.swingcraft4j.code.diff;

import java.util.ArrayList;
import java.util.List;

/**
 * Finds what changed within a line: the chars of the original line that the revised line has
 * replaced. A line diff tells which lines changed; this tells which part of them.
 * <pre>{@code
 * InlineDiff.compare("abc", "abcd", Granularity.CHAR);   // [Change(3, 0, 3, 1)]: the d put in
 * InlineDiff.compare("int count;", "int total;", Granularity.WORD);   // [Change(4, 5, 4, 5)]
 * }</pre>
 * The changes are of the same form as those of {@link Diff}, with indexes of chars. Two lines
 * of more than 20,000 pieces between them are changed as a whole.
 */
public final class InlineDiff {

    /**
     * Lines with more pieces than this between them, such as those of a minified script, are
     * changed as a whole: comparing them piece by piece could take long, and would mark little
     * a reader could follow.
     */
    static final int MAX_UNITS = 20_000;

    private InlineDiff() {
    }

    /** The changes that turn one line into the other, in order; empty where they are equal. */
    public static List<Change> compare(String original, String revised, Granularity granularity) {
        if (original.equals(revised)) {
            return List.of();
        }
        if (granularity == Granularity.LINE) {
            return List.of(new Change(0, original.length(), 0, revised.length()));
        }
        List<String> a = units(original, granularity);
        List<String> b = units(revised, granularity);
        if (a.size() + b.size() > MAX_UNITS) {
            return List.of(new Change(0, original.length(), 0, revised.length()));
        }
        int[] startsA = starts(a);
        int[] startsB = starts(b);
        List<Change> changes = new ArrayList<>();
        for (Change change : Diff.compare(a, b)) {
            int originalStart = startsA[change.originalStart()];
            int revisedStart = startsB[change.revisedStart()];
            changes.add(new Change(originalStart, startsA[change.originalEnd()] - originalStart,
                    revisedStart, startsB[change.revisedEnd()] - revisedStart));
        }
        return changes;
    }

    /** The pieces a line is compared in: each char, or words, runs of blanks and single signs. */
    static List<String> units(String text, Granularity granularity) {
        List<String> units = new ArrayList<>();
        int i = 0;
        while (i < text.length()) {
            int end = i + 1;
            if (granularity == Granularity.WORD) {
                char c = text.charAt(i);
                if (isWordPart(c)) {
                    while (end < text.length() && isWordPart(text.charAt(end))) {
                        end++;
                    }
                } else if (Character.isWhitespace(c)) {
                    while (end < text.length() && Character.isWhitespace(text.charAt(end))) {
                        end++;
                    }
                }
            }
            units.add(text.substring(i, end));
            i = end;
        }
        return units;
    }

    private static boolean isWordPart(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    /** Where each unit starts in the line, and the length of the line after the last. */
    private static int[] starts(List<String> units) {
        int[] starts = new int[units.size() + 1];
        for (int i = 0; i < units.size(); i++) {
            starts[i + 1] = starts[i] + units.get(i).length();
        }
        return starts;
    }
}
