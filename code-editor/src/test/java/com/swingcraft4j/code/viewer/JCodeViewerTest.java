package com.swingcraft4j.code.viewer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JCodeViewerTest {

    private final JCodeViewer viewer = new JCodeViewer("one two\n  three\nfour");

    private void press(String action) {
        viewer.getActionMap().get(action).actionPerformed(null);
    }

    @Test
    void theKeysMoveTheCaret() {
        press("caret-right");
        assertEquals(1, viewer.getCaretPosition());
        press("caret-next-word");
        assertEquals(4, viewer.getCaretPosition());
        press("caret-line-end");
        assertEquals(7, viewer.getCaretPosition());
        press("caret-down");
        assertEquals(15, viewer.getCaretPosition());
        press("caret-line-start");
        assertEquals(10, viewer.getCaretPosition(), "the first char of the line that is not a blank");
        press("caret-document-end");
        assertEquals(20, viewer.getCaretPosition());
        press("caret-left");
        press("caret-document-start");
        assertEquals(0, viewer.getCaretPosition());
    }

    @Test
    void upAndDownKeepTheColumn() {
        viewer.select(7, 7);
        press("caret-down");
        press("caret-down");
        assertEquals(20, viewer.getCaretPosition(), "the last line is shorter");
        press("caret-up");
        press("caret-up");
        assertEquals(7, viewer.getCaretPosition());
    }

    @Test
    void withShiftTheKeysExtendTheSelection() {
        press("select-caret-next-word");
        assertEquals("one ", viewer.getSelectedText());
        press("select-caret-line-end");
        press("select-caret-down");
        assertEquals(0, viewer.getSelectionAnchor());
        assertEquals(15, viewer.getCaretPosition());
        press("caret-left");
        assertEquals(0, viewer.getCaretPosition(), "a selection that is dropped leaves the caret at its start");
        assertEquals(0, viewer.getSelectionAnchor());
    }
}
