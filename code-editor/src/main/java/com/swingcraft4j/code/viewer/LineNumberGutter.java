package com.swingcraft4j.code.viewer;

import com.swingcraft4j.code.layout.RowIndex;
import com.swingcraft4j.code.theme.CodeTheme;

import com.swingcraft4j.code.marker.Severity;

import javax.swing.JComponent;
import javax.swing.ToolTipManager;
import java.awt.BasicStroke;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;

/**
 * Line numbers, and next to them the arrows that collapse and expand folds, shown as the row
 * header of the scroll pane holding a {@link JCodeViewer}.
 */
final class LineNumberGutter extends JComponent {

    /** The space between the fold arrows and the edge of the gutter. */
    private static final int FOLD_PAD_RIGHT = 4;

    private final JCodeViewer viewer;

    LineNumberGutter(JCodeViewer viewer) {
        this.viewer = viewer;
        setOpaque(true);
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                int line = foldableLineAt(e);
                if (line >= 0) {
                    viewer.toggleFold(line);
                }
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                setCursor(foldableLineAt(e) >= 0 ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : null);
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        ToolTipManager.sharedInstance().registerComponent(this);
    }

    /** Width of the column holding the marks for lines with markers, at the left edge; none while there are no markers. */
    private int markColumnWidth() {
        return viewer.hasMarkers() ? viewer.lineHeight() * 2 / 3 : 0;
    }

    /** The messages of the markers on the line under the mouse. */
    @Override
    public String getToolTipText(MouseEvent e) {
        RowIndex rows = viewer.rowIndex();
        int row = viewer.rowAtY(e.getY());
        if (!viewer.hasMarkers() || row < 0 || row >= rows.rowCount()) {
            return null;
        }
        return viewer.lineMarkerText(rows.lineAtRow(row));
    }

    /** Width of the column holding the fold arrows, at the right edge of the gutter, with the space after them. */
    private int foldColumnWidth() {
        return viewer.isFoldingEnabled() ? (int) Math.ceil(viewer.cellWidth() * 2) + FOLD_PAD_RIGHT : 0;
    }

    /** The foldable line whose arrow is under the mouse, or -1. */
    private int foldableLineAt(MouseEvent e) {
        RowIndex rows = viewer.rowIndex();
        int row = viewer.rowAtY(e.getY());
        if (e.getX() < getWidth() - 1 - foldColumnWidth() || row < 0 || row >= rows.rowCount()) {
            return -1;
        }
        int line = rows.lineAtRow(row);
        return rows.firstRow(line) == row && viewer.isFoldable(line) ? line : -1;
    }

    @Override
    public Dimension getPreferredSize() {
        int digits = Math.max(2, Integer.toString(viewer.getLineCount()).length());
        int width = markColumnWidth() + (int) Math.ceil((digits + 2) * viewer.cellWidth()) + foldColumnWidth() + 1;
        return new Dimension(width, viewer.getPreferredSize().height);
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            CodeTheme theme = viewer.getTheme();
            Rectangle clip = g.getClipBounds();
            int width = getWidth();
            g.setColor(theme.gutterBackground());
            g.fillRect(clip.x, clip.y, clip.width, clip.height);
            g.setColor(theme.gutterBorder());
            g.fillRect(width - 1, clip.y, 1, clip.height);

            viewer.applyTextHints(g);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            // drawn where they are put: left to itself, Java moves each point of a line onto a
            // pixel, which bends the two halves of an arrow differently
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setFont(viewer.getFont());
            g.setColor(theme.gutterForeground());
            RowIndex rows = viewer.rowIndex();
            int lineHeight = viewer.lineHeight();
            double cellWidth = viewer.cellWidth();
            int foldWidth = foldColumnWidth();
            int firstRow = Math.max(0, viewer.rowAtY(clip.y));
            int lastRow = Math.min(rows.rowCount() - 1, viewer.rowAtY(clip.y + clip.height - 1));
            if (firstRow > lastRow) {
                return;
            }
            int line = rows.lineAtRow(firstRow);
            if (rows.firstRow(line) < firstRow) {
                line++;
            }
            int lineCount = viewer.getLineCount();
            for (; line < lineCount; line++) {
                int row = rows.firstRow(line);
                if (row > lastRow) {
                    break;
                }
                if (rows.isHidden(line)) {
                    continue;
                }
                String number = Integer.toString(line + 1);
                float x = (float) (width - 1 - foldWidth - (number.length() + 1) * cellWidth);
                g.drawString(number, x, viewer.rowY(row) + viewer.baseline());
                Severity severity = viewer.hasMarkers() ? viewer.lineSeverity(line) : null;
                if (severity != null) {
                    // a dot in the colour of the most serious marker on the line
                    int size = Math.max(6, lineHeight / 2 - 1);
                    g.setColor(theme.markerColor(severity));
                    g.fillOval(3, viewer.rowY(row) + (lineHeight - size) / 2, size, size);
                    g.setColor(theme.gutterForeground());
                }
                if (foldWidth > 0 && viewer.isFoldable(line)) {
                    // centred on a pixel and a whole number of pixels in size, so that the
                    // two halves are the same and every arrow is like the others
                    int centerX = width - 1 - FOLD_PAD_RIGHT - (foldWidth - FOLD_PAD_RIGHT) / 2;
                    int centerY = viewer.rowY(row) + lineHeight / 2;
                    paintArrow(g, centerX + 0.5, centerY + 0.5,
                            Math.max(3, Math.round(lineHeight / 5.0)), viewer.isCollapsed(line));
                }
            }
        } finally {
            g.dispose();
        }
    }

    /** A chevron pointing down for an open fold and to the right for a collapsed one. */
    private static void paintArrow(Graphics2D g, double x, double y, double size, boolean collapsed) {
        Path2D arrow = new Path2D.Double();
        if (collapsed) {
            arrow.moveTo(x - size / 2, y - size);
            arrow.lineTo(x + size / 2, y);
            arrow.lineTo(x - size / 2, y + size);
        } else {
            arrow.moveTo(x - size, y - size / 2);
            arrow.lineTo(x, y + size / 2);
            arrow.lineTo(x + size, y - size / 2);
        }
        g.draw(arrow);
    }
}
