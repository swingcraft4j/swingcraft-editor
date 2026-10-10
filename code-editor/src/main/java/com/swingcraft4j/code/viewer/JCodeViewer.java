package com.swingcraft4j.code.viewer;

import com.swingcraft4j.code.folding.IndentFolding;
import com.swingcraft4j.code.layout.CellMeasure;
import com.swingcraft4j.code.layout.Cells;
import com.swingcraft4j.code.layout.RowIndex;
import com.swingcraft4j.code.lexer.Language;
import com.swingcraft4j.code.lexer.Lexer;
import com.swingcraft4j.code.lexer.BracketMatcher;
import com.swingcraft4j.code.lexer.IncrementalLineStates;
import com.swingcraft4j.code.lexer.LineStateIndex;
import com.swingcraft4j.code.lexer.LineStates;
import com.swingcraft4j.code.marker.Marker;
import com.swingcraft4j.code.marker.MarkerProvider;
import com.swingcraft4j.code.marker.Severity;
import com.swingcraft4j.code.search.TextSearch;
import com.swingcraft4j.code.text.ArrayTextModel;
import com.swingcraft4j.code.text.EditableTextModel;
import com.swingcraft4j.code.text.TextChange;
import com.swingcraft4j.code.text.TextListener;
import com.swingcraft4j.code.text.TextModel;
import com.swingcraft4j.code.theme.CodeTheme;
import com.swingcraft4j.code.theme.TokenStyle;

import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.InputMap;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JViewport;
import javax.swing.KeyStroke;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.ToolTipManager;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.font.FontRenderContext;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Read-only source code viewer with syntax highlighting, optional line wrapping and line
 * numbers. Put it in a {@link JScrollPane}; the line number gutter installs itself as the
 * scroll pane's row header.
 * <p>
 * Only the visible rows are measured, tokenized and painted, so the cost of showing a
 * document does not grow with its length. With a monospaced font the layout is a grid of
 * equal cells; with any other font every char is measured.
 */
public class JCodeViewer extends JComponent implements Scrollable {

    private static final int PAD_LEFT = 6;
    private static final int PAD_RIGHT = 6;
    private static final int MIN_WRAP_CELLS = 8;
    /** What stands for the hidden lines of a collapsed fold, and the space at each side of it. */
    private static final String FOLD_MARKER = "...";
    private static final int FOLD_MARKER_PAD = 4;
    /** Longer lines are shown without highlighting, to keep painting them cheap. */
    private static final int MAX_HIGHLIGHT_LINE = 20_000;

    /** Time spent bringing the highlighting up to date right after a change, and per timer tick. */
    private static final long HIGHLIGHT_SYNC_NANOS = 4_000_000;
    private static final long HIGHLIGHT_SLICE_NANOS = 10_000_000;

    /** How far a bracket's partner is looked for; further away it is simply not marked. */
    private static final int BRACKET_SEARCH_LINES = 20_000;
    private static final int SELECTION_RADIUS = 4;
    /** How long editing must pause before the marker provider is asked again, in milliseconds. */
    private static final int MARKER_DELAY = 350;

    public static final String ACTION_COPY = "copy";
    public static final String ACTION_SELECT_ALL = "select-all";
    public static final String ACTION_GO_TO_LINE = "go-to-line";
    /** There is no such action until a find bar is attached. */
    public static final String ACTION_FIND = "find";
    public static final String ACTION_FIND_NEXT = "find-next";
    public static final String ACTION_FIND_PREVIOUS = "find-previous";
    public static final String ACTION_COLLAPSE_FOLD = "collapse-fold";
    public static final String ACTION_EXPAND_FOLD = "expand-fold";
    public static final String ACTION_EXPAND_ALL_FOLDS = "expand-all-folds";

    private final LineNumberGutter gutter = new LineNumberGutter(this);
    private final TokenBuffer tokens = new TokenBuffer();
    private final Font[] fonts = new Font[4];
    /** Used for runs the main font has no glyphs for; logical fonts fall back across scripts. */
    private final Font[] fallbackFonts = new Font[4];
    private final GlyphMeasure glyphMeasure = new GlyphMeasure();
    private char[] lineBuffer = new char[256];

    private TextModel model = ArrayTextModel.of("");
    private RowIndex rows;
    private Language language;
    private Lexer lexer;
    private LineStateIndex lineStates;
    /** The same object as {@code lineStates} while the model is editable, else null. */
    private IncrementalLineStates incrementalStates;
    private final Timer highlightTimer = new Timer(15, e -> continueHighlighting(HIGHLIGHT_SLICE_NANOS));
    private final TextListener modelListener = this::modelChanged;
    private final List<ChangeListener> selectionListeners = new CopyOnWriteArrayList<>();
    private CodeTheme theme = CodeTheme.forLookAndFeel();
    private int tabSize = 4;
    private boolean lineWrap;
    private boolean lineNumbersVisible = true;

    private double cellWidth;
    /** Whether the font is monospaced, so that text lies on a grid of cells. */
    private boolean monospaced = true;
    /** Whether the font is the one of the Look and Feel rather than one that was set. */
    private boolean defaultFontInUse;
    /** How the text is measured: hinted, as it is drawn, at the scale of the screen it is on. */
    private FontRenderContext measureContext;
    private double metricsScale = 1;
    private int lineHeight;
    private int baseline;

    private int selectionAnchor;
    private int selectionCaret;
    private TextSearch search;

    /** The markers on show, ordered by where they start. */
    private Marker[] markers = new Marker[0];
    private int[] markerReach = new int[0];
    private MarkerProvider markerProvider;
    /** Asks the marker provider again once editing has paused. */
    private final Timer markerTimer = new Timer(MARKER_DELAY, e -> runMarkerProvider());

    private boolean bracketMatching = true;
    private final BracketMatcher bracketMatcher = new BracketMatcher();
    /** The bracket next to the caret and its partner, or -1 when there is no such pair. */
    private int bracketOffset = -1;
    private int bracketPartnerOffset = -1;

    private boolean foldingEnabled = true;
    /** The collapsed folds: the line each one starts at, mapped to its last hidden line. */
    private TreeMap<Integer, Integer> collapsedFolds = new TreeMap<>();

    // The part of the line being painted that lies inside the clip.
    /** Counts the changes to the layout, to tell when what is known of a line's rows is out of date. */
    private long layoutVersion;
    private final LineRows[] rowCache = {new LineRows(), new LineRows(), new LineRows()};
    private int rowCacheNext;
    private boolean wrapStyleWord;

    private int spanLineRow;
    private int spanRow0;
    private int spanRow1;
    private double spanCellFrom;
    private double spanCellTo;
    private final LineRows spanRows = new LineRows();
    private boolean spanPlain;
    private int spanClipLeft;
    private int spanClipRight;

    private boolean roundedSelection = true;
    private int bottomPadding;
    // Scratch space for working out the outline of a rounded selection.
    private final int[] extentRow = new int[2];
    private final int[] extentAbove = new int[2];
    private final int[] extentBelow = new int[2];
    private char[] extentBuffer = new char[256];
    private final Path2D.Double selectionPath = new Path2D.Double(Path2D.WIND_NON_ZERO);

    public JCodeViewer() {
        setOpaque(true);
        setFocusable(true);
        setAutoscrolls(true);
        setCursor(Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR));
        rows = createRowIndex();
        installDefaultFont();
        installMouse();
        installActions();
        installPopupMenu();
        markerTimer.setRepeats(false);
        // a move to a screen with another scale changes how wide the hinted glyphs are
        addPropertyChangeListener("graphicsConfiguration", event -> {
            if (screenScale() != metricsScale) {
                metricsChanged(topLine());
            }
        });
    }

    public JCodeViewer(CharSequence text) {
        this();
        setText(text);
    }

    /**
     * The font of the Look and Feel: its default font where it has one, as FlatLaf does under
     * the key <code>defaultFont</code>, else the font of its text areas.
     */
    private static Font defaultFont() {
        Font font = UIManager.getFont("defaultFont");
        if (font == null) {
            font = UIManager.getFont("TextArea.font");
        }
        return font != null ? font : new Font(Font.DIALOG, Font.PLAIN, 12);
    }

    /** Takes the font of the Look and Feel, and goes on following it until a font is set. */
    private void installDefaultFont() {
        setFont(defaultFont());
        defaultFontInUse = true;
    }

    // ---- Document ----

    public TextModel getModel() {
        return model;
    }

    public void setModel(TextModel model) {
        setDocument(model, language);
    }

    public void setText(CharSequence text) {
        setModel(ArrayTextModel.of(text));
    }

    public Language getLanguage() {
        return language;
    }

    /** Sets the language used for highlighting; null shows plain text. */
    public void setLanguage(Language language) {
        this.language = language;
        restartHighlighting();
        updateBracketMatch();
        runMarkerProvider();
        repaint();
    }

    /**
     * Replaces the model and the language in one step and scrolls back to the start. Another
     * model is reported to property change listeners as {@code "model"}; {@link #setText} and
     * {@link #setModel} come here too.
     */
    public void setDocument(TextModel model, Language language) {
        Objects.requireNonNull(model);
        TextModel old = this.model;
        if (this.model instanceof EditableTextModel editable) {
            editable.removeTextListener(modelListener);
        }
        this.model = model;
        this.language = language;
        if (model instanceof EditableTextModel editable) {
            editable.addTextListener(modelListener);
        }
        collapsedFolds.clear();
        rows = createRowIndex();
        restartHighlighting();
        relayout(0, false);
        select(0, 0);
        updateBracketMatch();
        markerTimer.stop();
        setMarkers(List.of());
        runMarkerProvider();
        firePropertyChange("model", old, model);
    }

    public int getLineCount() {
        return model.lineCount();
    }

    private void restartHighlighting() {
        if (lineStates != null) {
            lineStates.dispose();
        }
        highlightTimer.stop();
        lexer = language != null ? language.createLexer() : null;
        lineStates = null;
        incrementalStates = null;
        if (language == null) {
            return;
        }
        if (model instanceof EditableTextModel) {
            // An editable model is lexed in slices on this thread, never behind its back.
            incrementalStates = new IncrementalLineStates(model, language.createLexer());
            lineStates = incrementalStates;
            continueHighlighting(HIGHLIGHT_SYNC_NANOS);
        } else {
            lineStates = new LineStates(model, language.createLexer(), this::repaint);
        }
    }

    private void continueHighlighting(long nanos) {
        if (incrementalStates != null && incrementalStates.process(nanos)) {
            if (!highlightTimer.isRunning()) {
                highlightTimer.start();
            }
        } else {
            highlightTimer.stop();
        }
        repaint();
    }

    /** Brings the layout, the highlighting and the selection up to date after the model changed. */
    private void modelChanged(TextChange change) {
        layoutVersion++;
        int oldRowCount = rows.rowCount();
        int oldMaxCells = rows.maxCells();
        rows.linesReplaced(change.firstLine(), change.removedLines(), change.insertedLines());
        if (!collapsedFolds.isEmpty()) {
            moveFolds(change);
        }
        if (incrementalStates != null) {
            incrementalStates.linesReplaced(change.firstLine(), change.removedLines(), change.insertedLines());
            continueHighlighting(HIGHLIGHT_SYNC_NANOS);
        }
        int anchor = shifted(selectionAnchor, change);
        int caret = shifted(selectionCaret, change);
        if (markers.length > 0) {
            moveMarkers(change);
        }
        if (markerProvider != null) {
            markerTimer.restart();
        }
        boolean moved = anchor != selectionAnchor || caret != selectionCaret;
        selectionAnchor = anchor;
        selectionCaret = caret;
        expandFoldsAround(model.lineOfOffset(caret));
        updateBracketMatch();
        if (moved) {
            selectionChanged();
        }
        if (rows.rowCount() != oldRowCount || rows.maxCells() != oldMaxCells) {
            contentSizeChanged();
        }
        repaint();
        gutter.repaint();
    }

    /**
     * Resizes to the new content at once, so that a caret on a new last row can be scrolled to
     * without waiting for the next layout.
     */
    private void contentSizeChanged() {
        if (getParent() instanceof JViewport viewport) {
            Dimension preferred = getPreferredSize();
            int width = lineWrap ? getWidth() : Math.max(preferred.width, viewport.getWidth());
            setSize(width, Math.max(preferred.height, viewport.getHeight()));
        }
        revalidate();
        gutter.revalidate();
    }

    /** Where an offset ends up after a change. */
    private static int shifted(int offset, TextChange change) {
        if (offset <= change.offset()) {
            return offset;
        }
        if (offset >= change.offset() + change.removedLength()) {
            return offset + change.insertedLength() - change.removedLength();
        }
        return change.offset() + change.insertedLength();
    }

    // ---- Appearance ----

    public CodeTheme getTheme() {
        return theme;
    }

    /** Sets the colours. Another theme is reported to property change listeners as {@code "theme"}. */
    public void setTheme(CodeTheme theme) {
        CodeTheme old = this.theme;
        this.theme = Objects.requireNonNull(theme);
        repaint();
        gutter.repaint();
        firePropertyChange("theme", old, theme);
    }

    /**
     * Sets the font of the text. Until this is called the font is the default font of the Look
     * and Feel, and changes with it. A monospaced font lays text out on a grid of cells, which
     * is what keeps very long lines and very large documents cheap; any other font works too,
     * with every char measured.
     */
    @Override
    public void setFont(Font font) {
        int anchor = topLine();
        super.setFont(font != null ? font : defaultFont());
        defaultFontInUse = font == null;
        metricsChanged(anchor);
    }

    /** Measures the font again and lays the text out again, as after a change of font or screen. */
    private void metricsChanged(int anchorLine) {
        updateMetrics();
        // the width of text outside the basic Latin range depends on the font
        rows = createRowIndex();
        applyFolds();
        relayout(anchorLine, true);
        gutter.revalidate();
    }

    public int getTabSize() {
        return tabSize;
    }

    public void setTabSize(int tabSize) {
        if (tabSize < 1) {
            throw new IllegalArgumentException("tabSize must be at least 1: " + tabSize);
        }
        int anchor = topLine();
        this.tabSize = tabSize;
        collapsedFolds.clear(); // folds follow the indentation, which the tab size changes
        rows = createRowIndex();
        relayout(anchor, true);
    }

    private RowIndex createRowIndex() {
        RowIndex index = new RowIndex(model, tabSize, glyphMeasure);
        index.setWordWrap(wrapStyleWord);
        return index;
    }

    public boolean isWrapStyleWord() {
        return wrapStyleWord;
    }

    /**
     * Sets whether a wrapped line is broken after the last blank that fits instead of after
     * the last char; off by default. It reads better, but every line that wraps then has to
     * be read whenever the width changes, where otherwise the rows are plain arithmetic, so
     * a document with very many long lines re-wraps more slowly. Has no effect without
     * {@link #setLineWrap line wrap}.
     */
    public void setWrapStyleWord(boolean wrapStyleWord) {
        int anchor = topLine();
        this.wrapStyleWord = wrapStyleWord;
        rows.setWordWrap(wrapStyleWord);
        relayout(anchor, true);
    }

    public boolean isLineWrap() {
        return lineWrap;
    }

    /** Wraps lines at the viewport width instead of scrolling horizontally. */
    public void setLineWrap(boolean lineWrap) {
        int anchor = topLine();
        this.lineWrap = lineWrap;
        relayout(anchor, true);
    }

    public boolean isRoundedSelection() {
        return roundedSelection;
    }

    /** Whether the selection is drawn with rounded corners (the default) or as plain rectangles. */
    public void setRoundedSelection(boolean roundedSelection) {
        this.roundedSelection = roundedSelection;
        repaint();
    }

    public int getBottomPadding() {
        return bottomPadding;
    }

    /**
     * Sets the height in pixels of the empty space below the last line; none by default. With
     * it the end of the text can be scrolled up, out from under whatever lies over the bottom of
     * the view, such as a floating toolbar.
     */
    public void setBottomPadding(int bottomPadding) {
        if (bottomPadding < 0) {
            throw new IllegalArgumentException("bottomPadding must not be negative: " + bottomPadding);
        }
        this.bottomPadding = bottomPadding;
        revalidate();
        gutter.revalidate();
    }

    public boolean isLineNumbersVisible() {
        return lineNumbersVisible;
    }

    public void setLineNumbersVisible(boolean visible) {
        lineNumbersVisible = visible;
        configureEnclosingScrollPane();
    }

    // ---- Selection ----

    public int getSelectionStart() {
        return Math.min(selectionAnchor, selectionCaret);
    }

    public int getSelectionEnd() {
        return Math.max(selectionAnchor, selectionCaret);
    }

    public String getSelectedText() {
        return model.getText(getSelectionStart(), getSelectionEnd());
    }

    /** The end of the selection that stays put while the selection is extended. */
    public int getSelectionAnchor() {
        return selectionAnchor;
    }

    /** The end of the selection that moves while it is extended; an editor shows its caret here. */
    public int getCaretPosition() {
        return selectionCaret;
    }

    /** Selects from an anchor offset to a caret offset, which may come before the anchor. */
    public void select(int anchor, int caret) {
        anchor = Math.max(0, Math.min(anchor, model.length()));
        caret = Math.max(0, Math.min(caret, model.length()));
        if (anchor != selectionAnchor || caret != selectionCaret) {
            selectionAnchor = anchor;
            selectionCaret = caret;
            expandFoldsAround(model.lineOfOffset(caret));
            updateBracketMatch();
            selectionChanged();
        }
        repaint();
    }

    /** Called after the selection or the caret position changed; notifies the listeners. */
    protected void selectionChanged() {
        ChangeEvent event = new ChangeEvent(this);
        for (ChangeListener listener : selectionListeners) {
            listener.stateChanged(event);
        }
    }

    public void addSelectionListener(ChangeListener listener) {
        selectionListeners.add(listener);
    }

    public void removeSelectionListener(ChangeListener listener) {
        selectionListeners.remove(listener);
    }

    public void selectAll() {
        select(0, model.length());
    }

    /** Copies the selection to the system clipboard. */
    public void copy() {
        if (selectionAnchor != selectionCaret) {
            StringSelection contents = new StringSelection(getSelectedText());
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(contents, contents);
        }
    }

    // ---- Search and navigation ----

    public TextSearch getSearch() {
        return search;
    }

    /** Sets the query whose matches are marked in the text; null clears the marks. */
    public void setSearch(TextSearch search) {
        this.search = search;
        repaint();
    }

    /** Selects and shows the next match after the selection, wrapping round at the end. */
    public boolean findNext() {
        return find(getSelectionEnd(), true);
    }

    /** Selects and shows the previous match before the selection, wrapping round at the start. */
    public boolean findPrevious() {
        return find(getSelectionStart(), false);
    }

    /**
     * Selects and shows the first match at or after the start of the selection. Used while a
     * query is being typed, so that the current match grows instead of jumping ahead.
     */
    public boolean findFromSelectionStart() {
        return find(getSelectionStart(), true);
    }

    private boolean find(int from, boolean forward) {
        if (search == null) {
            return false;
        }
        TextSearch.Match match = forward ? search.findNext(model, from) : search.findPrevious(model, from);
        if (match == null && from != (forward ? 0 : model.length())) {
            // wrap round, unless the search already covered the whole document
            match = forward ? search.findNext(model, 0) : search.findPrevious(model, model.length());
        }
        if (match == null) {
            return false;
        }
        select(match.start(), match.end());
        reveal(match.start(), match.end());
        return true;
    }

    /** Moves to the start of the zero-based line and brings it into view. */
    public void goToLine(int line) {
        int offset = model.lineStart(Math.max(0, Math.min(line, model.lineCount() - 1)));
        select(offset, offset);
        reveal(offset, offset);
    }

    /** Asks for a line number and goes to it. */
    public void showGoToLineDialog() {
        String input = JOptionPane.showInputDialog(this, "Line number (1 - " + model.lineCount() + "):",
                "Go to Line", JOptionPane.QUESTION_MESSAGE);
        if (input == null) {
            return;
        }
        try {
            goToLine(Integer.parseInt(input.trim()) - 1);
        } catch (NumberFormatException e) {
            Toolkit.getDefaultToolkit().beep();
        }
    }

    /** Scrolls so that the zero-based line is at the top of the view. */
    public void scrollToLine(int line) {
        line = Math.max(0, Math.min(line, model.lineCount() - 1));
        int y = rows.firstRow(line) * lineHeight;
        if (getParent() instanceof JViewport viewport) {
            int maxY = Math.max(0, getHeight() - viewport.getExtentSize().height);
            viewport.setViewPosition(new Point(viewport.getViewPosition().x, Math.min(y, maxY)));
        } else {
            scrollRectToVisible(new Rectangle(0, y, 1, lineHeight));
        }
    }

    /** The cell of an offset, in component coordinates. */
    public Rectangle getOffsetBounds(int offset) {
        int line = model.lineOfOffset(offset);
        int start = model.lineStart(line);
        int index = Math.min(offset - start, model.lineLength(line));
        double cell = rows.isPlain(line) ? index
                : Cells.position(readChars(start, start + index), 0, index, tabSize, glyphMeasure);
        LineRows lineRows = lineRows(line);
        int rowInLine = lineRows.rowOf(cell);
        int row = rows.firstRow(line) + rowInLine;
        cell -= lineRows.start(rowInLine);
        return new Rectangle(PAD_LEFT + (int) (cell * cellWidth), row * lineHeight, (int) Math.ceil(cellWidth), lineHeight);
    }

    /**
     * Where the rows of one line begin, in cells from the start of the line. Broken after any
     * char, the rows are all of the wrap width and this is arithmetic; broken at words, the
     * line has to be read to tell.
     */
    private final class LineRows {

        private int line = -1;
        private long version = -1;
        private int count = 1;
        private int wrap;
        /** Where the rows begin when the line is broken at words, else null. */
        private double[] starts;
        private double[] buffer = new double[8];

        void load(int line) {
            this.line = line;
            version = layoutVersion;
            wrap = rows.wrapCells();
            count = wrap > 0 ? Math.max(1, rows.rowsOf(line)) : 1;
            starts = null;
            if (count > 1 && rows.isWordWrap()) {
                buffer = rows.wordRowStarts(line, buffer);
                starts = buffer;
            }
        }

        double start(int row) {
            return starts != null ? starts[row] : (double) row * wrap;
        }

        /** Where the row ends; the last row of a line has no end. */
        double end(int row) {
            return row + 1 < count ? start(row + 1) : Double.MAX_VALUE;
        }

        /** The row a cell is on; a cell at the very place a row begins is on that row. */
        int rowOf(double cell) {
            if (count == 1) {
                return 0;
            }
            if (starts == null) {
                return (int) Math.max(0, Math.min(cell / wrap, count - 1));
            }
            int low = 0;
            int high = count - 1;
            while (low < high) {
                int middle = (low + high + 1) >>> 1;
                if (starts[middle] <= cell) {
                    low = middle;
                } else {
                    high = middle - 1;
                }
            }
            return low;
        }
    }

    /** The rows of a line, from a few kept for the lines asked about last. */
    private LineRows lineRows(int line) {
        for (LineRows cached : rowCache) {
            if (cached.line == line && cached.version == layoutVersion) {
                return cached;
            }
        }
        LineRows loaded = rowCache[rowCacheNext];
        rowCacheNext = (rowCacheNext + 1) % rowCache.length;
        loaded.load(line);
        return loaded;
    }

    /** Scrolls a range into view, centring it vertically when it is outside the view. */
    private void reveal(int start, int end) {
        Rectangle target = getOffsetBounds(start);
        Rectangle visible = getVisibleRect();
        int margin = (int) (cellWidth * 4);
        int right = target.x + (int) (Math.min(end - start, 60) * cellWidth) + margin;
        target.x = Math.max(0, target.x - margin);
        target.width = Math.max(1, Math.min(right, getWidth()) - target.x);
        if (target.y < visible.y || target.y + lineHeight > visible.y + visible.height) {
            int top = target.y - (visible.height - lineHeight) / 2;
            target.y = Math.max(0, Math.min(top, getHeight() - visible.height));
            target.height = visible.height;
        }
        scrollRectToVisible(target);
    }

    // ---- Markers ----

    /** The markers on show, in the order of where they start. */
    public List<Marker> getMarkers() {
        return List.of(markers);
    }

    /**
     * Shows markers in the text: each is underlined with a wavy line in the colour of its
     * severity, marks its line in the gutter, and gives its message as a tooltip. They follow
     * the text when it is edited. An empty collection removes them all.
     */
    public void setMarkers(Collection<Marker> newMarkers) {
        int length = model.length();
        List<Marker> kept = new ArrayList<>();
        for (Marker marker : newMarkers) {
            if (marker.start() <= length) {
                kept.add(marker.end() <= length ? marker
                        : new Marker(marker.start(), length, marker.severity(), marker.message()));
            }
        }
        kept.sort(Comparator.comparingInt(Marker::start));
        boolean had = markers.length > 0;
        markers = kept.toArray(new Marker[0]);
        // the furthest any marker up to each one reaches, to find those touching a line quickly
        markerReach = new int[markers.length];
        int reach = 0;
        for (int i = 0; i < markers.length; i++) {
            reach = Math.max(reach, markers[i].end());
            markerReach[i] = reach;
        }
        if (had != markers.length > 0) {
            if (markers.length > 0) {
                ToolTipManager.sharedInstance().registerComponent(this);
            } else {
                ToolTipManager.sharedInstance().unregisterComponent(this);
            }
            gutter.revalidate(); // the gutter makes room for its marks only while there are any
        }
        repaint();
        gutter.repaint();
    }

    public MarkerProvider getMarkerProvider() {
        return markerProvider;
    }

    /**
     * Sets what finds the markers. It is asked at once, whenever another document or language
     * is shown, and a moment after each pause in editing. Null stops that and removes the markers.
     */
    public void setMarkerProvider(MarkerProvider provider) {
        markerProvider = provider;
        markerTimer.stop();
        if (provider == null) {
            setMarkers(List.of());
        } else {
            runMarkerProvider();
        }
    }

    private void runMarkerProvider() {
        if (markerProvider != null) {
            setMarkers(markerProvider.markers(model, language));
        }
    }

    /** Keeps the markers on their text after a change; one whose text is gone goes with it. */
    private void moveMarkers(TextChange change) {
        List<Marker> moved = new ArrayList<>(markers.length);
        for (Marker marker : markers) {
            int start = shifted(marker.start(), change);
            int end = Math.max(start, shifted(marker.end(), change));
            if (end > start || marker.start() == marker.end()) {
                moved.add(start == marker.start() && end == marker.end() ? marker
                        : new Marker(start, end, marker.severity(), marker.message()));
            }
        }
        setMarkers(moved);
    }

    /** The index of the first marker that can reach as far as the offset. */
    private int firstMarkerReaching(int offset) {
        int low = 0;
        int high = markers.length;
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (markerReach[middle] >= offset) {
                high = middle;
            } else {
                low = middle + 1;
            }
        }
        return low;
    }

    /** Whether any of the marker shows on the line: a part of its range, or its position if it has no length. */
    private static boolean isOnLine(Marker marker, int lineStart, int lineEnd) {
        return marker.start() == marker.end()
                ? marker.start() >= lineStart && marker.start() <= lineEnd
                : marker.start() <= lineEnd && marker.end() > lineStart;
    }

    private List<Marker> markersOnLine(int line) {
        int lineStart = model.lineStart(line);
        int lineEnd = model.lineEnd(line);
        List<Marker> found = new ArrayList<>();
        for (int i = firstMarkerReaching(lineStart); i < markers.length && markers[i].start() <= lineEnd; i++) {
            if (isOnLine(markers[i], lineStart, lineEnd)) {
                found.add(markers[i]);
            }
        }
        return found;
    }

    boolean hasMarkers() {
        return markers.length > 0;
    }

    /** The most serious severity among the markers on a line, or null if there are none. */
    Severity lineSeverity(int line) {
        Severity worst = null;
        for (Marker marker : markersOnLine(line)) {
            if (worst == null || marker.severity().compareTo(worst) < 0) {
                worst = marker.severity();
            }
        }
        return worst;
    }

    /** The messages of the markers on a line as a tooltip, or null if there are none. */
    String lineMarkerText(int line) {
        return toolTipFor(markersOnLine(line));
    }

    private static String toolTipFor(List<Marker> found) {
        if (found.isEmpty()) {
            return null;
        }
        StringBuilder html = new StringBuilder("<html>");
        for (int i = 0; i < found.size(); i++) {
            html.append(i > 0 ? "<br>" : "")
                    .append(found.get(i).message().replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"));
        }
        return html.append("</html>").toString();
    }

    /** The messages of the markers under the mouse. */
    @Override
    public String getToolTipText(MouseEvent event) {
        if (markers.length == 0) {
            return super.getToolTipText(event);
        }
        int row = Math.floorDiv(event.getY(), lineHeight);
        double cell = (event.getX() - PAD_LEFT) / cellWidth;
        if (row < 0 || row >= rows.rowCount() || cell < 0) {
            return null;
        }
        int line = rows.lineAtRow(row);
        cell += lineRows(line).start(row - rows.firstRow(line));
        if (cell >= rows.lineCells(line) + 1) {
            return null; // past the end of the line, allowing one cell for a marker placed there
        }
        int start = model.lineStart(line);
        int length = model.lineLength(line);
        int index = rows.isPlain(line) ? (int) cell
                : Cells.indexAtCell(readChars(start, start + length), 0, length, cell, tabSize, glyphMeasure);
        int offset = start + Math.min(index, length);
        List<Marker> found = new ArrayList<>();
        for (Marker marker : markersOnLine(line)) {
            boolean under = marker.start() == marker.end()
                    ? offset == marker.start()
                    : offset >= marker.start() && (offset < marker.end() || offset == start + length);
            if (under) {
                found.add(marker);
            }
        }
        return toolTipFor(found);
    }

    /** Underlines the parts of the markers that lie on the line being painted. */
    private void paintMarkers(Graphics2D g, char[] text, int lineStart, int length) {
        int lineEnd = lineStart + length;
        for (int i = firstMarkerReaching(lineStart); i < markers.length && markers[i].start() <= lineEnd; i++) {
            Marker marker = markers[i];
            if (!isOnLine(marker, lineStart, lineEnd)) {
                continue;
            }
            int a = Math.max(0, marker.start() - lineStart);
            int b = Math.min(length, marker.end() - lineStart);
            // A line that is not plain was read from its start, so its cells can be counted.
            double cellA = spanPlain ? a : Cells.position(text, 0, a, tabSize, glyphMeasure);
            // a position, or a range of which only the line break is on this line, gets one cell
            double cellB = b <= a ? cellA + 1 : spanPlain ? b : Cells.position(text, 0, b, tabSize, glyphMeasure);
            g.setColor(theme.markerColor(marker.severity()));
            underlineCells(g, cellA, cellB);
        }
    }

    /** Draws a wavy line under the cells {@code [cellA, cellB)} of the line being painted, in the current colour. */
    private void underlineCells(Graphics2D g, double cellA, double cellB) {
        for (int row = spanRow0; row <= spanRow1; row++) {
            double rowCell = spanRows.start(row);
            double lo = Math.max(cellA, Math.max(rowCell, spanCellFrom));
            double hi = Math.min(cellB, Math.min(spanRows.end(row), spanCellTo));
            if (lo >= hi) {
                continue;
            }
            int x1 = PAD_LEFT + (int) Math.round((lo - rowCell) * cellWidth);
            int x2 = PAD_LEFT + (int) Math.round((hi - rowCell) * cellWidth);
            int y = (spanLineRow + row) * lineHeight + baseline + 1;
            int points = (x2 - x1) / 2 + 1;
            int[] xs = new int[points];
            int[] ys = new int[points];
            for (int p = 0; p < points; p++) {
                xs[p] = x1 + p * 2;
                // up and down by where the point is, so that neighbouring pieces join up
                ys[p] = y + ((xs[p] >> 1 & 1) == 0 ? 0 : 2);
            }
            g.drawPolyline(xs, ys, points);
        }
    }

    // ---- Bracket matching ----

    public boolean isBracketMatching() {
        return bracketMatching;
    }

    /** Whether a bracket next to the caret and its partner are marked; on by default. */
    public void setBracketMatching(boolean bracketMatching) {
        this.bracketMatching = bracketMatching;
        updateBracketMatch();
    }

    /**
     * The offset of the bracket matching the one at the offset, or -1 when there is no bracket
     * there, it is inside a string or comment, or its partner is missing or very far away.
     */
    public int getMatchingBracket(int offset) {
        if (offset < 0 || offset >= model.length()) {
            return -1;
        }
        return bracketMatcher.match(model, offset, lexer, lineStates, BRACKET_SEARCH_LINES);
    }

    /** Looks for a bracket after the caret, or else before it, and for the bracket's partner. */
    private void updateBracketMatch() {
        int bracket = -1;
        int partner = -1;
        if (bracketMatching && selectionAnchor == selectionCaret) {
            int caret = selectionCaret;
            if (caret < model.length() && BracketMatcher.isBracket(model.charAt(caret))) {
                partner = getMatchingBracket(caret);
                bracket = caret;
            }
            if (partner < 0 && caret > 0 && BracketMatcher.isBracket(model.charAt(caret - 1))) {
                partner = getMatchingBracket(caret - 1);
                bracket = caret - 1;
            }
        }
        if (partner < 0) {
            bracket = -1;
        }
        if (bracket != bracketOffset || partner != bracketPartnerOffset) {
            bracketOffset = bracket;
            bracketPartnerOffset = partner;
            repaint();
        }
    }

    // ---- Folding ----

    public boolean isFoldingEnabled() {
        return foldingEnabled;
    }

    /** Whether blocks can be collapsed, with arrows for them in the gutter; on by default. */
    public void setFoldingEnabled(boolean foldingEnabled) {
        this.foldingEnabled = foldingEnabled;
        if (!foldingEnabled) {
            expandAllFolds();
        }
        gutter.revalidate();
        gutter.repaint();
    }

    /** Whether the zero-based line starts a block that can be collapsed, or is collapsed already. */
    public boolean isFoldable(int line) {
        return foldingEnabled && (collapsedFolds.containsKey(line) || IndentFolding.isFoldStart(model, line, tabSize));
    }

    public boolean isCollapsed(int line) {
        return collapsedFolds.containsKey(line);
    }

    public void toggleFold(int line) {
        if (isCollapsed(line)) {
            expandFold(line);
        } else {
            collapseFold(line);
        }
    }

    /** Hides the lines of the block starting at the line, which itself stays visible. */
    public void collapseFold(int line) {
        if (!foldingEnabled || isCollapsed(line)) {
            return;
        }
        int end = IndentFolding.foldEnd(model, line, tabSize);
        if (end <= line) {
            return;
        }
        // A caret left inside would open the fold again, so it waits at the end of the first line.
        int caretLine = model.lineOfOffset(selectionCaret);
        int anchorLine = model.lineOfOffset(selectionAnchor);
        if ((caretLine > line && caretLine <= end) || (anchorLine > line && anchorLine <= end)) {
            select(model.lineEnd(line), model.lineEnd(line));
        }
        collapsedFolds.put(line, end);
        foldsChanged();
    }

    public void expandFold(int line) {
        if (collapsedFolds.remove(line) != null) {
            foldsChanged();
        }
    }

    public void expandAllFolds() {
        if (!collapsedFolds.isEmpty()) {
            collapsedFolds.clear();
            foldsChanged();
        }
    }

    private void collapseFoldAtCaret() {
        int line = model.lineOfOffset(selectionCaret);
        if (!IndentFolding.isFoldStart(model, line, tabSize)) {
            line = IndentFolding.enclosingFoldStart(model, line, tabSize);
        }
        if (line >= 0) {
            collapseFold(line);
        }
    }

    private void expandFoldAtCaret() {
        expandFold(model.lineOfOffset(selectionCaret));
    }

    /** Opens every collapsed fold that hides the line, as when the caret or a search lands in it. */
    private void expandFoldsAround(int line) {
        if (collapsedFolds.isEmpty()) {
            return;
        }
        if (collapsedFolds.entrySet().removeIf(fold -> fold.getKey() < line && line <= fold.getValue())) {
            foldsChanged();
        }
    }

    /** Keeps the folds on their lines after a change, opening those the change touches. */
    private void moveFolds(TextChange change) {
        int changedFrom = model.lineOfOffset(change.offset());
        int changedTo = change.firstLine() + change.removedLines();
        int shift = change.insertedLines() - change.removedLines();
        TreeMap<Integer, Integer> kept = new TreeMap<>();
        collapsedFolds.forEach((start, end) -> {
            if (end < changedFrom) {
                kept.put(start, end);
            } else if (start > changedTo) {
                kept.put(start + shift, end + shift);
            }
        });
        collapsedFolds = kept;
        applyFolds();
    }

    private void foldsChanged() {
        int oldRowCount = rows.rowCount();
        applyFolds();
        if (rows.rowCount() != oldRowCount) {
            contentSizeChanged();
        }
        repaint();
        gutter.repaint();
    }

    private void applyFolds() {
        BitSet hidden = null;
        if (!collapsedFolds.isEmpty()) {
            hidden = new BitSet(model.lineCount());
            for (var fold : collapsedFolds.entrySet()) {
                hidden.set(fold.getKey() + 1, fold.getValue() + 1);
            }
        }
        rows.setHiddenLines(hidden);
    }

    // ---- Layout ----

    private void updateMetrics() {
        Font font = getFont();
        for (int style = 0; style < fonts.length; style++) {
            fonts[style] = font.deriveFont(style);
            fallbackFonts[style] = new Font(Font.MONOSPACED, style, font.getSize());
        }
        // Text is drawn hinted, which keeps it sharp but makes every glyph a whole number of
        // screen pixels wide. Measuring at the scale of the screen gives the cell exactly that
        // width, so a long line stays on the grid however the display is scaled.
        metricsScale = screenScale();
        measureContext = new FontRenderContext(AffineTransform.getScaleInstance(metricsScale, metricsScale),
                textAntialiasing(), RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
        cellWidth = font.getStringBounds("0000000000", measureContext).getWidth() / 10;
        // In a font that is not monospaced a cell is only the unit text is measured in.
        monospaced = true;
        for (String sample : new String[]{"iiiiiiiiii", "WWWWWWWWWW", "          ", ".........."}) {
            monospaced &= Math.abs(font.getStringBounds(sample, measureContext).getWidth() / 10 - cellWidth) < 0.01;
        }
        glyphMeasure.reset();
        FontMetrics metrics = getFontMetrics(font);
        lineHeight = metrics.getHeight() + 2;
        baseline = metrics.getLeading() + metrics.getAscent() + 1;
    }

    /**
     * Measures clusters outside the basic Latin range with the font they are drawn in. This is
     * what keeps the caret and the selection in step with scripts such as Khmer, whose glyphs
     * come from a fallback font and are not one cell wide.
     */
    private final class GlyphMeasure implements CellMeasure {

        private static final int MAX_CACHED_CLUSTERS = 50_000;

        /** The advance of each cluster of one char, or a negative number while not measured. */
        private final float[] singles = new float[0x10000];
        private final Map<String, Double> clusters = new HashMap<>();

        void reset() {
            Arrays.fill(singles, -1);
            clusters.clear();
        }

        @Override
        public boolean monospaced() {
            return monospaced;
        }

        @Override
        public double clusterAdvance(char[] text, int start, int end) {
            char first = text[start];
            if (Cells.width(first) == 0) {
                return 0; // a mark on its own, or the mark of a basic Latin letter
            }
            if (end - start == 1) {
                if (singles[first] < 0) {
                    singles[first] = (float) measure(text, start, end);
                }
                return singles[first];
            }
            String cluster = new String(text, start, end - start);
            Double cells = clusters.get(cluster);
            if (cells == null) {
                if (clusters.size() >= MAX_CACHED_CLUSTERS) {
                    clusters.clear();
                }
                cells = measure(text, start, end);
                clusters.put(cluster, cells);
            }
            return cells;
        }

        /** The advance of a cluster in cells, as drawn in the plain font. */
        private double measure(char[] text, int start, int end) {
            Font font = fonts[Font.PLAIN];
            if (font.canDisplayUpTo(text, start, end) >= 0) {
                font = fallbackFonts[Font.PLAIN];
            }
            double advance = font.getStringBounds(text, start, end, measureContext).getWidth();
            return Math.max(0, Math.min(100, advance / cellWidth));
        }
    }

    /** The scale of the screen the component is on, or of the main screen while it is on none. */
    private double screenScale() {
        GraphicsConfiguration configuration = getGraphicsConfiguration();
        if (configuration == null && !GraphicsEnvironment.isHeadless()) {
            configuration = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration();
        }
        return configuration != null ? configuration.getDefaultTransform().getScaleX() : 1;
    }

    /** The antialiasing the desktop uses for text, so that code looks like the text around it. */
    private static Object textAntialiasing() {
        Object desktopHints = Toolkit.getDefaultToolkit().getDesktopProperty("awt.font.desktophints");
        if (desktopHints instanceof Map<?, ?> hints && hints.get(RenderingHints.KEY_TEXT_ANTIALIASING) != null) {
            return hints.get(RenderingHints.KEY_TEXT_ANTIALIASING);
        }
        return RenderingHints.VALUE_TEXT_ANTIALIAS_ON;
    }

    private int wrapCellsFor(int width) {
        if (!lineWrap) {
            return 0;
        }
        int cells = (int) ((width - PAD_LEFT - PAD_RIGHT) / cellWidth);
        if (!monospaced && !wrapStyleWord) {
            cells -= 2; // room for the char that begins inside a row and ends past it
        }
        return Math.max(MIN_WRAP_CELLS, cells);
    }

    private int clampRow(int row) {
        return Math.max(0, Math.min(row, rows.rowCount() - 1));
    }

    /** The line at the top of the visible area. */
    private int topLine() {
        if (lineHeight == 0 || !(getParent() instanceof JViewport)) {
            return 0;
        }
        return rows.lineAtRow(clampRow(-getY() / lineHeight));
    }

    private void relayout(int anchorLine, boolean keepX) {
        Container parent = getParent();
        layoutVersion++;
        rows.setWrapCells(wrapCellsFor(parent instanceof JViewport ? parent.getWidth() : getWidth()));
        revalidate();
        repaint();
        gutter.revalidate();
        gutter.repaint();
        if (parent instanceof JViewport viewport) {
            int line = Math.min(anchorLine, model.lineCount() - 1);
            int x = keepX && !lineWrap ? viewport.getViewPosition().x : 0;
            // A position past the old size is fine: the pending layout clamps it to the new one.
            viewport.setViewPosition(new Point(x, rows.firstRow(line) * lineHeight));
        }
    }

    @Override
    public void setBounds(int x, int y, int width, int height) {
        int cells = wrapCellsFor(width);
        if (lineWrap && rows != null && cells != rows.wrapCells()) {
            // The width changed under wrapping: re-wrap, keeping the text at the top in place.
            int top = Math.max(0, -getY());
            int topRow = clampRow(top / lineHeight);
            int line = rows.lineAtRow(topRow);
            double cell = lineRows(line).start(topRow - rows.firstRow(line));
            layoutVersion++;
            rows.setWrapCells(cells);
            int row = rows.firstRow(line) + lineRows(line).rowOf(cell);
            if (getParent() instanceof JViewport viewport) {
                height = Math.max(rows.rowCount() * lineHeight + bottomPadding, viewport.getHeight());
                int newTop = row * lineHeight + top % lineHeight;
                y = -Math.max(0, Math.min(newTop, height - viewport.getHeight()));
            }
            revalidate();
            repaint();
            gutter.revalidate();
        }
        super.setBounds(x, y, width, height);
    }

    @Override
    public Dimension getPreferredSize() {
        if (isPreferredSizeSet()) {
            return super.getPreferredSize();
        }
        int cells = lineWrap ? MIN_WRAP_CELLS : rows.maxCells();
        return new Dimension(PAD_LEFT + (int) Math.ceil(cells * cellWidth) + PAD_RIGHT,
                rows.rowCount() * lineHeight + bottomPadding);
    }

    @Override
    public Dimension getPreferredScrollableViewportSize() {
        return new Dimension(PAD_LEFT + (int) Math.ceil(80 * cellWidth) + PAD_RIGHT, 25 * lineHeight);
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) {
        return orientation == SwingConstants.VERTICAL ? lineHeight : (int) Math.ceil(cellWidth * 4);
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) {
        return orientation == SwingConstants.VERTICAL
                ? Math.max(lineHeight, visible.height - lineHeight)
                : Math.max(1, visible.width - (int) (cellWidth * 4));
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
        return lineWrap || getParent().getWidth() > getPreferredSize().width;
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
        return getParent().getHeight() > getPreferredSize().height;
    }

    /** Also restyles the popup menu, which a Look and Feel change does not reach while it is hidden. */
    @Override
    public void updateUI() {
        super.updateUI();
        if (defaultFontInUse) {
            installDefaultFont();
        }
        JPopupMenu menu = getComponentPopupMenu();
        if (menu != null) {
            SwingUtilities.updateComponentTreeUI(menu);
        }
    }

    // ---- Scroll pane integration ----

    @Override
    public void addNotify() {
        super.addNotify();
        configureEnclosingScrollPane();
    }

    @Override
    public void removeNotify() {
        JScrollPane scrollPane = enclosingScrollPane();
        if (scrollPane != null && scrollPane.getRowHeader() != null && scrollPane.getRowHeader().getView() == gutter) {
            scrollPane.setRowHeaderView(null);
        }
        super.removeNotify();
    }

    private JScrollPane enclosingScrollPane() {
        if (getParent() instanceof JViewport viewport && viewport.getParent() instanceof JScrollPane scrollPane
                && scrollPane.getViewport() == viewport) {
            return scrollPane;
        }
        return null;
    }

    private void configureEnclosingScrollPane() {
        JScrollPane scrollPane = enclosingScrollPane();
        if (scrollPane == null) {
            return;
        }
        boolean installed = scrollPane.getRowHeader() != null && scrollPane.getRowHeader().getView() == gutter;
        if (lineNumbersVisible != installed) {
            scrollPane.setRowHeaderView(lineNumbersVisible ? gutter : null);
        }
    }

    // ---- Used by the gutter ----

    /** The height of one row of text. */
    public int getRowHeight() {
        return lineHeight;
    }

    RowIndex rowIndex() {
        return rows;
    }

    double cellWidth() {
        return cellWidth;
    }

    int lineHeight() {
        return lineHeight;
    }

    int baseline() {
        return baseline;
    }

    void applyTextHints(Graphics2D g) {
        Object desktopHints = Toolkit.getDefaultToolkit().getDesktopProperty("awt.font.desktophints");
        if (desktopHints instanceof Map<?, ?> hints) {
            g.addRenderingHints(hints);
        } else {
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        }
        // Hinted glyphs: unhinted ones are soft, and smear light pixels along their edges.
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
    }

    // ---- Painting ----

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            Rectangle clip = g.getClipBounds();
            g.setColor(theme.background());
            g.fillRect(clip.x, clip.y, clip.width, clip.height);
            spanClipLeft = clip.x;
            spanClipRight = clip.x + clip.width;
            paintBackgroundLayer(g, clip);
            applyTextHints(g);

            int firstRow = Math.max(0, clip.y / lineHeight);
            int lastRow = Math.min(rows.rowCount() - 1, (clip.y + clip.height - 1) / lineHeight);
            if (firstRow > lastRow) {
                return;
            }
            int lineCount = model.lineCount();
            for (int line = rows.lineAtRow(firstRow); line < lineCount; line++) {
                int lineRow = rows.firstRow(line);
                if (lineRow > lastRow) {
                    break;
                }
                if (rows.isHidden(line)) {
                    continue;
                }
                paintLine(g, clip, line, lineRow, firstRow, lastRow);
                if (collapsedFolds.containsKey(line)) {
                    paintFoldMarker(g, line);
                }
            }
        } finally {
            g.dispose();
        }
    }

    /** Paints between the background and the text. Does nothing here; an editor marks its current line. */
    protected void paintBackgroundLayer(Graphics2D g, Rectangle clip) {
    }

    private void paintLine(Graphics2D g, Rectangle clip, int line, int lineRow, int firstRow, int lastRow) {
        boolean wrapping = rows.wrapCells() > 0;
        int lineStart = model.lineStart(line);
        int length = model.lineLength(line);

        // The rows of this line, and the cells within them, that fall inside the clip.
        spanLineRow = lineRow;
        spanRow0 = Math.max(0, firstRow - lineRow);
        spanRows.load(line);
        spanRow1 = wrapping ? Math.min(spanRows.count - 1, lastRow - lineRow) : 0;
        if (wrapping) {
            spanCellFrom = spanRows.start(spanRow0);
            spanCellTo = spanRows.end(spanRow1);
        } else {
            spanCellFrom = Math.max(0, (int) ((clip.x - PAD_LEFT) / cellWidth));
            spanCellTo = (int) ((clip.x + clip.width - PAD_LEFT) / cellWidth) + 1;
        }
        spanPlain = rows.isPlain(line);

        int state = lexer != null && length <= MAX_HIGHLIGHT_LINE ? lineStates.startState(line) : -1;
        // Tokenizing or measuring cells needs the line from its start; otherwise the visible
        // chars are enough, which keeps very long lines cheap.
        int from = 0;
        int to = length;
        if (spanPlain && state < 0) {
            from = (int) Math.min(length, spanCellFrom);
            to = (int) Math.min(length, Math.ceil(spanCellTo));
        }
        char[] text = readChars(lineStart + from, lineStart + to);
        int count = to - from;

        if (search != null) {
            // On a line read only in part, a match cut by the edge of the view is not marked.
            int shift = from;
            g.setColor(theme.searchBackground());
            search.findInLine(text, count, (start, end) -> fillCells(g, text, start + shift, end + shift, false));
        }
        if (bracketOffset >= 0) {
            g.setColor(theme.bracketMatchBackground());
            if (bracketOffset >= lineStart && bracketOffset < lineStart + length) {
                fillCells(g, text, bracketOffset - lineStart, bracketOffset - lineStart + 1, false);
            }
            if (bracketPartnerOffset >= lineStart && bracketPartnerOffset < lineStart + length) {
                fillCells(g, text, bracketPartnerOffset - lineStart, bracketPartnerOffset - lineStart + 1, false);
            }
        }
        int selectionStart = getSelectionStart();
        int selectionEnd = getSelectionEnd();
        if (selectionStart < selectionEnd && selectionEnd > lineStart && selectionStart <= lineStart + length) {
            g.setColor(theme.selectionBackground());
            if (roundedSelection) {
                paintRoundedSelection(g, lineRow + spanRow0, lineRow + spanRow1);
            } else {
                fillCells(g, text, Math.max(0, selectionStart - lineStart), Math.min(length, selectionEnd - lineStart),
                        selectionEnd > lineStart + length);
            }
        }

        tokens.clear();
        if (state >= 0) {
            lexer.tokenize(text, 0, count, state, tokens);
        }

        if (monospaced) {
            drawOnGrid(g, text, count, from);
        } else {
            drawMeasured(g, text, count);
        }
        if (markers.length > 0) {
            paintMarkers(g, text, lineStart, length);
        }
    }

    /**
     * Draws the text of the line being painted in a font that is not monospaced: each cluster
     * where it was measured to be. Plain basic Latin text is as wide drawn in one piece as
     * measured char by char, so a run of it is one piece; a bold or italic glyph has another
     * width than the plain one that was measured, so such text is placed a cluster at a time.
     */
    private void drawMeasured(Graphics2D g, char[] text, int count) {
        double position = 0;
        int runStart = 0;
        int runLength = 0;
        double runPosition = 0;
        int runRow = 0;
        TokenStyle runStyle = null;
        int i = 0;
        while (i < count && position < spanCellTo) {
            char c = text[i];
            if (c == '\t') {
                if (runLength > 0) {
                    drawRun(g, text, runStart, runLength, runStyle, true, runRow, runPosition, position);
                    runLength = 0;
                }
                position = Cells.tabStop(position, tabSize);
                i++;
                continue;
            }
            int next = Cells.clusterEnd(text, i, count);
            double after = position + glyphMeasure.clusterAdvance(text, i, next);
            TokenStyle style = theme.style(tokens.typeAt(i));
            int row = spanRows.rowOf(position);
            boolean joins = next == i + 1 && c >= 0x20 && c < 0x7F && (style.fontStyle() & 3) == Font.PLAIN;
            if (runLength > 0 && (!joins || style != runStyle || row != runRow)) {
                drawRun(g, text, runStart, runLength, runStyle, true, runRow, runPosition, position);
                runLength = 0;
            }
            if (!joins) {
                drawRun(g, text, i, next - i, style, false, row, position, after);
            } else {
                if (runLength == 0) {
                    runStart = i;
                    runPosition = position;
                    runRow = row;
                    runStyle = style;
                }
                runLength++;
            }
            position = after;
            i = next;
        }
        if (runLength > 0) {
            drawRun(g, text, runStart, runLength, runStyle, true, runRow, runPosition, position);
        }
    }

    /** Draws the text of the line being painted in a monospaced font, from the cell {@code from} on. */
    private void drawOnGrid(Graphics2D g, char[] text, int count, int from) {
        int cell = from;
        int runStart = 0;
        int runLength = 0;
        int runCell = 0;
        int runRow = 0;
        boolean runAscii = true;
        TokenStyle runStyle = null;
        for (int i = 0; i < count && cell < spanCellTo; i++) {
            char c = text[i];
            if (c == '\t') {
                if (runLength > 0) {
                    drawRun(g, text, runStart, runLength, runStyle, runAscii, runRow, runCell, cell);
                    runLength = 0;
                }
                cell += tabSize - cell % tabSize;
                continue;
            }
            TokenStyle style = theme.style(tokens.typeAt(i));
            int row = spanRows.rowOf(cell);
            boolean marked = c < 0x300 && i + 1 < count && text[i + 1] >= 0x300
                    && Cells.isClusterContinuation(c, text[i + 1]);
            if (c >= 0x300 || marked) {
                if (runLength > 0) {
                    drawRun(g, text, runStart, runLength, runStyle, runAscii, runRow, runCell, cell);
                    runLength = 0;
                }
                if (marked) {
                    // a basic Latin letter with combining marks, drawn together onto its one cell
                    int next = Cells.clusterEnd(text, i, count);
                    drawRun(g, text, i, next - i, style, false, row, cell, cell + 1);
                    cell++;
                    i = next - 1;
                    continue;
                }
                // A run of another script. Each cluster is drawn on its own, at the width it
                // was measured to have, right after the one before; the run as a whole is
                // rounded up to whole cells, which puts the text after it back on the grid.
                double position = cell;
                int next = i;
                while (next < count && text[next] >= 0x300 && position < spanCellTo) {
                    int clusterStart = next;
                    next = Cells.clusterEnd(text, clusterStart, count);
                    double advance = glyphMeasure.clusterAdvance(text, clusterStart, next);
                    int clusterRow = spanRows.rowOf(position);
                    drawRun(g, text, clusterStart, next - clusterStart, theme.style(tokens.typeAt(clusterStart)), false,
                            clusterRow, position, position + advance);
                    position += advance;
                }
                cell = Cells.roundUp(position);
                i = next - 1;
                continue;
            }
            boolean ascii = c >= 0x20 && c < 0x7F;
            if (runLength > 0 && (style != runStyle || ascii != runAscii || row != runRow)) {
                drawRun(g, text, runStart, runLength, runStyle, runAscii, runRow, runCell, cell);
                runLength = 0;
            }
            if (runLength == 0) {
                runStart = i;
                runCell = cell;
                runRow = row;
                runAscii = ascii;
                runStyle = style;
            }
            runLength++;
            cell++;
        }
        if (runLength > 0) {
            drawRun(g, text, runStart, runLength, runStyle, runAscii, runRow, runCell, cell);
        }
    }

    private void drawRun(Graphics2D g, char[] text, int start, int length, TokenStyle style, boolean ascii,
                         int row, double cell, double endCell) {
        if (row < spanRow0 || row > spanRow1 || endCell <= spanCellFrom) {
            return;
        }
        g.setColor(style.color());
        Font font = fonts[style.fontStyle() & 3];
        if (!ascii && font.canDisplayUpTo(text, start, start + length) >= 0) {
            font = fallbackFonts[style.fontStyle() & 3];
        }
        g.setFont(font);
        // Translating by the whole pixels keeps the float coordinate small, so a run far along
        // a very long line is still placed exactly.
        double x = PAD_LEFT + (cell - spanRows.start(row)) * cellWidth;
        int wholeX = (int) x;
        g.translate(wholeX, 0);
        g.drawString(new String(text, start, length), (float) (x - wholeX), (spanLineRow + row) * lineHeight + baseline);
        g.translate(-wholeX, 0);
    }

    /**
     * Fills the background of chars {@code [a, b)} of the line being painted, in the current
     * colour. The indexes are relative to the start of the line.
     *
     * @param lineBreak whether to extend the fill by one cell for the line break
     */
    private void fillCells(Graphics2D g, char[] text, int a, int b, boolean lineBreak) {
        // A line that is not plain was read from its start, so its cells can be counted.
        double cellA = spanPlain ? a : Cells.position(text, 0, a, tabSize, glyphMeasure);
        double cellB = spanPlain ? b : Cells.position(text, 0, b, tabSize, glyphMeasure);
        if (lineBreak) {
            cellB++;
        }
        for (int row = spanRow0; row <= spanRow1; row++) {
            double rowCell = spanRows.start(row);
            double lo = Math.max(cellA, Math.max(rowCell, spanCellFrom));
            double hi = Math.min(cellB, Math.min(spanRows.end(row), spanCellTo));
            if (lo < hi) {
                int x1 = PAD_LEFT + (int) Math.round((lo - rowCell) * cellWidth);
                int x2 = PAD_LEFT + (int) Math.round((hi - rowCell) * cellWidth);
                g.fillRect(x1, (spanLineRow + row) * lineHeight, x2 - x1, lineHeight);
            }
        }
    }

    /**
     * Fills the selection on the given rows as one smooth outline. An outer corner is rounded;
     * where two rows of different width meet, the inner corner is rounded the other way, so
     * the edge runs from one row into the next in an S-curve. Where the edge carries on
     * straight into the next row there is no corner at all.
     */
    private void paintRoundedSelection(Graphics2D g, int firstRow, int lastRow) {
        Object antialiasing = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int[] row = extentRow;
        int[] above = extentAbove;
        int[] below = extentBelow;
        Path2D.Double path = selectionPath;
        for (int r = firstRow; r <= lastRow; r++) {
            if (!selectionExtent(r, row)) {
                continue;
            }
            double left = row[0];
            double right = row[1];
            double top = r * lineHeight;
            double bottom = top + lineHeight;
            // a neighbouring row matters only if it touches this one
            boolean hasAbove = r > 0 && selectionExtent(r - 1, above) && above[0] < right && above[1] > left;
            boolean hasBelow = r + 1 < rows.rowCount() && selectionExtent(r + 1, below) && below[0] < right && below[1] > left;
            double limit = Math.min(SELECTION_RADIUS, Math.min((right - left) / 2, lineHeight / 2.0));

            // Outer corners. Each is zero where the neighbour covers it, and no more than half
            // the step to the neighbour's edge, which leaves the other half for the inner corner.
            double topLeft = hasAbove ? outerRadius(above[0] - left, limit) : limit;
            double topRight = hasAbove ? outerRadius(right - above[1], limit) : limit;
            double bottomLeft = hasBelow ? outerRadius(below[0] - left, limit) : limit;
            double bottomRight = hasBelow ? outerRadius(right - below[1], limit) : limit;
            path.reset();
            path.moveTo(left + topLeft, top);
            path.lineTo(right - topRight, top);
            path.quadTo(right, top, right, top + topRight);
            path.lineTo(right, bottom - bottomRight);
            path.quadTo(right, bottom, right - bottomRight, bottom);
            path.lineTo(left + bottomLeft, bottom);
            path.quadTo(left, bottom, left, bottom - bottomLeft);
            path.lineTo(left, top + topLeft);
            path.quadTo(left, top, left + topLeft, top);
            path.closePath();

            // Inner corners, beside this row where a neighbour reaches further out.
            if (hasAbove) {
                addInnerCorner(path, left, top, -innerRadius(left - above[0]), 1);
                addInnerCorner(path, right, top, innerRadius(above[1] - right), 1);
            }
            if (hasBelow) {
                addInnerCorner(path, left, bottom, -innerRadius(left - below[0]), -1);
                addInnerCorner(path, right, bottom, innerRadius(below[1] - right), -1);
            }
            g.fill(path);
        }
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, antialiasing);
    }

    /** The radius of an outer corner that sticks out past the neighbouring row by {@code step}. */
    private static double outerRadius(double step, double limit) {
        return step <= 0 ? 0 : Math.min(limit, step / 2);
    }

    /** The radius of an inner corner where the neighbouring row sticks out by {@code step}; zero for none. */
    private static double innerRadius(double step) {
        return step <= 0 ? 0 : Math.min(SELECTION_RADIUS, step / 2);
    }

    /**
     * Adds the small filled shape that rounds an inner corner at a point on this row's edge.
     *
     * @param dx the radius, negative to lie to the left of the point
     * @param dy 1 to lie below the point, -1 to lie above it
     */
    private static void addInnerCorner(Path2D.Double path, double x, double y, double dx, int dy) {
        if (dx == 0) {
            return;
        }
        double radius = Math.abs(dx);
        path.moveTo(x, y);
        path.lineTo(x + dx, y);
        path.quadTo(x, y, x, y + dy * radius);
        path.closePath();
    }

    /**
     * The horizontal extent of the selection on a row, in pixels, kept near the clip so that
     * a very long line cannot overflow.
     *
     * @return false when nothing on the row is selected
     */
    private boolean selectionExtent(int row, int[] extent) {
        int line = rows.lineAtRow(row);
        int lineStart = model.lineStart(line);
        int length = model.lineLength(line);
        int selectionStart = getSelectionStart();
        int selectionEnd = getSelectionEnd();
        if (selectionStart == selectionEnd || selectionEnd <= lineStart || selectionStart > lineStart + length) {
            return false;
        }
        int a = Math.max(0, selectionStart - lineStart);
        int b = Math.min(length, selectionEnd - lineStart);
        double cellA = a;
        double cellB = b;
        if (!rows.isPlain(line)) {
            if (b > extentBuffer.length) {
                extentBuffer = new char[Math.max(b, extentBuffer.length * 2)];
            }
            model.getChars(lineStart, lineStart + b, extentBuffer, 0);
            cellA = Cells.position(extentBuffer, 0, a, tabSize, glyphMeasure);
            cellB = Cells.position(extentBuffer, 0, b, tabSize, glyphMeasure);
        }
        if (selectionEnd > lineStart + length) {
            cellB++; // the line break is selected too
        }
        LineRows lineRows = lineRows(line);
        int rowInLine = row - rows.firstRow(line);
        double rowCell = lineRows.start(rowInLine);
        double lo = Math.max(cellA, rowCell);
        double hi = Math.min(cellB, lineRows.end(rowInLine));
        if (lo >= hi) {
            return false;
        }
        double min = spanClipLeft - 100;
        double max = spanClipRight + 100;
        extent[0] = (int) Math.round(Math.max(min, Math.min(max, PAD_LEFT + (lo - rowCell) * cellWidth)));
        extent[1] = (int) Math.round(Math.max(min, Math.min(max, PAD_LEFT + (hi - rowCell) * cellWidth)));
        return extent[0] < extent[1];
    }

    /** Where the mark stands that is shown after the line starting a collapsed fold, in place of the hidden lines. */
    private Rectangle foldMarkerBounds(int line) {
        int cells = rows.lineCells(line);
        LineRows lineRows = lineRows(line);
        int row = lineRows.rowOf(cells);
        int x = PAD_LEFT + (int) ((cells - lineRows.start(row) + 1) * cellWidth);
        int y = (rows.firstRow(line) + row) * lineHeight;
        // as wide as the dots are in this font: three cells are too wide where a dot is narrow
        int width = getFontMetrics(fonts[Font.PLAIN]).stringWidth(FOLD_MARKER) + 2 * FOLD_MARKER_PAD;
        return new Rectangle(x, y + 2, width, lineHeight - 4);
    }

    private void paintFoldMarker(Graphics2D g, int line) {
        Rectangle mark = foldMarkerBounds(line);
        g.setColor(theme.foldMarkerBackground());
        g.fillRoundRect(mark.x, mark.y, mark.width, mark.height, 6, 6);
        g.setColor(theme.foldMarkerBorder());
        g.drawRoundRect(mark.x, mark.y, mark.width, mark.height, 6, 6);
        g.setColor(theme.gutterForeground());
        g.setFont(fonts[Font.PLAIN]);
        g.drawString(FOLD_MARKER, mark.x + FOLD_MARKER_PAD, mark.y - 2 + baseline);
    }

    /** The line whose mark of a collapsed fold is at the point, or -1. */
    private int foldMarkerAt(int x, int y) {
        int row = Math.floorDiv(y, lineHeight);
        if (collapsedFolds.isEmpty() || row < 0 || row >= rows.rowCount()) {
            return -1;
        }
        int line = rows.lineAtRow(row);
        return collapsedFolds.containsKey(line) && foldMarkerBounds(line).contains(x, y) ? line : -1;
    }

    private char[] readChars(int start, int end) {
        int count = end - start;
        if (count > lineBuffer.length) {
            lineBuffer = new char[Math.max(count, lineBuffer.length * 2)];
        }
        model.getChars(start, end, lineBuffer, 0);
        return lineBuffer;
    }

    // ---- Mouse and keyboard ----

    /** The document offset nearest to a point in component coordinates. */
    public int offsetAt(int x, int y) {
        int row = clampRow(Math.floorDiv(y, lineHeight));
        int line = rows.lineAtRow(row);
        double cell = Math.max(0, (x - PAD_LEFT) / cellWidth);
        // a click past the end of a row counts as one at its end
        LineRows lineRows = lineRows(line);
        int rowInLine = row - rows.firstRow(line);
        cell = lineRows.start(rowInLine) + Math.min(cell, lineRows.end(rowInLine) - lineRows.start(rowInLine));
        int start = model.lineStart(line);
        int length = model.lineLength(line);
        if (rows.isPlain(line)) {
            return start + (int) Math.min(Math.floor(cell + 0.5), length);
        }
        char[] text = readChars(start, start + length);
        return start + Cells.indexNear(text, 0, length, cell, tabSize, glyphMeasure);
    }

    private void installMouse() {
        MouseAdapter handler = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (!SwingUtilities.isLeftMouseButton(e)) {
                    return;
                }
                requestFocusInWindow();
                int collapsed = foldMarkerAt(e.getX(), e.getY());
                if (collapsed >= 0) {
                    // a click on the mark of a collapsed fold shows its lines again
                    expandFold(collapsed);
                    setCursor(Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR));
                    return;
                }
                int offset = offsetAt(e.getX(), e.getY());
                if (e.getClickCount() == 2) {
                    selectWord(offset);
                } else if (e.getClickCount() >= 3) {
                    selectLine(offset);
                } else if (e.isShiftDown()) {
                    select(selectionAnchor, offset);
                } else {
                    select(offset, offset);
                }
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                int cursor = foldMarkerAt(e.getX(), e.getY()) >= 0 ? Cursor.HAND_CURSOR : Cursor.TEXT_CURSOR;
                if (getCursor().getType() != cursor) {
                    setCursor(Cursor.getPredefinedCursor(cursor));
                }
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (!SwingUtilities.isLeftMouseButton(e)) {
                    return;
                }
                select(selectionAnchor, offsetAt(e.getX(), e.getY()));
                scrollRectToVisible(new Rectangle(e.getX(), e.getY(), 1, 1));
            }
        };
        addMouseListener(handler);
        addMouseMotionListener(handler);
    }

    private void selectWord(int offset) {
        int line = model.lineOfOffset(offset);
        int start = model.lineStart(line);
        int length = model.lineLength(line);
        char[] text = readChars(start, start + length);
        int index = Math.min(offset - start, length);
        if (index == length || !Character.isJavaIdentifierPart(text[index])) {
            if (index > 0 && Character.isJavaIdentifierPart(text[index - 1])) {
                index--;
            } else {
                select(start + index, start + Math.min(index + 1, length));
                return;
            }
        }
        int lo = index;
        int hi = index + 1;
        while (lo > 0 && Character.isJavaIdentifierPart(text[lo - 1])) {
            lo--;
        }
        while (hi < length && Character.isJavaIdentifierPart(text[hi])) {
            hi++;
        }
        select(start + lo, start + hi);
    }

    private void selectLine(int offset) {
        int line = model.lineOfOffset(offset);
        int end = line + 1 < model.lineCount() ? model.lineStart(line + 1) : model.lineEnd(line);
        select(model.lineStart(line), end);
    }

    private void installActions() {
        int menuKey = GraphicsEnvironment.isHeadless()
                ? InputEvent.CTRL_DOWN_MASK
                : Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        bindAction(ACTION_COPY, this::copy, KeyStroke.getKeyStroke(KeyEvent.VK_C, menuKey),
                KeyStroke.getKeyStroke(KeyEvent.VK_INSERT, InputEvent.CTRL_DOWN_MASK));
        bindAction(ACTION_SELECT_ALL, this::selectAll, KeyStroke.getKeyStroke(KeyEvent.VK_A, menuKey));
        bindAction(ACTION_GO_TO_LINE, this::showGoToLineDialog, KeyStroke.getKeyStroke(KeyEvent.VK_G, menuKey));
        bindAction(ACTION_COLLAPSE_FOLD, this::collapseFoldAtCaret, KeyStroke.getKeyStroke(KeyEvent.VK_MINUS, menuKey),
                KeyStroke.getKeyStroke(KeyEvent.VK_SUBTRACT, menuKey));
        bindAction(ACTION_EXPAND_FOLD, this::expandFoldAtCaret, KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, menuKey),
                KeyStroke.getKeyStroke(KeyEvent.VK_ADD, menuKey));
        bindAction(ACTION_EXPAND_ALL_FOLDS, this::expandAllFolds,
                KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, menuKey | InputEvent.SHIFT_DOWN_MASK));
        bindAction(ACTION_FIND_NEXT, this::findNext, KeyStroke.getKeyStroke(KeyEvent.VK_F3, 0));
        bindAction(ACTION_FIND_PREVIOUS, this::findPrevious, KeyStroke.getKeyStroke(KeyEvent.VK_F3, InputEvent.SHIFT_DOWN_MASK));
    }

    /** Registers an action under a name and binds it to the keys while the viewer has focus. */
    protected final void bindAction(String name, Runnable action, KeyStroke... keys) {
        for (KeyStroke key : keys) {
            getInputMap(WHEN_FOCUSED).put(key, name);
        }
        getActionMap().put(name, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                action.run();
            }
        });
    }

    /**
     * Gives an action other keys than the ones it has: those it had no longer run it, and with
     * no keys it is left without any. A key that ran another action runs this one from now on.
     * The actions of the viewer and the editor are named by the {@code ACTION_} constants; a
     * popup menu shows the new key beside its item.
     *
     * @throws IllegalArgumentException if there is no action of that name
     */
    public void setKeys(String actionName, KeyStroke... keys) {
        if (getActionMap().get(actionName) == null) {
            throw new IllegalArgumentException("No action named " + actionName);
        }
        InputMap bound = getInputMap(WHEN_FOCUSED);
        for (KeyStroke key : getKeys(actionName)) {
            bound.remove(key);
        }
        for (KeyStroke key : keys) {
            bound.put(key, actionName);
        }
    }

    /** The keys that run an action while the viewer has focus, in no particular order; none for a name that is not known. */
    public List<KeyStroke> getKeys(String actionName) {
        InputMap bound = getInputMap(WHEN_FOCUSED);
        List<KeyStroke> keys = new ArrayList<>();
        if (bound.keys() != null) {
            for (KeyStroke key : bound.keys()) {
                if (actionName.equals(bound.get(key))) {
                    keys.add(key);
                }
            }
        }
        return keys;
    }

    private void installPopupMenu() {
        JPopupMenu menu = new JPopupMenu();
        menu.addPopupMenuListener(new PopupMenuListener() {
            @Override
            public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
                // Rebuilt each time: what is offered depends on the selection and on what is attached.
                menu.removeAll();
                populatePopupMenu(menu);
            }

            @Override
            public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
            }

            @Override
            public void popupMenuCanceled(PopupMenuEvent e) {
            }
        });
        setComponentPopupMenu(menu);
    }

    /** Fills the popup menu just before it is shown. */
    protected void populatePopupMenu(JPopupMenu menu) {
        menu.add(createMenuItem("Copy", ACTION_COPY)).setEnabled(selectionAnchor != selectionCaret);
        menu.add(createMenuItem("Select All", ACTION_SELECT_ALL));
        // the find action exists only once a find bar is attached
        if (getActionMap().get(ACTION_FIND) != null) {
            menu.addSeparator();
            menu.add(createMenuItem("Find...", ACTION_FIND));
        }
    }

    /** A menu item that runs a bound action and shows its key. */
    protected final JMenuItem createMenuItem(String text, String actionName) {
        JMenuItem item = new JMenuItem(text);
        item.addActionListener(getActionMap().get(actionName));
        for (KeyStroke key : getInputMap(WHEN_FOCUSED).keys()) {
            if (actionName.equals(getInputMap(WHEN_FOCUSED).get(key))) {
                item.setAccelerator(key);
                break;
            }
        }
        return item;
    }
}
