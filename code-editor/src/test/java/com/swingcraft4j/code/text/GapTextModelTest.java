package com.swingcraft4j.code.text;

import com.swingcraft4j.code.layout.RowIndex;
import com.swingcraft4j.code.lexer.IncrementalLineStates;
import com.swingcraft4j.code.lexer.Language;
import com.swingcraft4j.code.lexer.Lexer;
import com.swingcraft4j.code.lexer.RuleLanguage;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Checks the editable model, and the indexes that follow it, against the same structures
 * built from scratch after every one of many random edits.
 */
class GapTextModelTest {

    private static final String ALPHABET = "ab \t\n\n\r{-}你";

    private static String randomText(Random random, int maxLength) {
        StringBuilder text = new StringBuilder();
        for (int i = random.nextInt(maxLength + 1); i > 0; i--) {
            text.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return text.toString();
    }

    /** Applies one random replacement to both the model and the plain copy of its text. */
    private static void randomEdit(Random random, GapTextModel model, StringBuilder text) {
        int start = random.nextInt(text.length() + 1);
        int end = Math.min(text.length(), start + (random.nextInt(4) == 0 ? random.nextInt(12) : 0));
        String inserted = randomText(random, random.nextInt(3) == 0 ? 12 : 2);
        model.replace(start, end, inserted);
        text.replace(start, end, inserted);
    }

    private static void assertSameLines(TextModel expected, TextModel actual) {
        assertEquals(expected.length(), actual.length());
        assertEquals(expected.getText(0, expected.length()), actual.getText(0, actual.length()));
        assertEquals(expected.lineCount(), actual.lineCount());
        for (int line = 0; line < expected.lineCount(); line++) {
            assertEquals(expected.lineStart(line), actual.lineStart(line), "start of line " + line);
            assertEquals(expected.lineEnd(line), actual.lineEnd(line), "end of line " + line);
        }
        for (int offset = 0; offset <= expected.length(); offset++) {
            assertEquals(expected.lineOfOffset(offset), actual.lineOfOffset(offset), "line of offset " + offset);
        }
    }

    @Test
    void splitsLinesLikeTheImmutableModel() {
        String text = "one\ntwo\r\nthree\rfour\n";
        assertSameLines(ArrayTextModel.of(text), new GapTextModel(text));
        assertSameLines(ArrayTextModel.of(""), new GapTextModel());
        assertSameLines(ArrayTextModel.of(text), GapTextModel.copyOf(ArrayTextModel.of(text)));
    }

    @Test
    void joinsAndSplitsCarriageReturnLineFeedPairs() {
        GapTextModel model = new GapTextModel("a\rb");
        model.replace(2, 2, "\n"); // a\r\nb: the \n completes the terminator before it
        assertEquals(2, model.lineCount());
        model.replace(2, 2, "x"); // a\rx\nb: the pair is split again
        assertEquals(3, model.lineCount());
        model.replace(2, 3, ""); // a\r\nb
        assertSameLines(ArrayTextModel.of("a\r\nb"), model);
        model.replace(1, 2, ""); // a\nb
        assertSameLines(ArrayTextModel.of("a\nb"), model);
    }

    @Test
    void rejectsRangesOutsideTheText() {
        GapTextModel model = new GapTextModel("abc");
        assertThrows(IndexOutOfBoundsException.class, () -> model.replace(2, 4, ""));
        assertThrows(IndexOutOfBoundsException.class, () -> model.replace(2, 1, ""));
    }

    @Test
    void staysConsistentUnderRandomEdits() {
        for (int seed = 0; seed < 40; seed++) {
            Random random = new Random(seed);
            StringBuilder text = new StringBuilder(randomText(random, 60));
            GapTextModel model = new GapTextModel(text);
            List<TextChange> changes = new ArrayList<>();
            model.addTextListener(changes::add);
            for (int step = 0; step < 150; step++) {
                TextModel before = ArrayTextModel.of(text);
                changes.clear();
                randomEdit(random, model, text);
                TextModel expected = ArrayTextModel.of(text);
                assertSameLines(expected, model);
                if (changes.isEmpty()) {
                    assertEquals(before.getText(0, before.length()), text.toString());
                    continue;
                }
                // the change must describe which lines were replaced
                TextChange change = changes.get(0);
                assertEquals(before.lineCount() - change.removedLines() + change.insertedLines(), expected.lineCount());
                for (int line = 0; line <= change.firstLine(); line++) {
                    assertEquals(before.lineStart(line), expected.lineStart(line));
                }
                int delta = change.insertedLength() - change.removedLength();
                int oldTail = change.firstLine() + change.removedLines() + 1;
                int newTail = change.firstLine() + change.insertedLines() + 1;
                for (int i = 0; oldTail + i < before.lineCount(); i++) {
                    assertEquals(before.lineStart(oldTail + i) + delta, expected.lineStart(newTail + i));
                }
            }
        }
    }

    @Test
    void rowIndexFollowsRandomEdits() {
        for (int seed = 0; seed < 30; seed++) {
            Random random = new Random(seed);
            // some runs start without tabs or wide chars, so the index starts in its compact form
            StringBuilder text = new StringBuilder(seed % 2 == 0 ? "plain line\nand another one\n" : randomText(random, 60));
            GapTextModel model = new GapTextModel(text);
            RowIndex rows = new RowIndex(model, 4);
            boolean atWords = seed % 3 == 0;
            rows.setWordWrap(atWords);
            rows.setWrapCells(5);
            model.addTextListener(c -> rows.linesReplaced(c.firstLine(), c.removedLines(), c.insertedLines()));
            for (int step = 0; step < 150; step++) {
                randomEdit(random, model, text);
                if (step % 37 == 0) {
                    rows.setWrapCells(random.nextInt(3) == 0 ? 0 : 3 + random.nextInt(6));
                }
                RowIndex expected = new RowIndex(ArrayTextModel.of(text), 4);
                expected.setWordWrap(atWords);
                expected.setWrapCells(rows.wrapCells());
                assertEquals(expected.rowCount(), rows.rowCount());
                assertEquals(expected.maxCells(), rows.maxCells());
                for (int line = 0; line < model.lineCount(); line++) {
                    assertEquals(expected.lineCells(line), rows.lineCells(line), "cells of line " + line);
                    assertEquals(expected.isPlain(line), rows.isPlain(line), "plainness of line " + line);
                    assertEquals(expected.firstRow(line), rows.firstRow(line), "first row of line " + line);
                }
                for (int row = 0; row < rows.rowCount(); row++) {
                    assertEquals(expected.lineAtRow(row), rows.lineAtRow(row));
                }
            }
        }
    }

    @Test
    void lineStatesFollowRandomEdits() {
        Language language = RuleLanguage.builder("toy", "Toy").blockComment("{-", "-}").build();
        for (int seed = 0; seed < 30; seed++) {
            Random random = new Random(seed);
            StringBuilder text = new StringBuilder(randomText(random, 80));
            GapTextModel model = new GapTextModel(text);
            IncrementalLineStates states = new IncrementalLineStates(model, language.createLexer());
            model.addTextListener(c -> states.linesReplaced(c.firstLine(), c.removedLines(), c.insertedLines()));
            for (int step = 0; step < 150; step++) {
                randomEdit(random, model, text);
                // often leave work pending, so that changes pile up on unfinished ones
                if (random.nextInt(3) != 0) {
                    continue;
                }
                while (states.process(random.nextBoolean() ? 0 : Long.MAX_VALUE)) {
                    // a zero budget still makes progress
                }
                Lexer lexer = language.createLexer();
                int state = Lexer.INITIAL_STATE;
                for (int line = 0; line < model.lineCount(); line++) {
                    assertEquals(state, states.startState(line), "seed " + seed + " step " + step + " line " + line);
                    char[] chars = model.getText(model.lineStart(line), model.lineEnd(line)).toCharArray();
                    state = lexer.tokenize(chars, 0, chars.length, state, (start, length, type) -> {
                    });
                }
            }
        }
    }
}
