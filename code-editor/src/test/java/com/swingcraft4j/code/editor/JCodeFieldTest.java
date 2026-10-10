package com.swingcraft4j.code.editor;

import com.swingcraft4j.code.languages.json.JsonLanguage;
import org.junit.jupiter.api.Test;

import javax.swing.JLabel;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JCodeFieldTest {

    private final JCodeField field = new JCodeField();
    private final JCodeEditor editor = field.getEditor();

    private void type(String text) {
        for (char c : text.toCharArray()) {
            editor.processKeyEvent(new KeyEvent(editor, KeyEvent.KEY_TYPED, 0, 0, KeyEvent.VK_UNDEFINED, c));
        }
    }

    private void press(String action) {
        editor.getActionMap().get(action).actionPerformed(null);
    }

    private void layOut(int width) {
        field.setSize(width, field.getPreferredSize().height);
        // by hand: a component that is in no window is not laid out when it is validated
        field.doLayout();
        editor.getParent().doLayout();
    }

    @Test
    void editsItsTextAsAnEditorDoes() {
        type("one two");
        assertEquals("one two", field.getText());
        editor.undo();
        assertEquals("one ", field.getText());
        field.setText("{\"a\": 1}");
        field.setLanguage(new JsonLanguage());
        assertSame(editor.getLanguage(), field.getLanguage());
        assertFalse(editor.canUndo(), "a text that is set has nothing to undo");
    }

    @Test
    void leavesTheLineBreaksOutOfATextThatIsSet() {
        field.setText("\r\none\r\n  two\n\nthree\n");
        assertEquals("one   two three", field.getText());
        assertEquals(1, editor.getLineCount());
    }

    @Test
    void leavesTheLineBreaksOutOfATextThatIsEntered() {
        field.setText("ab");
        editor.select(1, 1);
        editor.replaceSelection("x\ny\n");
        assertEquals("ax yb", field.getText());
        assertEquals(4, editor.getCaretPosition(), "the caret is after what was entered");
        editor.undo();
        assertEquals("ab", field.getText());
    }

    @Test
    void enterTellsTheActionListenersAndBreaksNoLine() {
        List<String> commands = new ArrayList<>();
        field.addActionListener(e -> commands.add(e.getActionCommand()));
        type("go");
        press("insert-break");
        assertEquals(List.of("go"), commands);
        assertEquals("go", field.getText());
    }

    @Test
    void tabIsNotTyped() {
        type("a");
        press("insert-tab");
        press("unindent");
        assertEquals("a", field.getText());
    }

    @Test
    void hasNoKeysForWhatIsDoneToManyLines() {
        assertTrue(editor.getKeys(JCodeEditor.ACTION_FORMAT).isEmpty());
        assertTrue(editor.getKeys(JCodeEditor.ACTION_DUPLICATE_LINES).isEmpty());
        assertTrue(editor.getKeys(JCodeEditor.ACTION_GO_TO_LINE).isEmpty());
        assertFalse(editor.getKeys(JCodeEditor.ACTION_UNDO).isEmpty());
    }

    @Test
    void laysTheTextOutOnOneRowBetweenTheComponentsAtItsEnds() {
        JLabel leading = new JLabel("GET");
        JLabel trailing = new JLabel("Send");
        field.setLeadingComponent(leading);
        field.setTrailingComponent(trailing);
        field.setText("text");
        layOut(300);
        Rectangle text = editor.getParent().getBounds();
        assertEquals(editor.getRowHeight(), text.height);
        assertTrue(leading.getX() + leading.getWidth() <= text.x, "the text begins after the leading component");
        assertTrue(text.x + text.width <= trailing.getX(), "the text ends before the trailing component");
        assertTrue(trailing.getX() + trailing.getWidth() <= 300);

        field.setLeadingComponent(null);
        layOut(300);
        assertTrue(editor.getParent().getX() < text.x, "the text takes the room of a component that is gone");
        assertEquals(1, countOf(leading, trailing));
    }

    private int countOf(JLabel... components) {
        int count = 0;
        for (JLabel component : components) {
            count += component.getParent() == field ? 1 : 0;
        }
        return count;
    }

    @Test
    void wouldLikeToBeAsWideAsItsColumns() {
        field.setText("some text that is longer than three columns");
        int wide = field.getPreferredSize().width;
        field.setColumns(3);
        assertTrue(field.getPreferredSize().width < wide);
        field.setText("");
        int three = field.getPreferredSize().width;
        field.setColumns(30);
        assertTrue(field.getPreferredSize().width > three);
    }

    @Test
    void scrollsToTheCaretInATextThatIsWiderThanTheField() {
        layOut(120);
        type("a text that is a good deal wider than a field of a hundred and twenty pixels");
        Rectangle caret = editor.getOffsetBounds(editor.getCaretPosition());
        assertTrue(editor.getVisibleRect().intersects(caret), "the caret at the end is in view");
        assertTrue(editor.getVisibleRect().x > 0);
        press("caret-document-start");
        assertEquals(0, editor.getVisibleRect().x);
    }

    @Test
    void paintsWithAPlaceholderWhileItIsEmpty() {
        field.setPlaceholder("Enter URL");
        layOut(200);
        assertTrue(inked() > 0, "the placeholder is drawn");
        int placeholder = inked();
        field.setPlaceholder(null);
        assertTrue(inked() < placeholder);
    }

    /** How many pixels the editor draws on a clear image. */
    private int inked() {
        BufferedImage image = new BufferedImage(editor.getWidth(), editor.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        editor.paint(g);
        g.dispose();
        int count = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                count += image.getRGB(x, y) >>> 24 != 0 ? 1 : 0;
            }
        }
        return count;
    }
}
