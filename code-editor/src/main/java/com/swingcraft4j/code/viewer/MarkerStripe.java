package com.swingcraft4j.code.viewer;

import com.swingcraft4j.code.layout.RowIndex;
import com.swingcraft4j.code.marker.Marker;
import com.swingcraft4j.code.marker.Severity;
import com.swingcraft4j.code.text.TextModel;
import com.swingcraft4j.code.theme.CodeTheme;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JScrollPane;
import javax.swing.JViewport;
import javax.swing.ToolTipManager;
import javax.swing.border.Border;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * A stripe at the right of a {@link JCodeViewer}, between the text and the vertical scroll bar,
 * with a mark for each marker: as far down the stripe as the marker is in the document, so it
 * shows those of the whole text and not only of the lines in view. A mark gives the messages
 * as a tooltip, and a click on it goes to the marker. A square at the top says how the whole
 * text is doing: it has the colour of the most serious marker, and is green when there are none.
 * <p>
 * It lies in room made for it by a border around the viewport of the scroll pane, which keeps
 * the layout of the scroll pane the one of the look and feel.
 */
final class MarkerStripe extends JComponent {

    /** The space at each side of a mark. */
    private static final int MARK_PAD = 2;
    private static final int MARK_HEIGHT = 3;
    private static final int STATUS_TOP = 2;
    private static final int STATUS_HEIGHT = 6;
    /** The room the mark at the top takes, which the marks of a text longer than the view stay out of. */
    private static final int STATUS_ROOM = STATUS_TOP + STATUS_HEIGHT + 2;
    /** The colour of the square while there are no markers. */
    private static final Color NO_MARKERS = new Color(0x59A869);
    /** The most messages one tooltip gives. */
    private static final int MAX_MESSAGES = 10;

    private final JCodeViewer viewer;
    private int width = 10;
    private JScrollPane scrollPane;
    private JViewport viewport;
    /** The border the viewport had before the stripe made room for itself. */
    private Border borderBefore;
    private Border border;
    /** For each row of pixels the most serious marker there, or null; null when not known. */
    private Severity[] marks;
    /** The height of the viewer the marks were worked out for. */
    private int marksViewHeight;
    private final ComponentListener viewportListener = new ComponentAdapter() {
        @Override
        public void componentResized(ComponentEvent e) {
            fit();
        }

        @Override
        public void componentMoved(ComponentEvent e) {
            fit();
        }
    };

    MarkerStripe(JCodeViewer viewer) {
        this.viewer = viewer;
        setOpaque(true);
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                List<Marker> found = markersAt(e.getY());
                if (isOnStatus(e.getY())) {
                    viewer.goToNextMarker();
                } else if (!found.isEmpty()) {
                    viewer.goToMarker(found.get(0));
                } else {
                    return;
                }
                viewer.requestFocusInWindow();
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                boolean clickable = isOnStatus(e.getY()) ? viewer.hasMarkers() : markAt(e.getY()) != null;
                setCursor(clickable ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : null);
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        ToolTipManager.sharedInstance().registerComponent(this);
    }

    int stripeWidth() {
        return width;
    }

    /** Makes the stripe another width, also where it is in a scroll pane already. */
    void setStripeWidth(int width) {
        if (width != this.width) {
            JScrollPane pane = scrollPane;
            install(null);
            this.width = width;
            install(pane);
        }
    }

    /** Puts the stripe into a scroll pane, taking it out of the one it was in; null only takes it out. */
    void install(JScrollPane pane) {
        if (pane == scrollPane) {
            return;
        }
        if (scrollPane != null) {
            viewport.removeComponentListener(viewportListener);
            scrollPane.remove(this);
            if (scrollPane.getViewportBorder() == border) {
                scrollPane.setViewportBorder(borderBefore);
            }
            scrollPane.revalidate();
            scrollPane.repaint();
        }
        scrollPane = pane;
        if (pane != null) {
            viewport = pane.getViewport();
            borderBefore = pane.getViewportBorder();
            Border room = BorderFactory.createEmptyBorder(0, 0, 0, width);
            border = borderBefore == null ? room : BorderFactory.createCompoundBorder(borderBefore, room);
            pane.setViewportBorder(border);
            pane.add(this); // not a part the layout of the scroll pane knows: the stripe places itself
            viewport.addComponentListener(viewportListener);
            fit();
            pane.revalidate();
        }
    }

    /** Fills the room at the right of the viewport. */
    private void fit() {
        setBounds(viewport.getX() + viewport.getWidth(), viewport.getY(), width, viewport.getHeight());
    }

    void markersChanged() {
        marks = null;
        repaint();
    }

    // ---- The square at the top ----

    /** Whether the square is there: not in a viewer that has never had to do with markers. */
    private boolean hasStatus() {
        return viewer.markersUsed() && getHeight() >= STATUS_ROOM;
    }

    /** Whether a height of the stripe is on the square and not on a mark, which a text that is all in view can have there. */
    private boolean isOnStatus(int y) {
        return hasStatus() && y < STATUS_TOP + STATUS_HEIGHT && markAt(y) == null;
    }

    /** How many markers there are of each severity, as "2 errors, 1 warning". */
    private String statusText() {
        int[] counts = new int[Severity.values().length];
        for (Marker marker : viewer.getMarkers()) {
            counts[marker.severity().ordinal()]++;
        }
        StringBuilder text = new StringBuilder();
        for (Severity severity : Severity.values()) {
            int count = counts[severity.ordinal()];
            if (count > 0) {
                text.append(text.isEmpty() ? "" : ", ").append(count).append(' ')
                        .append(severity.name().toLowerCase()).append(count == 1 ? "" : "s");
            }
        }
        return text.isEmpty() ? "No problems" : text.toString();
    }

    // ---- The marks ----

    /** How far down the stripe the middle of a row of the text is. */
    private int rowMiddle(int row, int viewHeight) {
        // A text longer than the view is spread over the stripe below the square. One that is all
        // in view is not moved, so that each mark is beside its line.
        int top = hasStatus() && viewHeight > getHeight() ? STATUS_ROOM : 0;
        long middle = viewer.rowY(row) + viewer.lineHeight() / 2;
        return top + (int) (middle * (getHeight() - top) / viewHeight);
    }

    /** The rows of pixels of the mark of a marker, from the first to past the last: from where it starts to where it ends. */
    private int[] markRows(Marker marker, TextModel model, RowIndex rows, int viewHeight) {
        int lastRow = rows.rowCount() - 1;
        int lastOffset = Math.min(model.length(), Math.max(marker.start(), marker.end() - 1));
        int first = rowMiddle(Math.min(rows.firstRow(model.lineOfOffset(marker.start())), lastRow), viewHeight);
        int last = rowMiddle(Math.min(rows.firstRow(model.lineOfOffset(lastOffset)), lastRow), viewHeight);
        first = Math.max(0, Math.min(first - MARK_HEIGHT / 2, getHeight() - MARK_HEIGHT));
        return new int[]{first, Math.min(getHeight(), Math.max(first + MARK_HEIGHT, last + MARK_HEIGHT - MARK_HEIGHT / 2))};
    }

    private Severity[] marks() {
        int height = getHeight();
        int viewHeight = viewer.getHeight();
        if (marks == null || marks.length != height || marksViewHeight != viewHeight) {
            marks = new Severity[height];
            marksViewHeight = viewHeight;
            if (viewHeight > 0) {
                TextModel model = viewer.getModel();
                RowIndex rows = viewer.rowIndex();
                for (Marker marker : viewer.getMarkers()) {
                    int[] span = markRows(marker, model, rows, viewHeight);
                    for (int y = span[0]; y < span[1]; y++) {
                        if (marks[y] == null || marker.severity().compareTo(marks[y]) < 0) {
                            marks[y] = marker.severity();
                        }
                    }
                }
            }
        }
        return marks;
    }

    /** The severity of the mark at a height of the stripe, or null where there is none. */
    private Severity markAt(int y) {
        Severity[] known = marks();
        return y >= 0 && y < known.length ? known[y] : null;
    }

    /** The markers whose mark is at a height of the stripe, the most serious first. */
    private List<Marker> markersAt(int y) {
        List<Marker> found = new ArrayList<>();
        int viewHeight = viewer.getHeight();
        if (markAt(y) != null && viewHeight > 0) {
            TextModel model = viewer.getModel();
            RowIndex rows = viewer.rowIndex();
            for (Marker marker : viewer.getMarkers()) {
                int[] span = markRows(marker, model, rows, viewHeight);
                if (y >= span[0] && y < span[1]) {
                    found.add(marker);
                }
            }
            found.sort((a, b) -> a.severity().compareTo(b.severity()));
        }
        return found;
    }

    /** The messages of the markers of the mark under the mouse, or over the square how many markers there are. */
    @Override
    public String getToolTipText(MouseEvent e) {
        if (isOnStatus(e.getY())) {
            return statusText();
        }
        List<Marker> found = markersAt(e.getY());
        return JCodeViewer.toolTipFor(found.subList(0, Math.min(found.size(), MAX_MESSAGES)));
    }

    @Override
    protected void paintComponent(Graphics g) {
        CodeTheme theme = viewer.getTheme();
        Rectangle clip = g.getClipBounds();
        g.setColor(theme.background());
        g.fillRect(clip.x, clip.y, clip.width, clip.height);
        if (hasStatus()) {
            Severity worst = null;
            for (Marker marker : viewer.getMarkers()) {
                if (worst == null || marker.severity().compareTo(worst) < 0) {
                    worst = marker.severity();
                }
            }
            g.setColor(worst != null ? theme.markerColor(worst) : NO_MARKERS);
            g.fillRect(MARK_PAD, STATUS_TOP, width - 2 * MARK_PAD, STATUS_HEIGHT); // as wide as the marks
        }
        // over the square, where a text that is all in view has the marks of its first lines
        Severity[] known = marks();
        int to = Math.min(known.length, clip.y + clip.height);
        for (int y = Math.max(0, clip.y); y < to; y++) {
            if (known[y] != null) {
                g.setColor(theme.markerColor(known[y]));
                g.fillRect(MARK_PAD, y, width - 2 * MARK_PAD, 1);
            }
        }
    }
}
