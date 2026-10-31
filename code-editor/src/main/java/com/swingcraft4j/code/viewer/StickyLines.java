package com.swingcraft4j.code.viewer;

import com.swingcraft4j.code.folding.IndentFolding;
import com.swingcraft4j.code.layout.RowIndex;
import com.swingcraft4j.code.text.TextModel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Works out which lines stay at the top of a scrolled {@link JCodeViewer}: those starting the
 * regions around the text in view, as the class and the method around a line of its body. The
 * last of them is pushed up by the end of its region, so that it leaves with its last line.
 * The regions are those of {@link IndentFolding}, which come from indentation alone.
 */
final class StickyLines {

    /** A line that stays at the top, and the height it is at, in the coordinates of the viewer. */
    record Stuck(int line, int y) {
    }

    /** How many lines are remembered before starting over; finding them can mean a long walk up. */
    private static final int MAX_REMEMBERED = 1024;

    private final JCodeViewer viewer;
    private final Map<Integer, int[]> remembered = new HashMap<>();
    private TextModel rememberedModel;
    private long rememberedVersion;

    StickyLines(JCodeViewer viewer) {
        this.viewer = viewer;
    }

    /** The lines to show over a view from a height down, with room for at most a number of them; the outermost first. */
    List<Stuck> layout(int viewY, int maxCount) {
        int lineHeight = viewer.lineHeight();
        int[] starts = {};
        int count = 0;
        while (count < maxCount) {
            int[] around = startsAt(viewY + count * lineHeight);
            if (around.length <= count || !startsWith(around, starts, count)) {
                break;
            }
            count++;
            starts = Arrays.copyOf(around, count);
        }
        if (count == 0) {
            return List.of();
        }
        if (count == maxCount) {
            // too deep to show it all: the regions closest to the text are the ones that matter
            int[] around = startsAt(viewY + count * lineHeight);
            if (around.length > count && startsWith(around, starts, count)) {
                starts = Arrays.copyOfRange(around, around.length - count, around.length);
            }
        }

        TextModel model = viewer.getModel();
        int tabSize = viewer.getTabSize();
        List<Stuck> stuck = new ArrayList<>(count);
        int previousY = viewY - lineHeight;
        for (int i = 0; i < count; i++) {
            int y = Math.min(viewY + i * lineHeight, regionBottom(starts[i], viewY, count) - lineHeight);
            if (y > previousY) { // else it has gone up behind the line above it
                stuck.add(new Stuck(IndentFolding.foldHeader(model, starts[i], tabSize), y));
                previousY = y;
            }
        }
        return stuck;
    }

    /** How many lines stay above a line when it is right below them, for scrolling to it. */
    int countAbove(int line, int maxCount) {
        return Math.min(enclosing(line).length, maxCount);
    }

    /**
     * The regions to show for the text at a height: those around its line, and the region that
     * line starts once its top has gone past that height.
     */
    private int[] startsAt(int y) {
        RowIndex rows = viewer.rowIndex();
        int line = rows.lineAtRow(viewer.clampRow(viewer.rowAtY(y)));
        int[] around = enclosing(line);
        if (viewer.rowY(rows.firstRow(line)) < y && IndentFolding.isFoldStart(viewer.getModel(), line, viewer.getTabSize())) {
            around = Arrays.copyOf(around, around.length + 1);
            around[around.length - 1] = line;
        }
        return around;
    }

    private int[] enclosing(int line) {
        TextModel model = viewer.getModel();
        if (model != rememberedModel || viewer.layoutVersion() != rememberedVersion || remembered.size() >= MAX_REMEMBERED) {
            remembered.clear();
            rememberedModel = model;
            rememberedVersion = viewer.layoutVersion();
        }
        return remembered.computeIfAbsent(line, l -> IndentFolding.enclosingFoldStarts(model, l, viewer.getTabSize()));
    }

    private static boolean startsWith(int[] array, int[] prefix, int count) {
        return Arrays.equals(array, 0, count, prefix, 0, count);
    }

    /**
     * Where a region ends in pixels, if that is near enough to the top of the view to matter
     * to the lines stuck there; otherwise anything further down.
     */
    private int regionBottom(int start, int viewY, int count) {
        TextModel model = viewer.getModel();
        RowIndex rows = viewer.rowIndex();
        int tabSize = viewer.getTabSize();
        int indent = IndentFolding.indent(model, start, tabSize);
        int bottomRow = rows.firstRow(start) + rows.rowsOf(start);
        int topRow = viewer.clampRow(viewer.rowAtY(viewY));
        int rowCount = rows.rowCount();
        for (int row = topRow; row < rowCount; row++) {
            int line = rows.lineAtRow(row);
            if (line <= start) {
                continue;
            }
            int other = IndentFolding.indent(model, line, tabSize);
            if (other < 0) {
                continue; // blank lines belong to the region only if indented lines follow
            }
            if (other <= indent) {
                return viewer.rowY(bottomRow);
            }
            if (row > topRow + count) {
                return Integer.MAX_VALUE;
            }
            bottomRow = row + 1;
        }
        return viewer.rowY(bottomRow);
    }
}
