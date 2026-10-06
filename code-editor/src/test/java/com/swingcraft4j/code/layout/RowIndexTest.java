package com.swingcraft4j.code.layout;

import com.swingcraft4j.code.text.ArrayTextModel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RowIndexTest {

    @Test
    void withoutWrappingARowIsALine() {
        RowIndex rows = new RowIndex(ArrayTextModel.of("a\nbbbbbbbbbbbb\nc"), 4);
        assertEquals(3, rows.rowCount());
        assertEquals(12, rows.maxCells());
        assertEquals(2, rows.firstRow(2));
        assertEquals(1, rows.lineAtRow(1));
    }

    @Test
    void wrapsLongLinesIntoSeveralRows() {
        // 3, 10, 0 and 25 cells at 10 cells per row: 1 + 1 + 1 + 3 rows
        RowIndex rows = new RowIndex(ArrayTextModel.of("abc\n0123456789\n\n" + "x".repeat(25)), 4);
        rows.setWrapCells(10);
        assertEquals(6, rows.rowCount());
        assertEquals(3, rows.firstRow(3));
        assertEquals(3, rows.rowsOf(3));
        assertEquals(1, rows.rowsOf(2));
        assertEquals(2, rows.lineAtRow(2));
        assertEquals(3, rows.lineAtRow(3));
        assertEquals(3, rows.lineAtRow(5));

        rows.setWrapCells(5);
        assertEquals(1 + 2 + 1 + 5, rows.rowCount());

        rows.setWrapCells(0);
        assertEquals(4, rows.rowCount());
        assertEquals(3, rows.lineAtRow(3));
    }

    @Test
    void measuresTabsAndWideChars() {
        RowIndex rows = new RowIndex(ArrayTextModel.of("plain\n\tx\na\tb\n你好"), 4);
        assertTrue(rows.isPlain(0));
        assertFalse(rows.isPlain(1));
        assertEquals(5, rows.lineCells(0));
        assertEquals(5, rows.lineCells(1));
        assertEquals(5, rows.lineCells(2));
        assertEquals(4, rows.lineCells(3));
    }

    @Test
    void takesAClusterAsOneUnit() {
        // Khmer: a consonant with a vowel sign, then a consonant with a letter written below it and a vowel sign
        char[] khmer = "\u179F\u17BD\u179F\u17D2\u178F\u17B8 a".toCharArray();
        assertEquals(2, Cells.clusterEnd(khmer, 0, khmer.length));
        assertEquals(6, Cells.clusterEnd(khmer, 2, khmer.length));
        assertEquals(7, Cells.clusterEnd(khmer, 6, khmer.length));
        char[] other = "e\u0301\uD83D\uDE00\u4F60\u597D".toCharArray();
        assertEquals(2, Cells.clusterEnd(other, 0, other.length), "a letter and its combining mark");
        assertEquals(4, Cells.clusterEnd(other, 2, other.length), "a surrogate pair");
        assertEquals(5, Cells.clusterEnd(other, 4, other.length), "ideographs stand alone");

        // with a measure that makes every cluster three cells wide
        CellMeasure three = (text, start, end) -> 3;
        assertEquals(3 + 3 + 1 + 1, Cells.count(khmer, 0, khmer.length, 4, three));
        assertEquals(0, Cells.indexAtCell(khmer, 0, khmer.length, 2, 4, three));
        assertEquals(2, Cells.indexAtCell(khmer, 0, khmer.length, 3, 4, three), "the start of the cluster covering the cell");
        assertEquals(2, Cells.indexAtCell(khmer, 0, khmer.length, 5, 4, three));
        assertEquals(6, Cells.indexAtCell(khmer, 0, khmer.length, 6, 4, three));

        RowIndex rows = new RowIndex(ArrayTextModel.of(new String(khmer)), 4, three);
        assertEquals(8, rows.lineCells(0));
        assertFalse(rows.isPlain(0));
    }

    @Test
    void breaksLinesAtWordsWhenAsked() {
        RowIndex rows = new RowIndex(ArrayTextModel.of("the quick brown fox\nabcdefghijklmnop qr\nshort"), 4);
        rows.setWrapCells(12);
        assertEquals(2 + 2 + 1, rows.rowCount(), "after any char: 19 cells in rows of 12");

        rows.setWordWrap(true);
        assertEquals(2 + 2 + 1, rows.rowCount());
        double[] starts = rows.wordRowStarts(0, new double[1]);
        assertEquals(0, starts[0]);
        assertEquals(10, starts[1], "\"brown\" does not fit after \"the quick \", so the row ends after the blank");
        starts = rows.wordRowStarts(1, starts);
        assertEquals(12, starts[1], "a word longer than a row is broken where the row ends");

        rows.setWrapCells(5);
        assertEquals(5 + 4 + 1, rows.rowCount());
        starts = rows.wordRowStarts(0, starts);
        // "the " | "quick" | " " | "brown" | " fox": a blank that does not fit starts the next row
        assertEquals(java.util.List.of(0.0, 4.0, 9.0, 10.0, 15.0),
                java.util.Arrays.stream(starts, 0, rows.rowsOf(0)).boxed().toList());

        rows.setWordWrap(false);
        assertEquals(4 + 4 + 1, rows.rowCount());
    }

    @Test
    void findsTheCharCoveringACell() {
        char[] text = "a\tb你c".toCharArray();
        assertEquals(0, Cells.indexAtCell(text, 0, text.length, 0, 4));
        assertEquals(1, Cells.indexAtCell(text, 0, text.length, 1, 4));
        assertEquals(1, Cells.indexAtCell(text, 0, text.length, 3, 4));
        assertEquals(2, Cells.indexAtCell(text, 0, text.length, 4, 4));
        assertEquals(3, Cells.indexAtCell(text, 0, text.length, 6, 4));
        assertEquals(4, Cells.indexAtCell(text, 0, text.length, 7, 4));
        assertEquals(text.length, Cells.indexAtCell(text, 0, text.length, 8, 4));
    }
}
