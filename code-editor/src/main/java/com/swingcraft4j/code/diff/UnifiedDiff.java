package com.swingcraft4j.code.diff;

import java.util.ArrayList;
import java.util.List;

/**
 * Writes the difference of two texts in the unified format that {@code git diff} prints: a
 * header with the names of the two files, then a hunk for each place where they differ, the
 * lines taken away marked with {@code -}, those put in with {@code +} and a few unchanged lines
 * around them for context.
 * <pre>{@code
 * String patch = UnifiedDiff.of(original, revised)
 *         .names("a/Main.java", "b/Main.java")
 *         .context(3)
 *         .format();
 * viewer.setLanguage(Languages.byId("diff").orElse(null));
 * viewer.setText(patch);
 * }</pre>
 * A line ends at {@code \n}. A line break of {@code \r\n} is a part of the line, so two texts
 * that differ only in their line breaks differ in every line, as they do to git. A text that
 * does not end with a line break gets {@code \ No newline at end of file} after its last line.
 */
public final class UnifiedDiff {

    private static final String NO_NEWLINE = "\\ No newline at end of file";

    private final String original;
    private final String revised;
    private String originalName = "a";
    private String revisedName = "b";
    private int context = 3;

    private UnifiedDiff(String original, String revised) {
        this.original = original;
        this.revised = revised;
    }

    /** The difference that turns {@code original} into {@code revised}. */
    public static UnifiedDiff of(String original, String revised) {
        return new UnifiedDiff(original, revised);
    }

    /** The names of the two files, shown in the header after {@code ---} and {@code +++}; {@code a} and {@code b} by default. */
    public UnifiedDiff names(String originalName, String revisedName) {
        this.originalName = originalName;
        this.revisedName = revisedName;
        return this;
    }

    /** How many unchanged lines are shown before and after each change; 3 by default, as in git. */
    public UnifiedDiff context(int lines) {
        if (lines < 0) {
            throw new IllegalArgumentException("negative context: " + lines);
        }
        context = lines;
        return this;
    }

    /** The diff as text, each line ended with {@code \n}; empty where the texts are equal. */
    public String format() {
        List<String> a = lines(original);
        List<String> b = lines(revised);
        List<Change> changes = Diff.compare(a, b);
        if (changes.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        out.append("--- ").append(originalName).append('\n');
        out.append("+++ ").append(revisedName).append('\n');
        int first = 0;
        while (first < changes.size()) {
            // changes whose context would touch or overlap go in one hunk
            int last = first;
            while (last + 1 < changes.size()
                    && changes.get(last + 1).originalStart() - changes.get(last).originalEnd() <= 2 * context) {
                last++;
            }
            hunk(out, a, b, changes.subList(first, last + 1));
            first = last + 1;
        }
        return out.toString();
    }

    private void hunk(StringBuilder out, List<String> a, List<String> b, List<Change> changes) {
        Change first = changes.get(0);
        Change last = changes.get(changes.size() - 1);
        int aStart = Math.max(0, first.originalStart() - context);
        int aEnd = Math.min(a.size(), last.originalEnd() + context);
        // the lines of context are the same in both, so the revised range is shifted by as much
        int bStart = first.revisedStart() - (first.originalStart() - aStart);
        int bEnd = last.revisedEnd() + (aEnd - last.originalEnd());
        out.append("@@ -").append(range(aStart, aEnd - aStart))
                .append(" +").append(range(bStart, bEnd - bStart)).append(" @@\n");
        int i = aStart;
        for (Change change : changes) {
            for (; i < change.originalStart(); i++) {
                line(out, ' ', a.get(i));
            }
            for (; i < change.originalEnd(); i++) {
                line(out, '-', a.get(i));
            }
            for (int j = change.revisedStart(); j < change.revisedEnd(); j++) {
                line(out, '+', b.get(j));
            }
        }
        for (; i < aEnd; i++) {
            line(out, ' ', a.get(i));
        }
    }

    /** The start and length of a hunk as git writes them: from 1, the length left out where it is 1, and the line before where it is 0. */
    private static String range(int start, int length) {
        if (length == 1) {
            return String.valueOf(start + 1);
        }
        return (length == 0 ? start : start + 1) + "," + length;
    }

    private static void line(StringBuilder out, char mark, String line) {
        out.append(mark);
        if (line.endsWith("\n")) {
            out.append(line);
        } else {
            out.append(line).append('\n').append(NO_NEWLINE).append('\n');
        }
    }

    /** The lines of a text, each with the {@code \n} that ends it, so that a last line without one differs from one with it. */
    static List<String> lines(String text) {
        List<String> lines = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = text.indexOf('\n', start);
            end = end < 0 ? text.length() : end + 1;
            lines.add(text.substring(start, end));
            start = end;
        }
        return lines;
    }
}
