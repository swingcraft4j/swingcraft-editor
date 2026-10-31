package com.swingcraft4j.code.viewer;

import com.swingcraft4j.code.marker.Marker;
import org.junit.jupiter.api.Test;

import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.image.BufferedImage;
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

    /** A class with a method of forty lines and one more method; each line is one row. */
    private static String stickyCode() {
        StringBuilder code = new StringBuilder("class A {\n    void one() {\n"); // lines 0 and 1
        for (int i = 0; i < 40; i++) {
            code.append("        call();\n"); // lines 2 to 41
        }
        code.append("    }\n\n    void two() {\n"); // lines 42 to 44
        for (int i = 0; i < 40; i++) {
            code.append("        call();\n"); // so that the view can go well past the first method
        }
        return code.append("    }\n}\n").toString();
    }

    /** A viewer in a scroll pane twenty rows high, which can move its caret into view. */
    private static final class StickyViewer extends JCodeViewer {
        final JScrollPane pane = new JScrollPane(this);

        StickyViewer() throws Exception {
            setText(stickyCode());
            setStickyLinesVisible(true);
            pane.setSize(400, 20 * getRowHeight() + pane.getInsets().top + pane.getInsets().bottom);
            pane.addNotify();
            layOut(pane);
        }

        void scrollTo(int y) {
            pane.getViewport().setViewPosition(new Point(0, y));
        }

        int viewY() {
            return pane.getViewport().getViewPosition().y;
        }

        List<Integer> stuckLines() {
            return stickyLayout().stream().map(StickyLines.Stuck::line).toList();
        }

        void caretTo(int line) {
            select(getModel().lineStart(line), getModel().lineStart(line));
            scrollCaretIntoView();
        }
    }

    @Test
    void stickyLinesAreTheBlocksAroundTheTextInView() throws Exception {
        StickyViewer sticky = new StickyViewer();
        int rowHeight = sticky.getRowHeight();
        assertEquals(List.of(), sticky.stuckLines(), "at the top of the text everything is in place");

        sticky.scrollTo(3);
        assertEquals(List.of(0, 1), sticky.stuckLines(), "each line stays as soon as it starts to leave its place");
        sticky.scrollTo(10 * rowHeight);
        assertEquals(List.of(0, 1), sticky.stuckLines());
        assertEquals(10 * rowHeight, sticky.stickyLayout().get(0).y());
        assertEquals(11 * rowHeight, sticky.stickyLayout().get(1).y());
        assertEquals(1, sticky.stickyLineAt(11 * rowHeight + 1));
        assertEquals(-1, sticky.stickyLineAt(12 * rowHeight));
    }

    @Test
    void aStickyLineIsPushedUpByTheEndOfItsBlock() throws Exception {
        StickyViewer sticky = new StickyViewer();
        int rowHeight = sticky.getRowHeight();
        // the last line of the first method, 41, is right below the two sticky lines
        sticky.scrollTo(39 * rowHeight);
        assertEquals(List.of(0, 1), sticky.stuckLines());
        assertEquals(40 * rowHeight, sticky.stickyLayout().get(1).y());

        sticky.scrollTo(40 * rowHeight + 5);
        assertEquals(41 * rowHeight, sticky.stickyLayout().get(1).y(), "goes up with the text");
        assertEquals(40 * rowHeight + 5, sticky.stickyLayout().get(0).y());

        sticky.scrollTo(41 * rowHeight);
        assertEquals(List.of(0), sticky.stuckLines(), "gone behind the line above it");
    }

    @Test
    void stickyLinesKeepToTheirLimits() throws Exception {
        StickyViewer sticky = new StickyViewer();
        sticky.setStickyLinesMaxCount(1);
        sticky.scrollTo(10 * sticky.getRowHeight());
        assertEquals(List.of(1), sticky.stuckLines(), "the closest block stays when not all fit");

        sticky.setStickyLinesMaxCount(5);
        sticky.setStickyLinesVisible(false);
        assertEquals(List.of(), sticky.stuckLines());
    }

    @Test
    void stickyLinesLeaveCollapsedTextOut() throws Exception {
        StickyViewer sticky = new StickyViewer();
        int rowHeight = sticky.getRowHeight();
        sticky.collapseFold(1);
        // lines 2 to 41 are hidden, and the closing brace of the method follows the line starting it
        sticky.scrollTo(3);
        assertEquals(List.of(0, 1), sticky.stuckLines());
        assertEquals(rowHeight, sticky.stickyLayout().get(1).y(), "its block ends with it, so it stays where it is");
        sticky.scrollTo(rowHeight + 3);
        assertEquals(List.of(0), sticky.stuckLines());
    }

    @Test
    void aClickOnAStickyLineGoesToIt() throws Exception {
        StickyViewer sticky = new StickyViewer();
        int rowHeight = sticky.getRowHeight();
        sticky.scrollTo(20 * rowHeight);
        assertEquals(1, sticky.stickyLineAt(21 * rowHeight));
        MouseEvent press = new MouseEvent(sticky, MouseEvent.MOUSE_PRESSED, 0, InputEvent.BUTTON1_DOWN_MASK,
                200, 21 * rowHeight, 200, 21 * rowHeight, 1, false, MouseEvent.BUTTON1);
        for (MouseListener listener : sticky.getMouseListeners()) {
            listener.mousePressed(press);
        }
        assertEquals(sticky.getModel().lineStart(1) + 4, sticky.getCaretPosition(), "at the start of the text of the line");
        assertEquals(0, sticky.viewY(), "right below the line stuck above it, which is here the first line");
    }

    @Test
    void scrollingToALineLeavesItBelowTheStickyLines() throws Exception {
        StickyViewer sticky = new StickyViewer();
        int rowHeight = sticky.getRowHeight();
        sticky.scrollToLine(30);
        assertEquals(28 * rowHeight, sticky.viewY(), "with the class and the method above it");
        assertEquals(List.of(0, 1), sticky.stuckLines());
    }

    @Test
    void theCaretStaysClearOfTheStickyLines() throws Exception {
        StickyViewer sticky = new StickyViewer();
        int rowHeight = sticky.getRowHeight();
        sticky.scrollTo(20 * rowHeight);
        sticky.caretTo(21); // behind the second sticky line
        assertEquals(19 * rowHeight, sticky.viewY());
        sticky.caretTo(30);
        assertEquals(19 * rowHeight, sticky.viewY(), "a caret already in the open does not scroll");
    }

    @Test
    void paintsStickyLinesOverTheText() throws Exception {
        StickyViewer sticky = new StickyViewer();
        int rowHeight = sticky.getRowHeight();
        sticky.scrollTo(20 * rowHeight);
        layOut(sticky.pane);
        BufferedImage image = new BufferedImage(400, 20 * rowHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        sticky.pane.paint(g);
        g.dispose();
        int viewportX = sticky.pane.getViewport().getX();
        int border = sticky.getTheme().gutterBorder().getRGB();
        assertEquals(border, image.getRGB(viewportX + 200, sticky.pane.getViewport().getY() + 2 * rowHeight - 1),
                "the line under the sticky lines");
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
