package com.swingcraft4j.code.folding;

import com.swingcraft4j.code.layout.RowIndex;
import com.swingcraft4j.code.lexer.BracketMatcher;
import com.swingcraft4j.code.lexer.IncrementalLineStates;
import com.swingcraft4j.code.lexer.Language;
import com.swingcraft4j.code.lexer.RuleLanguage;
import com.swingcraft4j.code.text.ArrayTextModel;
import com.swingcraft4j.code.text.TextModel;
import org.junit.jupiter.api.Test;

import java.util.BitSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IndentFoldingTest {

    private static final String CODE = String.join("\n",
            "class A {",            // 0
            "    void run() {",     // 1
            "        if (x) {",     // 2
            "",                     // 3
            "            y();",     // 4
            "        }",            // 5
            "\tint z;",             // 6: one tab is as deep as four spaces, so no deeper than line 1
            "    }",                // 7
            "",                     // 8
            "}",                    // 9
            "");                    // 10

    private final TextModel model = ArrayTextModel.of(CODE);

    @Test
    void findsWhereRegionsStartAndEnd() {
        assertTrue(IndentFolding.isFoldStart(model, 0, 4));
        assertTrue(IndentFolding.isFoldStart(model, 1, 4));
        assertTrue(IndentFolding.isFoldStart(model, 2, 4), "a blank line before the body does not matter");
        assertFalse(IndentFolding.isFoldStart(model, 3, 4));
        assertFalse(IndentFolding.isFoldStart(model, 4, 4));
        assertFalse(IndentFolding.isFoldStart(model, 7, 4));
        assertFalse(IndentFolding.isFoldStart(model, 9, 4));

        assertEquals(7, IndentFolding.foldEnd(model, 0, 4), "trailing blank lines stay outside");
        assertEquals(5, IndentFolding.foldEnd(model, 1, 4), "ends before the line that is no deeper");
        assertEquals(4, IndentFolding.foldEnd(model, 2, 4), "the closing brace stays visible");
        assertEquals(4, IndentFolding.foldEnd(model, 4, 4));
    }

    @Test
    void findsTheRegionAroundALine() {
        assertEquals(2, IndentFolding.enclosingFoldStart(model, 4, 4));
        assertEquals(1, IndentFolding.enclosingFoldStart(model, 2, 4));
        assertEquals(2, IndentFolding.enclosingFoldStart(model, 3, 4), "a blank line belongs to what is above it");
        assertEquals(-1, IndentFolding.enclosingFoldStart(model, 0, 4));
    }

    @Test
    void hiddenLinesTakeNoRows() {
        RowIndex rows = new RowIndex(model, 4);
        BitSet hidden = new BitSet();
        hidden.set(2, 7); // lines 2 to 6
        rows.setHiddenLines(hidden);
        assertEquals(11 - 5, rows.rowCount());
        assertEquals(1, rows.lineAtRow(1));
        assertEquals(7, rows.lineAtRow(2), "the row after the fold belongs to the first visible line");
        assertEquals(2, rows.firstRow(7));
        assertEquals(0, rows.rowsOf(4));
        assertTrue(rows.isHidden(6));
        assertFalse(rows.isHidden(7));

        rows.setWrapCells(8); // lines 0 and 1 are 9 and 16 cells: two rows each
        assertEquals(11 - 5 + 2, rows.rowCount());
        assertEquals(1, rows.lineAtRow(3));
        assertEquals(7, rows.lineAtRow(4));

        rows.setHiddenLines(null);
        rows.setWrapCells(0);
        assertEquals(11, rows.rowCount());
        assertEquals(4, rows.lineAtRow(4));
    }

    @Test
    void matchesBracketsOutsideStringsAndComments() {
        Language language = RuleLanguage.builder("toy", "Toy").string("\"").lineComment("//").blockComment("/*", "*/").build();
        String text = "f(a[1], \")\") { // }\n  g(/* ( */ x);\n}";
        TextModel code = ArrayTextModel.of(text);
        IncrementalLineStates states = new IncrementalLineStates(code, language.createLexer());
        states.process(Long.MAX_VALUE);
        BracketMatcher matcher = new BracketMatcher();

        int open = text.indexOf('(');
        assertEquals(text.indexOf(") {"), matcher.match(code, open, language.createLexer(), states, 100));
        assertEquals(open, matcher.match(code, text.indexOf(") {"), language.createLexer(), states, 100));
        assertEquals(text.indexOf(']'), matcher.match(code, text.indexOf('['), language.createLexer(), states, 100));
        assertEquals(text.length() - 1, matcher.match(code, text.indexOf('{'), language.createLexer(), states, 100));
        assertEquals(text.indexOf('{'), matcher.match(code, text.length() - 1, language.createLexer(), states, 100));
        assertEquals(text.indexOf(");"), matcher.match(code, text.indexOf("g(") + 1, language.createLexer(), states, 100));

        assertEquals(-1, matcher.match(code, text.indexOf("\")\"") + 1, language.createLexer(), states, 100), "inside a string");
        assertEquals(-1, matcher.match(code, 0, language.createLexer(), states, 100), "not a bracket");
        assertEquals(-1, matcher.match(code, text.indexOf('{'), language.createLexer(), states, 1), "partner out of reach");
        // without a lexer every bracket counts, so the brace in the comment is taken as the partner
        assertEquals(text.indexOf("}"), matcher.match(code, text.indexOf('{'), null, null, 100));
    }
}
