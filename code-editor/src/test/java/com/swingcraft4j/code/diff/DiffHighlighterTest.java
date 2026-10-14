package com.swingcraft4j.code.diff;

import com.swingcraft4j.code.theme.CodeTheme;
import com.swingcraft4j.code.viewer.Highlight;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DiffHighlighterTest {

    private final CodeTheme theme = CodeTheme.light();

    @Test
    void marksTheLinesAndTheCharsThatChangedOnBothSides() {
        DiffHighlighter.Sides sides = new DiffHighlighter().compare("keep\nabc\nkeep\n", "keep\nabcd\nnew\nkeep\n", theme);
        assertEquals(List.of(
                Highlight.line(5, 8, theme.deletedLineBackground())), sides.original());
        assertEquals(List.of(
                Highlight.line(5, 13, theme.insertedLineBackground()),
                Highlight.text(8, 9, theme.insertedTextBackground())), sides.revised());
    }

    @Test
    void withoutLineBackgroundsOrByLineMarksLess() {
        DiffHighlighter onlyChars = new DiffHighlighter().lineBackgrounds(false);
        assertEquals(List.of(Highlight.text(3, 4, theme.insertedTextBackground())),
                onlyChars.compare("abc", "abcd", theme).revised());
        DiffHighlighter onlyLines = new DiffHighlighter().granularity(Granularity.LINE);
        assertEquals(List.of(Highlight.line(0, 4, theme.insertedLineBackground())),
                onlyLines.compare("abc", "abcd", theme).revised());
        DiffHighlighter byWord = new DiffHighlighter().granularity(Granularity.WORD).lineBackgrounds(false);
        assertEquals(List.of(Highlight.text(4, 9, theme.deletedTextBackground())),
                byWord.compare("int count;", "int total;", theme).original());
    }

    @Test
    void marksNothingWithinTwoLinesThatHaveLittleInCommon() {
        DiffHighlighter highlighter = new DiffHighlighter().lineBackgrounds(false);
        assertEquals(List.of(), highlighter.compare("double sum = 0;", "return items.stream()", theme).revised());
        // unless asked to
        highlighter.minSimilarity(0);
        assertEquals(false, highlighter.compare("double sum = 0;", "return items.stream()", theme).revised().isEmpty());
    }

    @Test
    void marksAUnifiedDiffByCountingTheLinesOfEachHunk() {
        String patch = UnifiedDiff.of("a\n-- sql\nb\n", "a\n-- sql!\nb\n").format();
        assertEquals("""
                --- a
                +++ b
                @@ -1,3 +1,3 @@
                 a
                --- sql
                +-- sql!
                 b
                """, patch);
        int removed = patch.indexOf("--- sql");
        int added = patch.indexOf("+-- sql!");
        assertEquals(List.of(
                Highlight.line(removed, removed + 7, theme.deletedLineBackground()),
                Highlight.line(added, added + 8, theme.insertedLineBackground()),
                // after the mark: the ! put in
                Highlight.text(added + 7, added + 8, theme.insertedTextBackground())),
                new DiffHighlighter().patch(patch, theme));
    }
}
