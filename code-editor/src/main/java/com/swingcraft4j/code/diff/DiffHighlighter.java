package com.swingcraft4j.code.diff;

import com.swingcraft4j.code.theme.CodeTheme;
import com.swingcraft4j.code.viewer.Highlight;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lays the backgrounds of a diff on viewers, as a review of code on the web shows one: a line
 * taken away on red, a line put in on green, and the chars that changed within such a line on
 * a stronger red or green.
 * <p>
 * It does so for the two versions side by side, each in a viewer of its own:
 * <pre>{@code
 * DiffHighlighter highlighter = new DiffHighlighter().granularity(Granularity.WORD);
 * DiffHighlighter.Sides sides = highlighter.compare(originalText, revisedText, theme);
 * originalViewer.setHighlights(sides.original());
 * revisedViewer.setHighlights(sides.revised());
 * }</pre>
 * and for a unified diff in one viewer, whoever wrote it:
 * <pre>{@code
 * output.setText(patch);
 * output.setHighlights(highlighter.patch(patch, theme));
 * }</pre>
 * Within a change, the first line taken away is compared with the first line put in, the
 * second with the second, and so on; a line left over has its line background only, and so
 * does a pair that has too little in common, see {@link #minSimilarity}. The
 * colours are those of the theme, {@link CodeTheme#insertedLineBackground()} and the three
 * others, which a theme sets with its builder. Highlights are set again after the theme is
 * changed, as they hold the colours they were made with.
 */
public final class DiffHighlighter {

    /** The highlights for the original and for the revised version, each to show in a viewer of its own. */
    public record Sides(List<Highlight> original, List<Highlight> revised) {
    }

    private static final Pattern HUNK = Pattern.compile("@@ -\\d+(?:,(\\d+))? \\+\\d+(?:,(\\d+))? @@");

    private Granularity granularity = Granularity.CHAR;
    private boolean lineBackgrounds = true;
    private double minSimilarity = 0.5;

    /** How finely the changes within a changed line are marked; {@link Granularity#CHAR} by default, and {@link Granularity#LINE} marks none. */
    public DiffHighlighter granularity(Granularity granularity) {
        this.granularity = granularity;
        return this;
    }

    public Granularity getGranularity() {
        return granularity;
    }

    /** Whether the lines taken away and put in get a background from edge to edge; true by default. */
    public DiffHighlighter lineBackgrounds(boolean lineBackgrounds) {
        this.lineBackgrounds = lineBackgrounds;
        return this;
    }

    public boolean isLineBackgrounds() {
        return lineBackgrounds;
    }

    /**
     * How much of two paired lines must be the same for what changed within them to be marked,
     * from 0 to 1; 0.5 by default. Below it they are different lines rather than one line
     * edited, and marks scattered over them would tell nothing; they keep their line
     * backgrounds. 0 marks the changes within every pair.
     */
    public DiffHighlighter minSimilarity(double minSimilarity) {
        if (minSimilarity < 0 || minSimilarity > 1) {
            throw new IllegalArgumentException("not between 0 and 1: " + minSimilarity);
        }
        this.minSimilarity = minSimilarity;
        return this;
    }

    public double getMinSimilarity() {
        return minSimilarity;
    }

    /** The highlights of the two versions of a text, compared line by line. */
    public Sides compare(String original, String revised, CodeTheme theme) {
        List<String> a = UnifiedDiff.lines(original);
        List<String> b = UnifiedDiff.lines(revised);
        int[] startsA = starts(a);
        int[] startsB = starts(b);
        List<Highlight> left = new ArrayList<>();
        List<Highlight> right = new ArrayList<>();
        for (Change change : Diff.compare(a, b)) {
            if (lineBackgrounds) {
                if (change.originalCount() > 0) {
                    left.add(Highlight.line(startsA[change.originalStart()],
                            startsA[change.originalEnd() - 1] + content(a.get(change.originalEnd() - 1)).length(),
                            theme.deletedLineBackground()));
                }
                if (change.revisedCount() > 0) {
                    right.add(Highlight.line(startsB[change.revisedStart()],
                            startsB[change.revisedEnd() - 1] + content(b.get(change.revisedEnd() - 1)).length(),
                            theme.insertedLineBackground()));
                }
            }
            int pairs = Math.min(change.originalCount(), change.revisedCount());
            for (int k = 0; k < pairs; k++) {
                int i = change.originalStart() + k;
                int j = change.revisedStart() + k;
                inline(content(a.get(i)), startsA[i], content(b.get(j)), startsB[j], theme, left, right);
            }
        }
        return new Sides(left, right);
    }

    /**
     * The highlights of a unified diff, as {@link UnifiedDiff} or {@code git diff} writes one,
     * to show in the viewer that shows its text. The lines of each hunk are counted, as its
     * header gives them, so a line taken away that starts with {@code ---} is still one.
     */
    public List<Highlight> patch(String diff, CodeTheme theme) {
        List<Highlight> highlights = new ArrayList<>();
        // the lines taken away and put in, by their offsets, until the change they make is over
        List<Integer> removed = new ArrayList<>();
        List<Integer> added = new ArrayList<>();
        int originalLeft = 0;
        int revisedLeft = 0;
        int next = 0;
        for (String line : UnifiedDiff.lines(diff)) {
            int offset = next;
            next += line.length();
            String text = content(line);
            char mark = text.isEmpty() ? ' ' : text.charAt(0);
            boolean inHunk = originalLeft > 0 || revisedLeft > 0;
            if (inHunk && mark == '\\') {
                // the note that a line has no line break belongs to the line before
                continue;
            }
            if (inHunk && mark == '-' && originalLeft > 0) {
                if (!added.isEmpty()) {
                    flush(diff, removed, added, theme, highlights);
                }
                removed.add(offset);
                originalLeft--;
            } else if (inHunk && mark == '+' && revisedLeft > 0) {
                added.add(offset);
                revisedLeft--;
            } else {
                flush(diff, removed, added, theme, highlights);
                if (inHunk && mark == ' ') {
                    originalLeft = Math.max(0, originalLeft - 1);
                    revisedLeft = Math.max(0, revisedLeft - 1);
                } else {
                    Matcher hunk = HUNK.matcher(text);
                    if (hunk.lookingAt()) {
                        originalLeft = count(hunk.group(1));
                        revisedLeft = count(hunk.group(2));
                    } else {
                        originalLeft = 0;
                        revisedLeft = 0;
                    }
                }
            }
        }
        flush(diff, removed, added, theme, highlights);
        return highlights;
    }

    /** Highlights the lines of one change of a unified diff, and pairs them to mark what changed within them. */
    private void flush(String diff, List<Integer> removed, List<Integer> added, CodeTheme theme, List<Highlight> highlights) {
        if (lineBackgrounds) {
            for (int start : removed) {
                highlights.add(Highlight.line(start, start + lineLength(diff, start), theme.deletedLineBackground()));
            }
            for (int start : added) {
                highlights.add(Highlight.line(start, start + lineLength(diff, start), theme.insertedLineBackground()));
            }
        }
        List<Highlight> inline = new ArrayList<>();
        for (int k = 0; k < Math.min(removed.size(), added.size()); k++) {
            int a = removed.get(k);
            int b = added.get(k);
            // the text after the mark
            inline(diff.substring(a + 1, a + lineLength(diff, a)), a + 1,
                    diff.substring(b + 1, b + lineLength(diff, b)), b + 1, theme, inline, inline);
        }
        highlights.addAll(inline);
        removed.clear();
        added.clear();
    }

    /** Marks the chars that changed between a line taken away and the line put in for it. */
    private void inline(String original, int originalOffset, String revised, int revisedOffset, CodeTheme theme,
                        List<Highlight> left, List<Highlight> right) {
        if (granularity == Granularity.LINE) {
            return;
        }
        List<Change> changes = InlineDiff.compare(original, revised, granularity);
        int changed = 0;
        for (Change change : changes) {
            changed += change.originalCount() + change.revisedCount();
        }
        int total = original.length() + revised.length();
        if (total > 0 && (double) (total - changed) / total < minSimilarity) {
            // two lines that have little in common: marks within them would be noise
            return;
        }
        Color deleted = theme.deletedTextBackground();
        Color inserted = theme.insertedTextBackground();
        for (Change change : changes) {
            if (change.originalCount() > 0) {
                left.add(Highlight.text(originalOffset + change.originalStart(), originalOffset + change.originalEnd(), deleted));
            }
            if (change.revisedCount() > 0) {
                right.add(Highlight.text(revisedOffset + change.revisedStart(), revisedOffset + change.revisedEnd(), inserted));
            }
        }
    }

    private static int count(String group) {
        if (group == null) {
            return 1;
        }
        try {
            return Integer.parseInt(group);
        } catch (NumberFormatException e) {
            // too many to count: the rest of the text is the hunk
            return Integer.MAX_VALUE;
        }
    }

    /** The length of the line that starts at the offset, without its line break. */
    private static int lineLength(String text, int start) {
        int end = text.indexOf('\n', start);
        return (end < 0 ? text.length() : end) - start;
    }

    /** A line without the {@code \n} that ends it. */
    private static String content(String line) {
        return line.endsWith("\n") ? line.substring(0, line.length() - 1) : line;
    }

    private static int[] starts(List<String> lines) {
        int[] starts = new int[lines.size() + 1];
        for (int i = 0; i < lines.size(); i++) {
            starts[i + 1] = starts[i] + lines.get(i).length();
        }
        return starts;
    }
}
