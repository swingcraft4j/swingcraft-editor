package com.swingcraft4j.code.viewer;

import com.swingcraft4j.code.layout.RowIndex;
import com.swingcraft4j.code.theme.CodeTheme;

import com.swingcraft4j.code.marker.Marker;
import com.swingcraft4j.code.marker.Severity;

import javax.swing.JComponent;
import javax.swing.Timer;
import javax.swing.ToolTipManager;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Composite;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.awt.geom.Path2D;

/**
 * Line numbers, and next to them the arrows that collapse and expand folds, shown as the row
 * header of the scroll pane holding a {@link JCodeViewer}. The arrows of the open folds are
 * there only while the mouse is over the gutter, and fade in and out; that of a collapsed fold
 * stays.
 */
final class LineNumberGutter extends JComponent {

    /** The space between the fold arrows and the edge of the gutter. */
    private static final int FOLD_PAD_RIGHT = 4;
    /** How long the arrows of the open folds take to fade in or out. */
    private static final long FADE_NANOS = 150_000_000;

    private final JCodeViewer viewer;
    private boolean hovered;
    /** How much of the arrows of the open folds shows: from 0, when they are not there, to 1. */
    private float arrowAlpha;
    private long fadeTime;
    private final Timer fadeTimer = new Timer(15, e -> fade());

    LineNumberGutter(JCodeViewer viewer) {
        this.viewer = viewer;
        setOpaque(true);
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                int sticky = viewer.stickyLineAt(e.getY());
                if (sticky >= 0) {
                    viewer.goToStickyLine(sticky);
                    viewer.requestFocusInWindow();
                    return;
                }
                Marker marker = dotMarkerAt(e);
                int line = foldableLineAt(e);
                if (marker != null) {
                    viewer.goToMarker(marker);
                    viewer.requestFocusInWindow();
                } else if (line >= 0) {
                    viewer.toggleFold(line);
                }
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                setHovered(true);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                setHovered(false);
                viewer.setStickyHoverLine(-1);
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                int sticky = viewer.stickyLineAt(e.getY());
                viewer.setStickyHoverLine(sticky);
                boolean clickable = sticky >= 0 || dotMarkerAt(e) != null || foldableLineAt(e) >= 0;
                setCursor(clickable ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : null);
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        ToolTipManager.sharedInstance().registerComponent(this);
    }

    private void setHovered(boolean hovered) {
        if (hovered != this.hovered) {
            this.hovered = hovered;
            fadeTime = System.nanoTime();
            fadeTimer.start();
        }
    }

    /** Brings the arrows of the open folds a step closer to shown or to hidden, by the time that has passed. */
    private void fade() {
        long now = System.nanoTime();
        float step = (float) (now - fadeTime) / FADE_NANOS;
        fadeTime = now;
        arrowAlpha = hovered ? Math.min(1, arrowAlpha + step) : Math.max(0, arrowAlpha - step);
        if (arrowAlpha == (hovered ? 1 : 0)) {
            fadeTimer.stop();
        }
        int foldWidth = foldColumnWidth();
        Rectangle visible = getVisibleRect();
        repaint(getWidth() - 1 - foldWidth, visible.y, foldWidth, visible.height);
    }

    /** The messages of the markers on the line under the mouse. */
    @Override
    public String getToolTipText(MouseEvent e) {
        RowIndex rows = viewer.rowIndex();
        int row = viewer.rowAtY(e.getY());
        if (!viewer.hasMarkers() || !viewer.isMarkerDots() || row < 0 || row >= rows.rowCount()) {
            return null;
        }
        return viewer.lineMarkerText(rows.lineAtRow(row));
    }

    /** Width of the column holding the fold arrows, at the right edge of the gutter, with the space after them. */
    private int foldColumnWidth() {
        return viewer.isFoldingEnabled() ? (int) Math.ceil(viewer.cellWidth() * 2) + FOLD_PAD_RIGHT : 0;
    }

    /**
     * Room for the dots of the markers where there is no column of fold arrows to share it with.
     * It is there whether or not there are markers, so that the first one does not move the text.
     */
    private int dotColumnWidth() {
        return viewer.isMarkerDots() && !viewer.isFoldingEnabled() ? (int) Math.ceil(viewer.cellWidth() / 2) : 0;
    }

    /** Where the centre of the fold arrows is; a whole number, which puts it on a pixel. */
    private int arrowX() {
        return getWidth() - 1 - FOLD_PAD_RIGHT - (foldColumnWidth() - FOLD_PAD_RIGHT) / 2;
    }

    /** Half the width of a fold arrow; a whole number of pixels, so that every arrow is like the others. */
    private int arrowSize() {
        return (int) Math.max(3, Math.round(viewer.lineHeight() / 5.0));
    }

    /** Where the line numbers end: a cell before what comes after them. The dots stand in between. */
    private double numbersEnd() {
        return getWidth() - 1 - foldColumnWidth() - dotColumnWidth() - viewer.cellWidth();
    }

    /** Where the room for the dots ends: at the fold arrows, or else at the edge of the gutter. */
    private double dotsEnd() {
        return foldColumnWidth() > 0 ? arrowX() - arrowSize() : getWidth() - 1;
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

    /** The marker whose dot is under the mouse, or null. */
    private Marker dotMarkerAt(MouseEvent e) {
        RowIndex rows = viewer.rowIndex();
        int row = viewer.rowAtY(e.getY());
        if (!viewer.hasMarkers() || !viewer.isMarkerDots() || e.getX() < numbersEnd() || e.getX() >= dotsEnd()
                || row < 0 || row >= rows.rowCount()) {
            return null;
        }
        int line = rows.lineAtRow(row);
        return rows.firstRow(line) == row ? viewer.lineMarker(line) : null;
    }

    @Override
    public Dimension getPreferredSize() {
        int digits = Math.max(2, Integer.toString(viewer.getLineCount()).length());
        int width = (int) Math.ceil((digits + 2) * viewer.cellWidth()) + dotColumnWidth() + foldColumnWidth() + 1;
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
            int arrowX = arrowX();
            int arrowSize = arrowSize();
            double numbersEnd = numbersEnd();
            double dotsEnd = dotsEnd();
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
                float x = (float) (numbersEnd - number.length() * cellWidth);
                g.drawString(number, x, viewer.rowY(row) + viewer.baseline());
                Severity severity = viewer.hasMarkers() && viewer.isMarkerDots() ? viewer.lineSeverity(line) : null;
                if (severity != null) {
                    // a dot in the colour of the most serious marker on the line
                    int size = Math.max(4, Math.min(lineHeight / 2 - 1, (int) cellWidth - 1));
                    int left = (int) Math.round((numbersEnd + dotsEnd - size) / 2);
                    g.setColor(theme.markerColor(severity));
                    g.fillOval(left, viewer.rowY(row) + (lineHeight - size) / 2, size, size);
                    g.setColor(theme.gutterForeground());
                }
                boolean collapsed = viewer.isCollapsed(line);
                if (foldWidth > 0 && viewer.isFoldable(line) && (collapsed || arrowAlpha > 0)) {
                    int centerY = viewer.rowY(row) + lineHeight / 2;
                    Composite opaque = g.getComposite();
                    if (!collapsed) {
                        g.setComposite(AlphaComposite.SrcOver.derive(arrowAlpha));
                    }
                    paintArrow(g, arrowX + 0.5, centerY + 0.5, arrowSize, collapsed);
                    g.setComposite(opaque);
                }
            }
            paintStickyLines(g, theme, lineHeight, numbersEnd, cellWidth);
        } finally {
            g.dispose();
        }
    }

    /** Paints the numbers of the sticky lines over the others, beside where the viewer paints their text. */
    private void paintStickyLines(Graphics2D g, CodeTheme theme, int lineHeight, double numbersEnd, double cellWidth) {
        List<StickyLines.Stuck> stuck = viewer.stickyLayout();
        if (stuck.isEmpty()) {
            return;
        }
        int width = getWidth();
        for (int i = stuck.size() - 1; i >= 0; i--) {
            int line = stuck.get(i).line();
            int y = stuck.get(i).y();
            g.setColor(line == viewer.stickyHoverLine() ? theme.foldMarkerBackground() : theme.gutterBackground());
            g.fillRect(0, y, width, lineHeight);
            g.setColor(theme.gutterForeground());
            String number = Integer.toString(line + 1);
            g.drawString(number, (float) (numbersEnd - number.length() * cellWidth), y + viewer.baseline());
        }
        g.setColor(theme.gutterBorder());
        int bottom = stuck.get(stuck.size() - 1).y() + lineHeight;
        g.fillRect(width - 1, stuck.get(0).y(), 1, bottom - stuck.get(0).y());
        g.setColor(theme.stickyLinesBorder());
        g.fillRect(0, bottom - 1, width, 1);
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
