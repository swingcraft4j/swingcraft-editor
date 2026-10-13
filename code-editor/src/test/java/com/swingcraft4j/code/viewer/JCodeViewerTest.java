package com.swingcraft4j.code.viewer;

import com.swingcraft4j.code.marker.Marker;
import org.junit.jupiter.api.Test;

import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import java.awt.event.MouseEvent;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JCodeViewerTest {

    private final JCodeViewer viewer = new JCodeViewer("one two\n  three\nfour");

    private void press(String action) {
        viewer.getActionMap().get(action).actionPerformed(null);
    }

    private MarkerStripe stripeOf(JScrollPane pane) {
        return (MarkerStripe) Arrays.stream(pane.getComponents()).filter(MarkerStripe.class::isInstance).findFirst().orElse(null);
    }

    @Test
    void theMarkerStripeLiesAtTheRightOfTheText() throws Exception {
        JScrollPane pane = new JScrollPane(viewer);
        pane.setSize(300, 200);
        pane.addNotify();
        layOut(pane);
        MarkerStripe stripe = stripeOf(pane);
        assertEquals(pane.getViewport().getX() + pane.getViewport().getWidth(), stripe.getX());
        assertEquals(pane.getViewport().getHeight(), stripe.getHeight());
        assertTrue(stripe.getWidth() > 0 && stripe.getX() + stripe.getWidth() <= pane.getWidth());

        viewer.setMarkers(List.of(Marker.warning(0, 3, "first"), Marker.error(16, 20, "last")));
        assertEquals("<html>first</html>", stripe.getToolTipText(mouseAt(stripe, viewer.getRowHeight() / 2)),
                "beside its line while the whole text is in view");
        assertNull(stripe.getToolTipText(mouseAt(stripe, 150)), "no mark there");
        assertEquals("1 error, 1 warning", stripe.getToolTipText(mouseAt(stripe, 3)), "the square at the top counts them");
        viewer.setMarkers(List.of(Marker.info(0, 20, "all of it")));
        assertEquals("<html>all of it</html>", stripe.getToolTipText(mouseAt(stripe, viewer.getRowHeight() * 5 / 2)),
                "a mark is as long as the lines of its marker");
        viewer.setMarkers(List.of());
        assertEquals("No problems", stripe.getToolTipText(mouseAt(stripe, 3)));

        viewer.setMarkerStripeWidth(16);
        layOut(pane);
        assertEquals(16, stripeOf(pane).getWidth());
        assertEquals(pane.getViewport().getX() + pane.getViewport().getWidth(), stripeOf(pane).getX(), "the text makes room");

        viewer.setMarkerStripe(false);
        assertNull(stripeOf(pane));
        assertNull(pane.getViewportBorder(), "and the scroll pane is as it was");
    }

    @Test
    void goesFromMarkerToMarker() {
        viewer.setMarkers(List.of(Marker.error(10, 15, "second"), Marker.warning(4, 7, "first"), Marker.info(16, 20, "third")));
        press(JCodeViewer.ACTION_NEXT_MARKER);
        assertEquals(4, viewer.getCaretPosition());
        press(JCodeViewer.ACTION_NEXT_MARKER);
        press(JCodeViewer.ACTION_NEXT_MARKER);
        assertEquals(16, viewer.getCaretPosition());
        press(JCodeViewer.ACTION_NEXT_MARKER);
        assertEquals(4, viewer.getCaretPosition(), "after the last comes the first again");
        press(JCodeViewer.ACTION_PREVIOUS_MARKER);
        assertEquals(16, viewer.getCaretPosition(), "and before the first the last");
        press(JCodeViewer.ACTION_PREVIOUS_MARKER);
        assertEquals(10, viewer.getCaretPosition());
        assertEquals(10, viewer.getSelectionAnchor(), "nothing is selected");

        viewer.setMarkers(List.of());
        press(JCodeViewer.ACTION_NEXT_MARKER);
        assertEquals(10, viewer.getCaretPosition(), "without markers the caret stays");
    }

    @Test
    void tellsWhenThereAreOtherMarkers() {
        int[] told = {0};
        viewer.addMarkerListener(e -> told[0]++);
        viewer.setMarkers(List.of(Marker.error(4, 7, "two")));
        assertEquals(1, told[0]);
        viewer.setMarkers(List.of(Marker.error(4, 7, "two")));
        assertEquals(1, told[0], "the same markers again are no news");
        viewer.setMarkers(List.of());
        assertEquals(2, told[0]);
    }

    /** Lays the scroll pane out, and lets the stripe hear of where its viewport is then. */
    private static void layOut(JScrollPane pane) throws Exception {
        pane.doLayout();
        pane.getViewport().doLayout();
        SwingUtilities.invokeAndWait(() -> { });
    }

    private static MouseEvent mouseAt(MarkerStripe stripe, int y) {
        return new MouseEvent(stripe, MouseEvent.MOUSE_MOVED, 0, 0, 3, y, 3, y, 0, false, MouseEvent.NOBUTTON);
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
