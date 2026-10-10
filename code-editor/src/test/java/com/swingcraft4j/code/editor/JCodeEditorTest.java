package com.swingcraft4j.code.editor;

import com.swingcraft4j.code.languages.json.JsonLanguage;
import com.swingcraft4j.code.languages.markdown.MarkdownLanguage;
import com.swingcraft4j.code.lexer.Language;
import com.swingcraft4j.code.lexer.RuleLanguage;
import com.swingcraft4j.code.marker.Marker;
import com.swingcraft4j.code.text.GapTextModel;
import org.junit.jupiter.api.Test;

import java.awt.Font;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JCodeEditorTest {

    private final JCodeEditor editor = new JCodeEditor();

    private void type(String text) {
        for (char c : text.toCharArray()) {
            editor.processKeyEvent(new KeyEvent(editor, KeyEvent.KEY_TYPED, 0, 0, KeyEvent.VK_UNDEFINED, c));
        }
    }

    private void press(String action) {
        editor.getActionMap().get(action).actionPerformed(null);
    }

    private void press(String action, int times) {
        for (int i = 0; i < times; i++) {
            press(action);
        }
    }

    /** The text with {@code |} at the caret and, when there is a selection, {@code ^} at its anchor. */
    private String state() {
        StringBuilder text = new StringBuilder(editor.getText());
        int anchor = editor.getSelectionAnchor();
        int caret = editor.getCaretPosition();
        if (anchor == caret) {
            return text.insert(caret, '|').toString();
        }
        text.insert(Math.max(anchor, caret), anchor > caret ? '^' : '|');
        return text.insert(Math.min(anchor, caret), anchor > caret ? '|' : '^').toString();
    }

    @Test
    void typesAtTheCaretAndOverTheSelection() {
        type("hello");
        assertEquals("hello|", state());
        editor.select(0, 4);
        type("j");
        assertEquals("j|o", state());
    }

    @Test
    void undoesTypingWordByWord() {
        type("one two three");
        editor.undo();
        assertEquals("one two |", state());
        editor.undo();
        assertEquals("one |", state());
        editor.redo();
        editor.redo();
        assertEquals("one two three|", state());
        assertFalse(editor.canRedo());
        editor.undo();
        type("x");
        assertEquals("one two x|", state());
        assertFalse(editor.canRedo(), "typing after an undo discards what could be redone");
    }

    @Test
    void movingTheCaretStartsANewUndoStep() {
        type("ab");
        press("caret-left");
        press("caret-right");
        type("cd");
        editor.undo();
        assertEquals("ab|", state());
    }

    @Test
    void undoRestoresAReplacedSelection() {
        editor.setText("keep this text");
        editor.select(9, 5);
        type("X");
        assertEquals("keep X| text", state());
        editor.undo();
        assertEquals("keep |this^ text", state());
    }

    @Test
    void enterKeepsTheIndentation() {
        editor.setText("    if (x) {");
        editor.select(12, 12);
        press("insert-break");
        type("y");
        assertEquals("    if (x) {\n        y|", state(), "one level deeper after an opening bracket");
        editor.setText("    done();");
        editor.select(11, 11);
        press("insert-break");
        type("y");
        assertEquals("    done();\n    y|", state());
        editor.setText("a\r\n\tb");
        editor.select(5, 5);
        press("insert-break");
        assertEquals("a\r\n\tb\r\n\t|", state(), "the document's own line terminator is used");
    }

    @Test
    void tabInsertsSpacesToTheNextStop() {
        type("ab");
        press("insert-tab");
        assertEquals("ab  |", state());
        press("insert-tab");
        assertEquals("ab      |", state());
        editor.setTabsToSpaces(false);
        press("insert-tab");
        assertEquals("ab      \t|", state());
    }

    @Test
    void backspaceUnindentsAndJoinsLines() {
        editor.setText("        x");
        editor.select(8, 8);
        press("delete-previous");
        assertEquals("    |x", state());
        editor.setText("a\r\nb");
        editor.select(3, 3);
        press("delete-previous");
        assertEquals("a|b", state());
        editor.select(1, 1);
        press("delete-next", 5);
        assertEquals("a|", state());
        editor.undo();
        assertEquals("a|b", state(), "consecutive deletes are one undo step");
    }

    @Test
    void indentsAndUnindentsTheSelectedLines() {
        editor.setText("a\n  b\n\nc\nd");
        editor.select(0, 7); // up to the start of "c", which is therefore not included
        press("insert-tab");
        assertEquals("^    a\n      b\n\n|c\nd", state());
        press("unindent");
        assertEquals("^a\n  b\n\n|c\nd", state());
        editor.undo();
        editor.undo();
        assertEquals("a\n  b\n\nc\nd", editor.getText());

        editor.select(4, 4);
        press("unindent");
        assertEquals("a\n|b\n\nc\nd", state());
    }

    @Test
    void movesByWordLineAndDocument() {
        editor.setText("int count = 10;\n  next();");
        press("caret-next-word");
        assertEquals("int |count = 10;\n  next();", state());
        press("select-caret-next-word");
        assertEquals("int ^count |= 10;\n  next();", state());
        press("caret-right"); // drops the selection at its end
        assertEquals("int count |= 10;\n  next();", state());
        press("caret-line-end");
        press("caret-right");
        press("caret-line-start");
        assertEquals("int count = 10;\n  |next();", state());
        press("caret-line-start");
        assertEquals("int count = 10;\n|  next();", state());
        press("caret-previous-word");
        press("caret-previous-word");
        assertEquals("int count = 10|;\n  next();", state());
        press("select-caret-document-end");
        assertEquals("int count = 10^;\n  next();|", state());
        press("caret-document-start");
        assertEquals("|int count = 10;\n  next();", state());
    }

    @Test
    void movesUpAndDownKeepingTheColumn() {
        editor.setText("long line here\nab\nanother long one");
        editor.select(9, 9);
        press("caret-down");
        assertEquals("long line here\nab|\nanother long one", state());
        press("caret-down");
        assertEquals("long line here\nab\nanother l|ong one", state());
        press("caret-up", 5);
        assertEquals("|long line here\nab\nanother long one", state());
    }

    @Test
    void deletesWordsAndDuplicatesLines() {
        editor.setText("alpha beta\ngamma");
        editor.select(10, 10);
        press("delete-previous-word");
        assertEquals("alpha |\ngamma", state());
        press("duplicate-lines");
        assertEquals("alpha \nalpha |\ngamma", state());
        editor.undo();
        assertEquals("alpha |\ngamma", state());
    }

    private int rows() {
        return editor.getPreferredSize().height / editor.getRowHeight();
    }

    @Test
    void fontThatIsNotMonospacedIsMeasuredCharByChar() {
        editor.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        String text = "iiii\nWWWW\n\tif (a) { b(); }";
        editor.setText(text);
        Rectangle narrow = editor.getOffsetBounds(4);
        Rectangle wide = editor.getOffsetBounds(9);
        assertTrue(narrow.x < wide.x, "four i are narrower than four W");
        assertEquals(editor.getOffsetBounds(0).x, editor.getOffsetBounds(5).x, "lines begin at the same place");

        // a click where a char begins puts the caret before it, wherever the char is
        for (int offset = 0; offset < text.length(); offset++) {
            if (text.charAt(offset) == '\n') {
                continue;
            }
            Rectangle bounds = editor.getOffsetBounds(offset);
            assertEquals(offset, editor.offsetAt(bounds.x, bounds.y + 1), "offset " + offset);
            assertTrue(bounds.x < editor.getOffsetBounds(offset + 1).x, "offset " + offset + " has a width");
        }

        editor.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        assertEquals(editor.getOffsetBounds(4).x, editor.getOffsetBounds(9).x, "back on the grid");
    }

    @Test
    void foldsCollapseExpandAndFollowEdits() {
        // moving up keeps the caret at the same place across, which is the same column only on a grid
        editor.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        editor.setText("top\nclass A {\n    void run() {\n        x();\n    }\n}\nend");
        assertTrue(editor.isFoldable(1));
        assertTrue(editor.isFoldable(2));
        assertFalse(editor.isFoldable(0));
        assertEquals(7, rows());

        editor.select(editor.getText().indexOf("x()"), editor.getText().indexOf("x()"));
        editor.collapseFold(1);
        assertEquals(4, rows(), "the three lines of the body are hidden");
        assertTrue(editor.isCollapsed(1));
        assertEquals("top\nclass A {|", state().substring(0, 14), "the caret leaves the hidden lines");

        editor.select(0, 0);
        press("insert-break"); // a line above moves the fold down with its block
        assertTrue(editor.isCollapsed(2));
        assertFalse(editor.isCollapsed(1));
        assertEquals(5, rows());

        press("caret-document-end");
        press("caret-up", 2); // steps over the hidden lines onto the first line of the fold
        assertEquals(editor.getModel().lineStart(2) + 3, editor.getCaretPosition());
        assertTrue(editor.isCollapsed(2));

        press("caret-line-end");
        press("caret-right"); // moving into the hidden text opens the fold
        assertFalse(editor.isCollapsed(2));
        assertEquals(8, rows());

        editor.collapseFold(2);
        editor.collapseFold(0);
        editor.expandAllFolds();
        assertEquals(8, rows());
    }

    @Test
    void findsTheMatchingBracket() {
        editor.setText("call(a[0], (b)) { }");
        assertEquals(14, editor.getMatchingBracket(4));
        assertEquals(4, editor.getMatchingBracket(14));
        assertEquals(8, editor.getMatchingBracket(6));
        assertEquals(18, editor.getMatchingBracket(16));
        assertEquals(-1, editor.getMatchingBracket(0));
        editor.setText("no ) partner (");
        assertEquals(-1, editor.getMatchingBracket(3));
        assertEquals(-1, editor.getMatchingBracket(13));
    }

    @Test
    void aBracketTypedWithItsPartnerMovesTheCaretOnce() {
        List<String> seen = new java.util.ArrayList<>();
        editor.addSelectionListener(e -> seen.add(state()));
        type("(");
        assertEquals(List.of("(|)"), seen, "the caret is not seen after the partner on its way");
        seen.clear();
        editor.setText("word");
        seen.clear();
        editor.select(0, 4);
        seen.clear();
        type("\"");
        assertEquals("\"^word|\"", seen.get(seen.size() - 1));
    }

    @Test
    void closesBracketsAndQuotesAsTheyAreTyped() {
        type("call(");
        assertEquals("call(|)", state());
        type("\"a");
        assertEquals("call(\"a|\")", state());
        type("\"");
        assertEquals("call(\"a\"|)", state(), "typing the closing quote steps over the one that is there");
        type(")");
        assertEquals("call(\"a\")|", state());

        editor.setText("");
        type("[{");
        assertEquals("[{|}]", state());
        press("delete-previous");
        assertEquals("[|]", state(), "Backspace between a pair removes both");
        press("delete-previous");
        assertEquals("|", state());

        editor.setText("word");
        editor.select(0, 0);
        type("(");
        assertEquals("(|word", state(), "no partner in front of a word");
        editor.setText("don");
        editor.select(3, 3);
        type("'");
        assertEquals("don'|", state(), "an apostrophe after a letter stays single");
    }

    @Test
    void wrapsTheSelectionAndOpensBlocks() {
        editor.setText("a + b");
        editor.select(0, 5);
        type("(");
        assertEquals("(^a + b|)", state());
        editor.undo();
        assertEquals("a + b", editor.getText());

        editor.setText("    run() ");
        editor.select(10, 10);
        type("{");
        press("insert-break");
        type("x");
        assertEquals("    run() {\n        x|\n    }", state(), "the closing bracket goes onto its own line");

        editor.setAutoClosePairs(false);
        editor.setText("");
        type("(");
        assertEquals("(|", state());
    }

    @Test
    void togglesLineComments() {
        Language slashes = RuleLanguage.builder("toy", "Toy").lineComment("//").blockComment("/*", "*/").build();
        editor.setDocument(new GapTextModel("    if (x) {\n        y();\n\n    }\nend"), slashes);
        editor.select(6, 30); // from inside the first line to inside the fourth
        editor.toggleComment();
        assertEquals("    // if (x) {\n    //     y();\n\n    // }\nend", editor.getText(),
                "the marks line up at the least indentation, and a blank line is left alone");
        assertEquals(9, editor.getSelectionStart(), "the selection keeps to the same text");
        editor.toggleComment();
        assertEquals("    if (x) {\n        y();\n\n    }\nend", editor.getText());
        assertEquals(6, editor.getSelectionStart());
        assertEquals(30, editor.getSelectionEnd());

        // one line commented and one not: both get a mark, so nothing is lost
        editor.setDocument(new GapTextModel("// a\nb"), slashes);
        editor.selectAll();
        editor.toggleComment();
        assertEquals("// // a\n// b", editor.getText());
        editor.undo();
        assertEquals("// a\nb", editor.getText(), "one undo step");

        editor.select(2, 2);
        editor.toggleComment();
        assertEquals("|a\nb", state(), "without a selection, the caret's line");
    }

    @Test
    void wrapsLinesInBlockCommentsWhereThereIsNoLineComment() {
        Language markup = RuleLanguage.builder("markup", "Markup").blockComment("<!--", "-->").build();
        editor.setDocument(new GapTextModel("  <a>\n  <b/>  "), markup);
        editor.selectAll();
        editor.toggleComment();
        assertEquals("  <!-- <a> -->\n  <!-- <b/> -->  ", editor.getText());
        editor.toggleComment();
        assertEquals("  <a>\n  <b/>  ", editor.getText());

        editor.setDocument(new GapTextModel("plain"), null);
        editor.toggleComment();
        assertEquals("plain", editor.getText(), "nothing to do without a language that has comments");
    }

    @Test
    void formatsTheWholeTextAsOneUndoStep() {
        editor.setDocument(new GapTextModel("{\"a\":[1,2],\"b\":3}"), new JsonLanguage());
        assertTrue(editor.canFormat());
        editor.select(11, 11); // in front of "b"
        press("format");
        assertEquals("{\n    \"a\": [\n        1,\n        2\n    ],\n    |\"b\": 3\n}", state(),
                "indented as the editor indents, and the caret stays with its text");
        press("format");
        editor.undo();
        assertEquals("{\"a\":[1,2],|\"b\":3}", state(), "formatting what is formatted already is no edit");
        assertFalse(editor.canUndo());

        editor.setTabsToSpaces(false);
        editor.setDocument(new GapTextModel("[1]\r\n"), new JsonLanguage());
        editor.format();
        assertEquals("[\r\n\t1\r\n]\r\n", editor.getText(), "tabs and line terminators as the document has them");
    }

    @Test
    void formatsOnlyTheSelectedLines() {
        editor.setDocument(new GapTextModel("{\n    \"keep\":{\"x\":1},\n    \"a\":{\"b\":[1,2]},\n    \"z\":1\n}"), new JsonLanguage());
        editor.select(26, 30); // from "a" to its brace
        editor.format();
        assertEquals("{\n    \"keep\":{\"x\":1},\n    ^\"a\": |{\n        \"b\": [\n            1,\n            2\n        ]\n    },\n    \"z\":1\n}",
                state(), "the lines keep the indentation they had in common");
        editor.undo();
        assertEquals("{\n    \"keep\":{\"x\":1},\n    ^\"a\":|{\"b\":[1,2]},\n    \"z\":1\n}", state());
    }

    @Test
    void formatsSelectedLinesOfCodeWhereTheyStand() {
        editor.setDocument(new GapTextModel("class A {\n    void f() {\n        int x=1;\n        if(x>0){\n        y();\n        }\n    }\n}"),
                new com.swingcraft4j.code.languages.java.JavaLanguage());
        editor.select(42, 70); // from the if to the brace that closes it
        editor.format();
        assertEquals("class A {\n    void f() {\n        int x=1;\n        if (x > 0) {\n            y();\n        }\n    }\n}",
                editor.getText(), "the line above the selection is left as it was");
        editor.undo();
        editor.select(0, 0);
        editor.format();
        assertEquals("class A {\n    void f() {\n        int x = 1;\n        if (x > 0) {\n            y();\n        }\n    }\n}",
                editor.getText());
    }

    @Test
    void keepsTheCaretWithItsTextWhereFormattingAddsChars() {
        editor.setDocument(new GapTextModel("|a|b|\n|-|-|\n|long|x|\n\nend"), new MarkdownLanguage());
        editor.select(18, 18); // in front of the x
        editor.format();
        assertEquals("| a    | b   |\n| ---- | --- |\n| long | |x   |\n\nend", state());
        editor.undo();
        editor.select(24, 24);
        editor.format();
        assertEquals("| a    | b   |\n| ---- | --- |\n| long | x   |\n\nen|d", state(), "after dashes were added before it");
        editor.undo();
        editor.select(21, 21);
        editor.format();
        assertEquals("| a    | b   |\n| ---- | --- |\n| long | x   |\n|\nend", state(), "on an empty line");
    }

    @Test
    void formattingKeepsTheRowOfTheCaretWhereItIsOnTheScreen() {
        StringBuilder text = new StringBuilder("{\n\"first\":[1,2],\n");
        for (int i = 0; i < 40; i++) {
            text.append("    \"n").append(i).append("\": ").append(i).append(",\n");
        }
        editor.setDocument(new GapTextModel(text.append("    \"last\": 0\n}")), new JsonLanguage());
        javax.swing.JViewport viewport = new javax.swing.JViewport();
        viewport.setView(editor);
        viewport.setSize(400, editor.getRowHeight() * 6);
        viewport.doLayout();
        viewport.setViewPosition(new java.awt.Point(0, editor.getRowHeight() * 20));
        editor.select(editor.getModel().lineStart(22), editor.getModel().lineStart(22));

        editor.format();
        assertEquals(25, editor.getModel().lineOfOffset(editor.getCaretPosition()), "three lines were added above the caret");
        assertEquals(editor.getRowHeight() * 23, viewport.getViewPosition().y);
    }

    @Test
    void doesNotFormatWithoutAFormatter() {
        Language toy = RuleLanguage.builder("toy", "Toy").build();
        editor.setDocument(new GapTextModel("{\"a\":1}"), toy);
        assertFalse(editor.canFormat());
        editor.format();
        assertEquals("{\"a\":1}", editor.getText());
        editor.setLanguage(null);
        assertFalse(editor.canFormat());
        editor.format();
        assertFalse(editor.canUndo());

        editor.setLanguage(new JsonLanguage());
        editor.setEditable(false);
        editor.format();
        assertEquals("{\"a\":1}", editor.getText(), "nor a text that is read only");
    }

    @Test
    void givesAnActionOtherKeys() {
        javax.swing.KeyStroke standard = javax.swing.KeyStroke.getKeyStroke(KeyEvent.VK_F,
                java.awt.event.InputEvent.SHIFT_DOWN_MASK | java.awt.event.InputEvent.ALT_DOWN_MASK);
        javax.swing.KeyStroke f8 = javax.swing.KeyStroke.getKeyStroke(KeyEvent.VK_F8, 0);
        javax.swing.KeyStroke undoKey = javax.swing.KeyStroke.getKeyStroke(KeyEvent.VK_Z, java.awt.event.InputEvent.CTRL_DOWN_MASK);
        javax.swing.InputMap keys = editor.getInputMap(javax.swing.JComponent.WHEN_FOCUSED);
        assertEquals(List.of(standard), editor.getKeys(JCodeEditor.ACTION_FORMAT));

        editor.setKeys(JCodeEditor.ACTION_FORMAT, f8, undoKey);
        assertEquals(null, keys.get(standard), "the key it had no longer runs it");
        assertEquals("format", keys.get(f8));
        assertEquals("format", keys.get(undoKey), "a key taken from another action");
        assertEquals(List.of(), editor.getKeys(JCodeEditor.ACTION_UNDO));

        editor.setKeys(JCodeEditor.ACTION_FORMAT);
        assertEquals(List.of(), editor.getKeys(JCodeEditor.ACTION_FORMAT), "no keys leaves it without any");
        assertEquals(List.of(), editor.getKeys("no-such-action"));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> editor.setKeys("no-such-action", f8));
    }

    @Test
    void movesLinesUpAndDown() {
        editor.setText("one\ntwo\r\nthree\nfour");
        editor.select(5, 5); // in "two"
        editor.moveLinesDown();
        assertEquals("one\nthree\r\nt|wo\nfour", state(), "the terminator between the two lines stays between them");
        editor.moveLinesUp();
        editor.moveLinesUp();
        assertEquals("t|wo\none\r\nthree\nfour", state());
        editor.moveLinesUp();
        assertEquals("t|wo\none\r\nthree\nfour", state(), "already at the top");

        editor.select(1, 6); // "two" and "one"
        editor.moveLinesDown();
        assertEquals("three\r\nt^wo\non|e\nfour", state(), "several lines move as one block");
        editor.undo();
        assertEquals("two\none\r\nthree\nfour", editor.getText());
    }

    @Test
    void markersFollowTheTextAndGiveTheirMessage() {
        editor.setText("int value = cout;\nnext line");
        editor.setMarkers(List.of(Marker.error(12, 16, "cannot find symbol: cout"), Marker.warning(18, 22, "unused"),
                Marker.info(100, 200, "past the end")));
        assertEquals(List.of(Marker.error(12, 16, "cannot find symbol: cout"), Marker.warning(18, 22, "unused")),
                editor.getMarkers(), "one starting past the end of the text is dropped");

        editor.select(0, 0);
        type("final ");
        assertEquals(List.of(18, 24), editor.getMarkers().stream().map(Marker::start).toList(), "text typed before moves them");
        java.awt.Rectangle inside = editor.getOffsetBounds(19);
        java.awt.event.MouseEvent over = new java.awt.event.MouseEvent(editor, java.awt.event.MouseEvent.MOUSE_MOVED, 0, 0,
                inside.x + 2, inside.y + 4, 0, false);
        assertEquals("<html>cannot find symbol: cout</html>", editor.getToolTipText(over));
        java.awt.Rectangle outside = editor.getOffsetBounds(3);
        assertEquals(null, editor.getToolTipText(new java.awt.event.MouseEvent(editor, java.awt.event.MouseEvent.MOUSE_MOVED,
                0, 0, outside.x + 2, outside.y + 4, 0, false)));

        editor.select(18, 22);
        editor.replaceSelection("");
        assertEquals(List.of("unused"), editor.getMarkers().stream().map(Marker::message).toList(),
                "a marker goes with the text it was on");

        int[] calls = new int[1];
        editor.setMarkerProvider((model, language) -> {
            calls[0]++;
            int at = model.getText(0, model.length()).indexOf("next");
            return at < 0 ? List.of() : List.of(Marker.info(at, at + 4, "found"));
        });
        assertEquals(1, calls[0], "asked at once");
        assertEquals(List.of("found"), editor.getMarkers().stream().map(Marker::message).toList());
        editor.setMarkerProvider(null);
        assertEquals(List.of(), editor.getMarkers());
    }

    @Test
    void knowsWhetherTheTextWasChangedSinceItWasSaved() {
        List<Object> reported = new java.util.ArrayList<>();
        editor.addPropertyChangeListener("modified", e -> reported.add(e.getNewValue()));
        editor.setText("saved text");
        assertFalse(editor.isModified(), "a document just shown has nothing to save");

        editor.select(0, 0);
        type("ab");
        assertTrue(editor.isModified());
        editor.undo();
        assertFalse(editor.isModified(), "undoing back to the saved state");
        editor.redo();
        assertTrue(editor.isModified());

        editor.markSaved();
        assertFalse(editor.isModified());
        type("c");
        assertTrue(editor.isModified(), "typing on after saving does not slip into what was saved");
        editor.undo();
        assertFalse(editor.isModified());
        editor.undo();
        assertTrue(editor.isModified(), "undoing past the saved state is a change too");
        type("x");
        editor.undo();
        editor.redo();
        assertTrue(editor.isModified(), "the saved state was discarded with what could be redone");

        assertEquals(List.of(true, false, true, false, true, false, true), reported);
        editor.setText("another");
        assertFalse(editor.isModified());
    }

    @Test
    void wrapsAtWordsWhenAsked() {
        editor.setText("the quick brown fox");
        int cell = editor.getOffsetBounds(1).x - editor.getOffsetBounds(0).x;
        int row = editor.getRowHeight();
        editor.setLineWrap(true);
        editor.setSize(editor.getOffsetBounds(0).x * 2 + 12 * cell + 1, 200); // room for twelve cells
        assertEquals(0, editor.getOffsetBounds(10).y, "after any char: \"br\" still fits on the first row");
        assertEquals(row, editor.getOffsetBounds(12).y);

        editor.setWrapStyleWord(true);
        assertEquals(0, editor.getOffsetBounds(9).y);
        assertEquals(row, editor.getOffsetBounds(10).y, "at words: \"brown\" starts the second row");
        assertEquals(editor.getOffsetBounds(0).x, editor.getOffsetBounds(10).x);
        assertEquals(2 * row, editor.getPreferredSize().height);
        // a click on a char gives that char back, on either row
        for (int offset = 0; offset < 19; offset++) {
            java.awt.Rectangle bounds = editor.getOffsetBounds(offset);
            assertEquals(offset, editor.offsetAt(bounds.x + 1, bounds.y + 2), "offset " + offset);
        }
        editor.select(19, 19);
        type(" jumps");
        assertEquals(3 * row, editor.getPreferredSize().height, "typing re-wraps the line");
    }

    private void compose(String text, int committed) {
        java.text.AttributedCharacterIterator chars = text == null ? null : new java.text.AttributedString(text).getIterator();
        editor.dispatchEvent(new java.awt.event.InputMethodEvent(editor, java.awt.event.InputMethodEvent.INPUT_METHOD_TEXT_CHANGED,
                chars, committed, text == null ? null : java.awt.font.TextHitInfo.leading(text.length() - committed), null));
    }

    @Test
    void expandsACollapsedFoldWithAClickOnItsMark() {
        editor.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        editor.setText("class A {\n    run();\n}\nend");
        editor.setSize(400, 200);
        editor.collapseFold(0);
        assertTrue(editor.isCollapsed(0));
        Rectangle end = editor.getOffsetBounds(editor.getModel().lineEnd(0));
        int cell = editor.getOffsetBounds(1).x - editor.getOffsetBounds(0).x;

        // on the text of the line the click sets the caret
        click(editor.getOffsetBounds(2).x + 1, end.y + end.height / 2);
        assertTrue(editor.isCollapsed(0));
        assertEquals(2, editor.getCaretPosition());
        // the mark starts one cell after the end of the line
        click(end.x + cell + 4, end.y + end.height / 2);
        assertFalse(editor.isCollapsed(0));
        assertEquals(2, editor.getCaretPosition(), "the click on the mark does not move the caret");
    }

    private void click(int x, int y) {
        editor.dispatchEvent(new java.awt.event.MouseEvent(editor, java.awt.event.MouseEvent.MOUSE_PRESSED, 0,
                java.awt.event.InputEvent.BUTTON1_DOWN_MASK, x, y, 1, false, java.awt.event.MouseEvent.BUTTON1));
    }

    @Test
    void leavesSpaceBelowTheLastLineAndTellsOfAnotherModel() {
        editor.setText("one\ntwo");
        int height = editor.getPreferredSize().height;
        editor.setBottomPadding(30);
        assertEquals(height + 30, editor.getPreferredSize().height);
        editor.setBottomPadding(0);

        List<Object> models = new java.util.ArrayList<>();
        editor.addPropertyChangeListener("model", event -> models.add(event.getNewValue()));
        editor.setText("three");
        assertEquals(List.of(editor.getModel()), models);
        type("x");
        assertEquals(1, models.size(), "an edit is not another model");
    }

    @Test
    void composesTextInPlaceWithAnInputMethod() {
        editor.setText("ab");
        editor.select(1, 1);
        compose("n", 0);
        compose("ni", 0);
        assertEquals("ani|b", state(), "what is being composed is shown where it will go");
        assertTrue(editor.isComposing());
        assertFalse(editor.canUndo(), "and is not an edit yet");
        assertFalse(editor.isModified());

        compose("你", 1);
        assertEquals("a你|b", state(), "what the input method commits replaces it");
        assertFalse(editor.isComposing());
        assertTrue(editor.isModified());
        editor.undo();
        assertEquals("a|b", state(), "as one edit");

        compose("ha", 0);
        compose(null, 0);
        assertEquals("a|b", state(), "a composition given up leaves nothing behind");
        assertFalse(editor.canUndo());

        // part committed, the rest still being composed
        compose("好ma", 1);
        assertEquals("a好ma|b", state());
        assertTrue(editor.isComposing());
        editor.undo();
        assertEquals("a好|b", state(), "undo first settles what was being composed, then takes that back");
        assertFalse(editor.isComposing());
        editor.undo();
        assertEquals("a|b", state());
    }

    @Test
    void readOnlyBlocksEditingButNotMovement() {
        editor.setText("fixed");
        editor.setEditable(false);
        press("caret-right");
        press("delete-next");
        editor.replaceSelection("x");
        assertEquals("f|ixed", state());
        assertFalse(editor.canUndo());
    }

    @Test
    void aChangeMadeOnTheModelClearsTheHistory() {
        type("abc");
        assertTrue(editor.canUndo());
        ((com.swingcraft4j.code.text.EditableTextModel) editor.getModel()).replace(0, 1, "X");
        assertFalse(editor.canUndo());
        assertEquals("Xbc|", state());
    }
}
