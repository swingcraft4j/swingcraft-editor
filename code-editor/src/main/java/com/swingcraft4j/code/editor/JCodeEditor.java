package com.swingcraft4j.code.editor;

import com.swingcraft4j.code.editor.EditHistory.Edit;
import com.swingcraft4j.code.editor.EditHistory.Kind;
import com.swingcraft4j.code.format.FormatOptions;
import com.swingcraft4j.code.format.Formatter;
import com.swingcraft4j.code.layout.Cells;
import com.swingcraft4j.code.lexer.Language;
import com.swingcraft4j.code.text.EditableTextModel;
import com.swingcraft4j.code.text.GapTextModel;
import com.swingcraft4j.code.text.TextListener;
import com.swingcraft4j.code.text.TextModel;
import com.swingcraft4j.code.viewer.JCodeViewer;

import javax.swing.JPopupMenu;
import javax.swing.KeyStroke;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.AWTKeyStroke;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.KeyboardFocusManager;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.InputEvent;
import java.awt.event.InputMethodEvent;
import java.awt.event.InputMethodListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.font.TextHitInfo;
import java.awt.im.InputContext;
import java.awt.im.InputMethodRequests;
import java.text.AttributedCharacterIterator;
import java.text.AttributedCharacterIterator.Attribute;
import java.text.AttributedString;
import java.text.CharacterIterator;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.IntSupplier;
import java.util.regex.Pattern;

/**
 * Source code editor: a {@link JCodeViewer} with a caret, keyboard input, the clipboard, and
 * undo and redo. Put it in a scroll pane like the viewer.
 * <p>
 * The text lives in an {@link EditableTextModel}; a model of another kind is copied into one.
 * Change the text through the editor, or through the model: a change made directly on the
 * model is shown, but it clears the undo history.
 */
public class JCodeEditor extends JCodeViewer {

    public static final String ACTION_UNDO = "undo";
    public static final String ACTION_REDO = "redo";
    public static final String ACTION_CUT = "cut";
    public static final String ACTION_PASTE = "paste";
    public static final String ACTION_FORMAT = "format";
    public static final String ACTION_TOGGLE_COMMENT = "toggle-comment";
    public static final String ACTION_DUPLICATE_LINES = "duplicate-lines";
    public static final String ACTION_MOVE_LINES_UP = "move-lines-up";
    public static final String ACTION_MOVE_LINES_DOWN = "move-lines-down";

    /** In a regular expression: the rest of the line is not all blanks. */
    private static final String NOT_BLANK_AHEAD = "(?=[ \\t]*[^ \\t\\r\\n])";
    private static final String OPENING = "([{";
    private static final String CLOSING = ")]}";
    private static final String QUOTES = "\"'`";
    private static final boolean MAC = System.getProperty("os.name", "").toLowerCase().contains("mac");

    private boolean editable = true;
    private boolean tabsToSpaces = true;
    private boolean autoClosePairs = true;
    private boolean highlightCurrentLine = true;
    private boolean caretOn;
    /** Set while the editor itself changes the model or restores a selection. */
    private boolean applying;
    /** Set while the caret moves up or down, which keeps the column it is aiming for. */
    private boolean keepColumn;
    private int desiredX = -1;

    private final EditHistory history = new EditHistory();
    private final List<ChangeListener> editListeners = new CopyOnWriteArrayList<>();
    private final Timer blinkTimer = new Timer(500, e -> {
        caretOn = !caretOn;
        repaintCaret();
    });
    private final TextListener foreignChangeListener = change -> {
        if (!applying) {
            history.clear();
            updateModified();
        }
    };

    private boolean modified;
    /** The text being composed with an input method, as offsets, or -1 when there is none. */
    private int composedStart = -1;
    private int composedEnd = -1;
    private final Composition composition = new Composition();

    public JCodeEditor() {
        setDocument(new GapTextModel(), null);
        enableInputMethods(true);
        addInputMethodListener(composition);
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                finishComposition(); // a click elsewhere settles what was being composed
            }
        });
        // Tab is typed, not used to leave the editor; Ctrl+Tab moves the focus instead.
        setFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS,
                Set.of(AWTKeyStroke.getAWTKeyStroke(KeyEvent.VK_TAB, InputEvent.CTRL_DOWN_MASK)));
        setFocusTraversalKeys(KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS,
                Set.of(AWTKeyStroke.getAWTKeyStroke(KeyEvent.VK_TAB, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK)));
        installKeys();
        addFocusListener(new FocusListener() {
            @Override
            public void focusGained(FocusEvent e) {
                showCaret();
                repaintCaret();
            }

            @Override
            public void focusLost(FocusEvent e) {
                caretOn = false;
                blinkTimer.stop();
                repaintCaret();
            }
        });
    }

    public JCodeEditor(CharSequence text) {
        this();
        setText(text);
    }

    // ---- Document ----

    /** Shows the model if it is editable, or else an editable copy of it. Clears the undo history. */
    @Override
    public void setDocument(TextModel model, Language language) {
        EditableTextModel editableModel = model instanceof EditableTextModel already ? already : GapTextModel.copyOf(model);
        if (getModel() instanceof EditableTextModel old) {
            old.removeTextListener(foreignChangeListener);
        }
        cancelComposition();
        history.clear();
        history.markSaved(); // a document just shown has nothing to save
        super.setDocument(editableModel, language);
        editableModel.addTextListener(foreignChangeListener);
        updateModified();
    }

    /**
     * Whether the text differs from what it was when the document was shown or last
     * {@link #markSaved() marked as saved}. Undoing back to that state makes it unmodified
     * again. A change of this value is reported to property change listeners as
     * {@code "modified"}.
     */
    public boolean isModified() {
        return modified;
    }

    /** Declares the text as it is now to be saved, as after writing it to a file. */
    public void markSaved() {
        history.markSaved();
        updateModified();
    }

    private void updateModified() {
        boolean now = !history.isAtSavedState();
        if (now != modified) {
            modified = now;
            firePropertyChange("modified", !now, now);
        }
    }

    @Override
    public void setText(CharSequence text) {
        setModel(new GapTextModel(text));
    }

    public String getText() {
        return getModel().getText(0, getModel().length());
    }

    private EditableTextModel model() {
        return (EditableTextModel) getModel();
    }

    public boolean isEditable() {
        return editable;
    }

    /**
     * When false the text cannot be changed through the editor; the caret still moves, but it
     * does not blink.
     */
    public void setEditable(boolean editable) {
        this.editable = editable;
        if (isFocusOwner()) {
            showCaret();
            repaintCaret();
        }
    }

    /**
     * Shows the caret, and blinks it only where it says that typing would change the text. It
     * blinks at the rate the look and feel gives a text field, where zero is not at all.
     */
    private void showCaret() {
        caretOn = true;
        int rate = UIManager.get("TextField.caretBlinkRate") instanceof Integer given ? given : 500;
        if (editable && rate > 0) {
            blinkTimer.setInitialDelay(rate);
            blinkTimer.setDelay(rate);
            blinkTimer.restart();
        } else {
            blinkTimer.stop();
        }
    }

    public boolean isTabsToSpaces() {
        return tabsToSpaces;
    }

    /** Whether the Tab key inserts spaces up to the next tab stop (the default) or a tab char. */
    public void setTabsToSpaces(boolean tabsToSpaces) {
        this.tabsToSpaces = tabsToSpaces;
    }

    public boolean isAutoClosePairs() {
        return autoClosePairs;
    }

    /**
     * Whether typing an opening bracket or a quote also inserts its partner, typing the partner
     * steps over it, and Backspace between the two removes both; on by default.
     */
    public void setAutoClosePairs(boolean autoClosePairs) {
        this.autoClosePairs = autoClosePairs;
    }

    public boolean isHighlightCurrentLine() {
        return highlightCurrentLine;
    }

    /**
     * Whether the line of the caret has the background the theme gives it; on by default. A
     * preview that is only read looks calmer without.
     */
    public void setHighlightCurrentLine(boolean highlightCurrentLine) {
        this.highlightCurrentLine = highlightCurrentLine;
        repaint();
    }

    // ---- Editing ----

    /** Replaces the selection, or inserts at the caret when nothing is selected. */
    public void replaceSelection(String text) {
        replace(getSelectionStart(), getSelectionEnd(), text, Kind.OTHER);
    }

    /** Replaces the chars {@code [start, end)} and puts the caret after the new text. */
    public void replaceRange(int start, int end, String text) {
        replace(start, end, text, Kind.OTHER);
    }

    private boolean replace(int start, int end, String text, Kind kind) {
        if (!replaceInPlace(start, end, text, kind)) {
            return false;
        }
        scrollCaretIntoView();
        return true;
    }

    /** Makes an edit without scrolling to it. */
    private boolean replaceInPlace(int start, int end, String text, Kind kind) {
        return replaceInPlace(start, end, text, kind, -1, -1);
    }

    /**
     * Makes an edit and leaves a selection of its own, in one step: those that listen to the
     * caret are told once, of where it ends up, and not of a place it only passes.
     *
     * @param anchor the anchor of the selection after the edit, or -1 for the place after the new text
     * @param caret  its caret, or -1 for the same
     */
    private boolean replaceInPlace(int start, int end, String text, Kind kind, int anchor, int caret) {
        if (!editable) {
            Toolkit.getDefaultToolkit().beep();
            return false;
        }
        text = accepted(text);
        EditableTextModel model = model();
        String removed = model.getText(start, end);
        if (removed.isEmpty() && text.isEmpty()) {
            return false;
        }
        Edit edit = new Edit(start, removed, text, getSelectionAnchor(), getCaretPosition());
        applying = true;
        try {
            model.replace(start, end, text);
            int after = start + text.length();
            select(anchor < 0 ? after : anchor, caret < 0 ? after : caret);
        } finally {
            applying = false;
        }
        history.record(edit, kind);
        fireEdited();
        return true;
    }

    /** What is entered of a text that an edit is to put in: all of it here, one line of it in a {@link JCodeField}. */
    String accepted(String text) {
        return text;
    }

    /**
     * Adds a listener told after each change the editor makes to the text, undo and redo
     * included, once the change is complete and recorded for undo. Unlike a listener on the
     * model, it may change the text itself; {@link #appendToLastEdit} makes such a change part
     * of the one it follows.
     */
    public void addEditListener(ChangeListener listener) {
        editListeners.add(listener);
    }

    public void removeEditListener(ChangeListener listener) {
        editListeners.remove(listener);
    }

    private void fireEdited() {
        updateModified();
        ChangeEvent event = new ChangeEvent(this);
        for (ChangeListener listener : editListeners) {
            listener.stateChanged(event);
        }
    }

    /**
     * Runs edits that belong to the edit made just before, so that one undo takes them all
     * back together: used for changes that follow from what the user typed.
     */
    public void appendToLastEdit(Runnable edits) {
        history.beginJoin();
        try {
            edits.run();
        } finally {
            history.endJoin();
        }
    }

    public boolean canUndo() {
        return history.canUndo();
    }

    public boolean canRedo() {
        return history.canRedo();
    }

    public void undo() {
        finishComposition();
        List<Edit> edits = editable ? history.undo() : null;
        if (edits == null) {
            return;
        }
        applying = true;
        try {
            for (int i = edits.size() - 1; i >= 0; i--) {
                Edit edit = edits.get(i);
                model().replace(edit.offset(), edit.offset() + edit.inserted().length(), edit.removed());
            }
            select(edits.get(0).anchorBefore(), edits.get(0).caretBefore());
        } finally {
            applying = false;
        }
        fireEdited();
        scrollCaretIntoView();
    }

    public void redo() {
        finishComposition();
        List<Edit> edits = editable ? history.redo() : null;
        if (edits == null) {
            return;
        }
        applying = true;
        try {
            int caret = 0;
            for (Edit edit : edits) {
                model().replace(edit.offset(), edit.offset() + edit.removed().length(), edit.inserted());
                caret = edit.offset() + edit.inserted().length();
            }
            select(caret, caret);
        } finally {
            applying = false;
        }
        fireEdited();
        scrollCaretIntoView();
    }

    /** Moves the selection to the system clipboard. */
    public void cut() {
        if (editable && getSelectionStart() != getSelectionEnd()) {
            copy();
            replaceSelection("");
        }
    }

    /** Replaces the selection with the text on the system clipboard. */
    public void paste() {
        try {
            Object data = Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor);
            String text = ((String) data).replace("\r\n", "\n").replace('\r', '\n');
            replaceSelection(text.replace("\n", lineSeparator()));
        } catch (UnsupportedFlavorException | IOException | IllegalStateException e) {
            Toolkit.getDefaultToolkit().beep();
        }
    }

    /** The line terminator the document already uses, taken from its first line. */
    private String lineSeparator() {
        TextModel model = getModel();
        return model.lineCount() > 1 ? model.getText(model.lineEnd(0), model.lineStart(1)) : "\n";
    }

    private void typed(char c) {
        if (autoClosePairs && typedPair(c)) {
            return;
        }
        boolean overSelection = getSelectionStart() != getSelectionEnd();
        replace(getSelectionStart(), getSelectionEnd(), String.valueOf(c), overSelection ? Kind.OTHER : Kind.TYPING);
    }

    /**
     * Handles a typed bracket or quote that should not simply be inserted: an opening one gets
     * its partner, a closing one steps over a partner that is already there, and either wraps
     * the selection.
     *
     * @return whether the char was dealt with
     */
    private boolean typedPair(char c) {
        TextModel model = getModel();
        int start = getSelectionStart();
        int end = getSelectionEnd();
        int opening = OPENING.indexOf(c);
        boolean quote = QUOTES.indexOf(c) >= 0;
        char closing = opening >= 0 ? CLOSING.charAt(opening) : c;
        if (start != end) {
            if (opening < 0 && !quote) {
                return false;
            }
            String inner = model.getText(start, end);
            if (replaceInPlace(start, end, c + inner + closing, Kind.OTHER, start + 1, start + 1 + inner.length())) {
                scrollCaretIntoView();
            }
            return true;
        }
        char next = start < model.length() ? model.charAt(start) : '\n';
        char previous = start > 0 ? model.charAt(start - 1) : '\n';
        if ((CLOSING.indexOf(c) >= 0 || quote) && next == c) {
            moveCaret(start + 1, false); // the partner is there already: step over it
            return true;
        }
        // No partner in front of a word, which the new bracket is meant to go around; and no
        // second quote after a word or a backslash, where the quote is an apostrophe or escaped.
        boolean roomAfter = Character.isWhitespace(next) || ")]},;".indexOf(next) >= 0;
        boolean pairs = opening >= 0 || (quote && !Character.isLetterOrDigit(previous) && previous != '\\' && previous != c);
        if (roomAfter && pairs) {
            // the caret goes between the two at once: code completion takes a caret that moved
            // on by one char for a char that was typed
            if (replaceInPlace(start, start, "" + c + closing, Kind.OTHER, start + 1, start + 1)) {
                scrollCaretIntoView();
            }
            return true;
        }
        return false;
    }

    /** Whether the caret sits between a bracket or quote and its partner with nothing in between. */
    private boolean isInsideEmptyPair(int caret) {
        TextModel model = getModel();
        if (caret == 0 || caret >= model.length()) {
            return false;
        }
        char previous = model.charAt(caret - 1);
        char next = model.charAt(caret);
        int opening = OPENING.indexOf(previous);
        return opening >= 0 ? CLOSING.charAt(opening) == next : QUOTES.indexOf(previous) >= 0 && previous == next;
    }

    /**
     * Breaks the line and carries the indentation of the current line over to the new one.
     * After an opening bracket the new line is indented one level more, and a closing bracket
     * right after the caret goes onto a line of its own below.
     */
    private void insertLineBreak() {
        TextModel model = getModel();
        int start = getSelectionStart();
        int end = getSelectionEnd();
        int lineStart = model.lineStart(model.lineOfOffset(start));
        int indentEnd = lineStart;
        while (indentEnd < start && isBlank(model.charAt(indentEnd))) {
            indentEnd++;
        }
        String newLine = lineSeparator() + model.getText(lineStart, indentEnd);
        int before = start;
        while (before > indentEnd && isBlank(model.charAt(before - 1))) {
            before--;
        }
        char last = before > indentEnd ? model.charAt(before - 1) : '\0';
        int opening = OPENING.indexOf(last);
        boolean block = opening >= 0 || (last == ':' && getLanguage() != null && getLanguage().id().equals("python"));
        if (!block) {
            replace(start, end, newLine, Kind.OTHER);
            return;
        }
        String inner = newLine + (tabsToSpaces ? " ".repeat(getTabSize()) : "\t");
        boolean closes = opening >= 0 && end < model.length() && model.charAt(end) == CLOSING.charAt(opening);
        if (replace(start, end, closes ? inner + newLine : inner, Kind.OTHER) && closes) {
            selectQuietly(start + inner.length(), start + inner.length());
        }
    }

    private void insertTab() {
        TextModel model = getModel();
        if (model.lineOfOffset(getSelectionStart()) != model.lineOfOffset(getSelectionEnd())) {
            indentLines(true);
            return;
        }
        String text = "\t";
        if (tabsToSpaces) {
            int tabSize = getTabSize();
            text = " ".repeat(tabSize - column(getSelectionStart()) % tabSize);
        }
        replace(getSelectionStart(), getSelectionEnd(), text, Kind.OTHER);
    }

    /** The cell column of an offset within its line. */
    private int column(int offset) {
        TextModel model = getModel();
        int lineStart = model.lineStart(model.lineOfOffset(offset));
        char[] prefix = new char[offset - lineStart];
        model.getChars(lineStart, offset, prefix, 0);
        return Cells.count(prefix, 0, prefix.length, getTabSize());
    }

    /** Indents or unindents every line touched by the selection, as one undo step. */
    private void indentLines(boolean indent) {
        if (!editable) {
            Toolkit.getDefaultToolkit().beep();
            return;
        }
        TextModel model = getModel();
        int firstLine = model.lineOfOffset(getSelectionStart());
        int lastLine = model.lineOfOffset(getSelectionEnd());
        if (lastLine > firstLine && getSelectionEnd() == model.lineStart(lastLine)) {
            lastLine--; // a selection ending at the start of a line does not include that line
        }
        String unit = tabsToSpaces ? " ".repeat(getTabSize()) : "\t";
        boolean collapsed = getSelectionStart() == getSelectionEnd();
        int caret = getCaretPosition();
        int lengthBefore = model.length();
        history.beginCompound();
        try {
            for (int line = firstLine; line <= lastLine; line++) {
                int start = model.lineStart(line);
                if (indent) {
                    if (model.lineLength(line) > 0) {
                        replace(start, start, unit, Kind.OTHER);
                    }
                } else {
                    int end = start;
                    int limit = Math.min(model.lineEnd(line), start + getTabSize());
                    if (end < limit && model.charAt(end) == '\t') {
                        end++;
                    } else {
                        while (end < limit && model.charAt(end) == ' ') {
                            end++;
                        }
                    }
                    replace(start, end, "", Kind.OTHER);
                }
            }
        } finally {
            history.endCompound();
        }
        if (collapsed) {
            int moved = Math.max(model.lineStart(firstLine), caret + model.length() - lengthBefore);
            selectQuietly(moved, moved);
        } else {
            // whole lines, so that doing it again works on the same ones
            boolean lastOfAll = lastLine + 1 == model.lineCount();
            selectQuietly(model.lineStart(firstLine), lastOfAll ? model.lineEnd(lastLine) : model.lineStart(lastLine + 1));
        }
    }

    private void deletePrevious() {
        if (deleteSelection()) {
            return;
        }
        TextModel model = getModel();
        int caret = getCaretPosition();
        if (caret == 0) {
            return;
        }
        if (autoClosePairs && isInsideEmptyPair(caret)) {
            replace(caret - 1, caret + 1, "", Kind.DELETE_BACKWARD); // take the partner along
            return;
        }
        int start = caret - 1;
        char c = model.charAt(start);
        if (start > 0 && ((c == '\n' && model.charAt(start - 1) == '\r')
                || (Character.isLowSurrogate(c) && Character.isHighSurrogate(model.charAt(start - 1))))) {
            start--;
        } else if (c == ' ' && tabsToSpaces) {
            // in the indentation, step back to the previous tab stop
            int lineStart = model.lineStart(model.lineOfOffset(caret));
            int blank = caret;
            while (blank > lineStart && model.charAt(blank - 1) == ' ') {
                blank--;
            }
            if (blank == lineStart) {
                start = caret - ((caret - lineStart - 1) % getTabSize() + 1);
            }
        }
        replace(start, caret, "", Kind.DELETE_BACKWARD);
    }

    private void deleteNext() {
        if (!deleteSelection() && getCaretPosition() < getModel().length()) {
            int caret = getCaretPosition();
            TextModel model = getModel();
            int end = caret + 1;
            char c = model.charAt(caret);
            if (end < model.length() && ((c == '\r' && model.charAt(end) == '\n')
                    || (Character.isHighSurrogate(c) && Character.isLowSurrogate(model.charAt(end))))) {
                end++;
            }
            replace(caret, end, "", Kind.DELETE_FORWARD);
        }
    }

    private boolean deleteSelection() {
        if (getSelectionStart() == getSelectionEnd()) {
            return false;
        }
        replace(getSelectionStart(), getSelectionEnd(), "", Kind.OTHER);
        return true;
    }

    private void deleteTo(int target) {
        if (!deleteSelection()) {
            int caret = getCaretPosition();
            replace(Math.min(caret, target), Math.max(caret, target), "", Kind.OTHER);
        }
    }

    /** Copies the lines touched by the selection below themselves; the selection moves to the copy. */
    private void duplicateLines() {
        TextModel model = getModel();
        int anchor = getSelectionAnchor();
        int caret = getCaretPosition();
        int start = model.lineStart(model.lineOfOffset(getSelectionStart()));
        int end = model.lineEnd(model.lineOfOffset(getSelectionEnd()));
        String copy = lineSeparator() + model.getText(start, end);
        if (replace(end, end, copy, Kind.OTHER)) {
            selectQuietly(anchor + copy.length(), caret + copy.length());
            scrollCaretIntoView();
        }
    }

    /** The first and the last line touched by the selection; a line it only ends at the start of is not one. */
    private int[] selectedLines() {
        TextModel model = getModel();
        int first = model.lineOfOffset(getSelectionStart());
        int last = model.lineOfOffset(getSelectionEnd());
        if (last > first && getSelectionEnd() == model.lineStart(last)) {
            last--;
        }
        return new int[]{first, last};
    }

    /**
     * Comments out the lines touched by the selection, or takes the comment marks away again
     * when every one of them is commented out already. Uses the language's line comment, or
     * where it has none wraps each line in its block comment; does nothing for a language
     * without comments. One undo step.
     */
    public void toggleComment() {
        Language language = getLanguage();
        String linePrefix = language != null ? language.lineComment() : null;
        String[] block = language != null ? language.blockComment() : null;
        if (!editable || (linePrefix == null && block == null)) {
            Toolkit.getDefaultToolkit().beep();
            return;
        }
        String open = linePrefix != null ? linePrefix : block[0];
        String close = linePrefix != null ? "" : block[1];
        TextModel model = getModel();
        int[] lines = selectedLines();

        // The marks go at the indentation of the least indented line, so that they line up.
        int indent = Integer.MAX_VALUE;
        boolean allCommented = true;
        for (int line = lines[0]; line <= lines[1]; line++) {
            int start = model.lineStart(line);
            int text = textStart(line);
            int end = textEnd(line);
            if (text == end) {
                continue; // blank lines are left alone
            }
            indent = Math.min(indent, text - start);
            allCommented &= startsWith(text, end, open) && (close.isEmpty() || startsWith(end - close.length(), end, close));
        }
        if (indent == Integer.MAX_VALUE) {
            return;
        }
        int[] selection = {getSelectionAnchor(), getCaretPosition()};
        history.beginCompound();
        try {
            for (int line = lines[0]; line <= lines[1]; line++) {
                int start = model.lineStart(line);
                int text = textStart(line);
                int end = textEnd(line);
                if (text == end) {
                    continue;
                }
                if (allCommented) {
                    if (!close.isEmpty()) {
                        int from = end - close.length();
                        edit(from > text && model.charAt(from - 1) == ' ' ? from - 1 : from, end, "", selection);
                    }
                    int after = text + open.length();
                    edit(text, after < model.lineEnd(line) && model.charAt(after) == ' ' ? after + 1 : after, "", selection);
                } else {
                    if (!close.isEmpty()) {
                        edit(end, end, " " + close, selection);
                    }
                    edit(start + indent, start + indent, open + " ", selection);
                }
            }
        } finally {
            history.endCompound();
        }
        selectQuietly(selection[0], selection[1]);
        scrollCaretIntoView();
    }

    /** The offset of the first char of a line that is not a blank, or the end of the line. */
    private int textStart(int line) {
        TextModel model = getModel();
        int offset = model.lineStart(line);
        int end = model.lineEnd(line);
        while (offset < end && isBlank(model.charAt(offset))) {
            offset++;
        }
        return offset;
    }

    /** The offset just past the last char of a line that is not a blank. */
    private int textEnd(int line) {
        TextModel model = getModel();
        int start = model.lineStart(line);
        int offset = model.lineEnd(line);
        while (offset > start && isBlank(model.charAt(offset - 1))) {
            offset--;
        }
        return offset;
    }

    private boolean startsWith(int offset, int end, String text) {
        return offset >= 0 && offset + text.length() <= end && getModel().getText(offset, offset + text.length()).equals(text);
    }

    /** Makes an edit and moves the two offsets of a selection to where the edit leaves them. */
    private void edit(int start, int end, String text, int[] selection) {
        if (replace(start, end, text, Kind.OTHER)) {
            for (int i = 0; i < selection.length; i++) {
                if (selection[i] >= end && selection[i] > start) {
                    selection[i] += text.length() - (end - start);
                } else if (selection[i] > start) {
                    selection[i] = start + text.length();
                }
            }
        }
    }

    /** Whether the language of the document has a formatter, so that {@link #format()} has something to do. */
    public boolean canFormat() {
        return getLanguage() != null && getLanguage().formatter() != null;
    }

    /**
     * Lays the text out afresh with the formatter of the language: the lines touched by the
     * selection, or the whole text when nothing is selected. Does nothing for a language without
     * a formatter. One undo step, and only the part of the text that comes out different is
     * replaced, so the caret stays with the text it was at and on the row of the screen it was on.
     */
    public void format() {
        Formatter formatter = getLanguage() != null ? getLanguage().formatter() : null;
        if (!editable || formatter == null) {
            Toolkit.getDefaultToolkit().beep();
            return;
        }
        finishComposition();
        TextModel model = getModel();
        boolean selection = getSelectionStart() != getSelectionEnd();
        int[] lines = selectedLines();
        int start = selection ? model.lineStart(lines[0]) : 0;
        int end = selection ? model.lineEnd(lines[1]) : model.length();
        String before = model.getText(start, end);
        FormatOptions options = new FormatOptions(getTabSize(), !tabsToSpaces, lineSeparator());
        String after = selection ? formatLines(formatter, before, options) : formatter.format(before, options);

        int limit = Math.min(before.length(), after.length());
        int head = 0;
        while (head < limit && before.charAt(head) == after.charAt(head)) {
            head++;
        }
        int tail = 0;
        while (tail < limit - head && before.charAt(before.length() - 1 - tail) == after.charAt(after.length() - 1 - tail)) {
            tail++;
        }
        int anchor = formattedOffset(getSelectionAnchor(), start, before, after);
        int caret = formattedOffset(getCaretPosition(), start, before, after);
        Rectangle visible = getVisibleRect();
        int row = getOffsetBounds(getCaretPosition()).y;
        if (replaceInPlace(start + head, end - tail, after.substring(head, after.length() - tail), Kind.OTHER)) {
            selectQuietly(anchor, caret);
            visible.y += getOffsetBounds(caret).y - row;
            scrollRectToVisible(visible);
        }
    }

    /**
     * Formats lines taken from the middle of a text. The indentation they all share is taken
     * off first and put back after, since a formatter indents from the margin.
     */
    private static String formatLines(Formatter formatter, String text, FormatOptions options) {
        String indent = null;
        for (String line : text.split("\\R")) {
            if (line.isBlank()) {
                continue;
            }
            int length = 0;
            while (isBlank(line.charAt(length)) && (indent == null || (length < indent.length()
                    && indent.charAt(length) == line.charAt(length)))) {
                length++;
            }
            indent = line.substring(0, length);
        }
        if (indent == null || indent.isEmpty()) {
            return formatter.format(text, options);
        }
        String flush = Pattern.compile("^" + indent + NOT_BLANK_AHEAD, Pattern.MULTILINE).matcher(text).replaceAll("");
        String formatted = formatter.format(flush, options);
        return Pattern.compile("^" + NOT_BLANK_AHEAD, Pattern.MULTILINE).matcher(formatted).replaceAll(indent);
    }

    /**
     * Where an offset lies once the chars from {@code start} on, which were {@code before}, are
     * {@code after}. Formatting keeps the chars that are not white space, so the two texts are
     * read side by side by those: the offset goes after the one it was after, and stays in
     * front of the one it was in front of.
     */
    private static int formattedOffset(int offset, int start, String before, String after) {
        if (offset <= start) {
            return offset;
        }
        if (offset >= start + before.length()) {
            return offset + after.length() - before.length();
        }
        int at = offset - start;
        int from = 0;
        int to = 0;
        while (from < at) {
            char c = before.charAt(from);
            if (Character.isWhitespace(c)) {
                from++;
                continue;
            }
            int next = skipWhitespace(after, to);
            if (next == after.length()) {
                to = next;
                break;
            }
            if (after.charAt(next) == c) {
                from++;
                to = next + 1;
                continue;
            }
            // Chars were added or taken away here, as the dashes of a table are: go on from the
            // nearest place where the two texts agree again.
            int added = indexNearby(after, c, next);
            int removed = indexNearby(before, after.charAt(next), from);
            if (added >= 0 && (removed < 0 || added - next <= removed - from)) {
                to = added + 1;
                from++;
            } else if (removed >= 0) {
                from = removed;
            } else {
                from++;
            }
        }
        if (!Character.isWhitespace(before.charAt(at))) {
            return start + skipWhitespace(after, to);
        }
        // In white space: as many lines below the text before it as it was, and as far into the line.
        int breaks = 0;
        int column = -1;
        for (int i = at; i > 0 && Character.isWhitespace(before.charAt(i - 1)); i--) {
            if (before.charAt(i - 1) == '\n') {
                breaks++;
                column = column < 0 ? at - i : column;
            }
        }
        while (breaks > 0 && to < after.length() && Character.isWhitespace(after.charAt(to))) {
            if (after.charAt(to++) == '\n') {
                breaks--;
            }
        }
        while (column-- > 0 && to < after.length() && isBlank(after.charAt(to))) {
            to++;
        }
        return start + to;
    }

    private static int skipWhitespace(String text, int from) {
        int index = from;
        while (index < text.length() && Character.isWhitespace(text.charAt(index))) {
            index++;
        }
        return index;
    }

    /** The index of a char at or shortly after {@code from}, or -1 if it is not near. */
    private static int indexNearby(String text, char c, int from) {
        int index = text.indexOf(c, from);
        return index - from < 64 ? index : -1;
    }

    /** Moves the lines touched by the selection up past the line above them. */
    public void moveLinesUp() {
        moveLines(-1);
    }

    /** Moves the lines touched by the selection down past the line below them. */
    public void moveLinesDown() {
        moveLines(1);
    }

    private void moveLines(int direction) {
        TextModel model = getModel();
        int[] lines = selectedLines();
        int other = direction < 0 ? lines[0] - 1 : lines[1] + 1;
        if (other < 0 || other >= model.lineCount()) {
            return;
        }
        int anchor = getSelectionAnchor();
        int caret = getCaretPosition();
        int blockStart = model.lineStart(lines[0]);
        int blockEnd = model.lineEnd(lines[1]);
        String block = model.getText(blockStart, blockEnd);
        String otherLine = model.getText(model.lineStart(other), model.lineEnd(other));
        // The two change places around the line terminator between them, which stays as it is.
        boolean moved;
        int shift;
        if (direction < 0) {
            String terminator = model.getText(model.lineEnd(other), blockStart);
            shift = -(otherLine.length() + terminator.length());
            moved = replace(model.lineStart(other), blockEnd, block + terminator + otherLine, Kind.OTHER);
        } else {
            String terminator = model.getText(blockEnd, model.lineStart(other));
            shift = otherLine.length() + terminator.length();
            moved = replace(blockStart, model.lineEnd(other), otherLine + terminator + block, Kind.OTHER);
        }
        if (moved) {
            selectQuietly(anchor + shift, caret + shift);
            scrollCaretIntoView();
        }
    }

    // ---- Caret ----

    /** Changes the selection as part of an edit, without ending the current undo group's context. */
    private void selectQuietly(int anchor, int caret) {
        applying = true;
        try {
            select(anchor, caret);
        } finally {
            applying = false;
        }
    }

    @Override
    protected void selectionChanged() {
        if (!applying) {
            history.closeGroup();
        }
        if (!keepColumn) {
            desiredX = -1;
        }
        if (isFocusOwner()) {
            showCaret();
        }
        super.selectionChanged();
    }

    private void moveCaret(int target, boolean extend) {
        select(extend ? getSelectionAnchor() : target, target);
        scrollCaretIntoView();
    }

    private void moveVertically(int rows, boolean extend) {
        Rectangle caret = getOffsetBounds(getCaretPosition());
        if (desiredX < 0) {
            desiredX = caret.x;
        }
        int y = caret.y + rows * getRowHeight();
        int target;
        if (y < 0) {
            target = 0;
        } else if (y >= getPreferredSize().height) {
            target = getModel().length();
        } else {
            target = offsetAt(desiredX, y);
        }
        keepColumn = true;
        try {
            moveCaret(target, extend);
        } finally {
            keepColumn = false;
        }
    }

    private int visibleRows() {
        return Math.max(1, getVisibleRect().height / getRowHeight() - 1);
    }

    private void scrollCaretIntoView() {
        Rectangle caret = getOffsetBounds(getCaretPosition());
        int margin = caret.width * 4;
        int left = Math.max(0, caret.x - margin);
        scrollRectToVisible(new Rectangle(left, caret.y, caret.x + margin - left, caret.height));
    }

    private int previousOffset(int offset) {
        TextModel model = getModel();
        if (offset == 0) {
            return 0;
        }
        int previous = offset - 1;
        if (model.charAt(previous) == '\n' && previous > 0 && model.charAt(previous - 1) == '\r') {
            return previous - 1;
        }
        // step over a whole cluster: marks, joined letters, the halves of a surrogate pair
        while (previous > 0 && Cells.isClusterContinuation(model.charAt(previous - 1), model.charAt(previous))) {
            previous--;
        }
        return previous;
    }

    private int nextOffset(int offset) {
        TextModel model = getModel();
        int length = model.length();
        if (offset >= length) {
            return length;
        }
        int next = offset + 1;
        if (model.charAt(offset) == '\r' && next < length && model.charAt(next) == '\n') {
            return next + 1;
        }
        while (next < length && Cells.isClusterContinuation(model.charAt(next - 1), model.charAt(next))) {
            next++;
        }
        return next;
    }

    /** 0 for blanks, 1 for word chars, 2 for other chars, 3 for line terminators. */
    private static int charClass(char c) {
        if (c == '\n' || c == '\r') {
            return 3;
        }
        if (isBlank(c)) {
            return 0;
        }
        return Character.isLetterOrDigit(c) || c == '_' ? 1 : 2;
    }

    private static boolean isBlank(char c) {
        return c == ' ' || c == '\t';
    }

    /** The start of the next word: past the run of chars at the offset and the blanks after it. */
    private int nextWord(int offset) {
        TextModel model = getModel();
        int length = model.length();
        if (offset >= length) {
            return length;
        }
        int kind = charClass(model.charAt(offset));
        if (kind == 3) {
            return nextOffset(offset);
        }
        int next = offset;
        while (next < length && charClass(model.charAt(next)) == kind) {
            next++;
        }
        while (next < length && charClass(model.charAt(next)) == 0) {
            next++;
        }
        return next;
    }

    /** The start of the word before the offset. */
    private int previousWord(int offset) {
        TextModel model = getModel();
        if (offset == 0) {
            return 0;
        }
        if (charClass(model.charAt(offset - 1)) == 3) {
            return previousOffset(offset);
        }
        int previous = offset;
        while (previous > 0 && charClass(model.charAt(previous - 1)) == 0) {
            previous--;
        }
        if (previous > 0) {
            int kind = charClass(model.charAt(previous - 1));
            while (kind != 3 && previous > 0 && charClass(model.charAt(previous - 1)) == kind) {
                previous--;
            }
        }
        return previous;
    }

    /** The first non-blank char of the caret's line, or the start of the line if the caret is already there. */
    private int smartHome() {
        TextModel model = getModel();
        int line = model.lineOfOffset(getCaretPosition());
        int start = model.lineStart(line);
        int text = start;
        while (text < model.lineEnd(line) && isBlank(model.charAt(text))) {
            text++;
        }
        return getCaretPosition() == text ? start : text;
    }

    private int lineEnd() {
        return getModel().lineEnd(getModel().lineOfOffset(getCaretPosition()));
    }

    // ---- Keys ----

    @Override
    protected void processKeyEvent(KeyEvent e) {
        super.processKeyEvent(e);
        if (e.isConsumed() || e.getID() != KeyEvent.KEY_TYPED) {
            return;
        }
        char c = e.getKeyChar();
        // Ctrl+Alt is AltGr on Windows and types a char; Alt alone is a menu mnemonic there.
        boolean shortcut = e.isMetaDown() || (e.isAltDown() && !e.isControlDown() && !MAC)
                || (e.isControlDown() && !e.isAltDown());
        if (c >= 0x20 && c != 0x7F && c != KeyEvent.CHAR_UNDEFINED && !shortcut) {
            typed(c);
            e.consume();
        }
    }

    private void installKeys() {
        int menu = GraphicsEnvironment.isHeadless()
                ? InputEvent.CTRL_DOWN_MASK
                : Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        int shift = InputEvent.SHIFT_DOWN_MASK;
        int word = MAC ? InputEvent.ALT_DOWN_MASK : InputEvent.CTRL_DOWN_MASK;

        bindMove("caret-left", KeyEvent.VK_LEFT, 0, () -> previousOffset(getCaretPosition()), this::getSelectionStart);
        bindMove("caret-right", KeyEvent.VK_RIGHT, 0, () -> nextOffset(getCaretPosition()), this::getSelectionEnd);
        bindMove("caret-previous-word", KeyEvent.VK_LEFT, word, () -> previousWord(getCaretPosition()), null);
        bindMove("caret-next-word", KeyEvent.VK_RIGHT, word, () -> nextWord(getCaretPosition()), null);
        bindMove("caret-line-start", KeyEvent.VK_HOME, 0, this::smartHome, null);
        bindMove("caret-line-end", KeyEvent.VK_END, 0, this::lineEnd, null);
        bindMove("caret-document-start", KeyEvent.VK_HOME, menu, () -> 0, null);
        bindMove("caret-document-end", KeyEvent.VK_END, menu, () -> getModel().length(), null);
        bindVertical("caret-up", KeyEvent.VK_UP, () -> -1);
        bindVertical("caret-down", KeyEvent.VK_DOWN, () -> 1);
        bindVertical("caret-page-up", KeyEvent.VK_PAGE_UP, () -> -visibleRows());
        bindVertical("caret-page-down", KeyEvent.VK_PAGE_DOWN, this::visibleRows);

        bindAction("insert-break", this::insertLineBreak, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0),
                KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, shift));
        bindAction("insert-tab", this::insertTab, KeyStroke.getKeyStroke(KeyEvent.VK_TAB, 0));
        bindAction("unindent", () -> indentLines(false), KeyStroke.getKeyStroke(KeyEvent.VK_TAB, shift));
        bindAction("delete-previous", this::deletePrevious, KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, 0),
                KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, shift));
        bindAction("delete-next", this::deleteNext, KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0));
        bindAction("delete-previous-word", () -> deleteTo(previousWord(getCaretPosition())),
                KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, word));
        bindAction("delete-next-word", () -> deleteTo(nextWord(getCaretPosition())),
                KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, word));
        bindAction(ACTION_DUPLICATE_LINES, this::duplicateLines, KeyStroke.getKeyStroke(KeyEvent.VK_D, menu));
        bindAction(ACTION_TOGGLE_COMMENT, this::toggleComment, KeyStroke.getKeyStroke(KeyEvent.VK_SLASH, menu),
                KeyStroke.getKeyStroke(KeyEvent.VK_DIVIDE, menu));
        bindAction(ACTION_MOVE_LINES_UP, this::moveLinesUp, KeyStroke.getKeyStroke(KeyEvent.VK_UP, InputEvent.ALT_DOWN_MASK));
        bindAction(ACTION_MOVE_LINES_DOWN, this::moveLinesDown, KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, InputEvent.ALT_DOWN_MASK));
        bindAction(ACTION_FORMAT, this::format, KeyStroke.getKeyStroke(KeyEvent.VK_F, InputEvent.SHIFT_DOWN_MASK | InputEvent.ALT_DOWN_MASK));

        bindAction(ACTION_UNDO, this::undo, KeyStroke.getKeyStroke(KeyEvent.VK_Z, menu));
        bindAction(ACTION_REDO, this::redo, KeyStroke.getKeyStroke(KeyEvent.VK_Y, menu),
                KeyStroke.getKeyStroke(KeyEvent.VK_Z, menu | shift));
        bindAction(ACTION_CUT, this::cut, KeyStroke.getKeyStroke(KeyEvent.VK_X, menu),
                KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, shift));
        bindAction(ACTION_PASTE, this::paste, KeyStroke.getKeyStroke(KeyEvent.VK_V, menu),
                KeyStroke.getKeyStroke(KeyEvent.VK_INSERT, shift));
    }

    /**
     * Binds a caret movement and, with Shift, the same movement extending the selection.
     *
     * @param collapse where the caret goes instead when a selection is dropped, or null to move as usual
     */
    private void bindMove(String name, int keyCode, int modifiers, IntSupplier target, IntSupplier collapse) {
        bindAction(name, () -> {
            boolean selection = getSelectionStart() != getSelectionEnd();
            moveCaret(selection && collapse != null ? collapse.getAsInt() : target.getAsInt(), false);
        }, KeyStroke.getKeyStroke(keyCode, modifiers));
        bindAction("select-" + name, () -> moveCaret(target.getAsInt(), true),
                KeyStroke.getKeyStroke(keyCode, modifiers | InputEvent.SHIFT_DOWN_MASK));
    }

    private void bindVertical(String name, int keyCode, IntSupplier rows) {
        bindAction(name, () -> moveVertically(rows.getAsInt(), false), KeyStroke.getKeyStroke(keyCode, 0));
        bindAction("select-" + name, () -> moveVertically(rows.getAsInt(), true),
                KeyStroke.getKeyStroke(keyCode, InputEvent.SHIFT_DOWN_MASK));
    }

    @Override
    protected void populatePopupMenu(JPopupMenu menu) {
        boolean selection = getSelectionStart() != getSelectionEnd();
        menu.add(createMenuItem("Undo", ACTION_UNDO)).setEnabled(editable && canUndo());
        menu.add(createMenuItem("Redo", ACTION_REDO)).setEnabled(editable && canRedo());
        menu.addSeparator();
        menu.add(createMenuItem("Cut", ACTION_CUT)).setEnabled(editable && selection);
        menu.add(createMenuItem("Copy", ACTION_COPY)).setEnabled(selection);
        menu.add(createMenuItem("Paste", ACTION_PASTE)).setEnabled(editable);
        menu.add(createMenuItem("Select All", ACTION_SELECT_ALL));
        if (getActionMap().get(ACTION_FIND) != null) {
            menu.addSeparator();
            menu.add(createMenuItem("Find...", ACTION_FIND));
        }
    }

    // ---- Painting ----

    @Override
    protected void paintBackgroundLayer(Graphics2D g, Rectangle clip) {
        if (!highlightCurrentLine) {
            return;
        }
        TextModel model = getModel();
        int line = model.lineOfOffset(getCaretPosition());
        int top = getOffsetBounds(model.lineStart(line)).y;
        int bottom = getOffsetBounds(model.lineEnd(line)).y + getRowHeight();
        g.setColor(getTheme().currentLineBackground());
        g.fillRect(clip.x, top, clip.width, bottom - top);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (isComposing()) {
            // text still being composed is underlined, as input methods have it
            Rectangle from = getOffsetBounds(composedStart);
            Rectangle to = getOffsetBounds(composedEnd);
            g.setColor(getTheme().foreground());
            if (from.y == to.y) {
                g.drawLine(from.x, from.y + from.height - 2, to.x, from.y + from.height - 2);
            }
        }
        if (caretOn) {
            Rectangle caret = getOffsetBounds(getCaretPosition());
            g.setColor(getTheme().foreground());
            g.fillRect(caret.x, caret.y, caretWidth(), caret.height);
        }
    }

    // ---- Input methods ----

    /** Whether text is being composed with an input method and is not yet part of the document for good. */
    public boolean isComposing() {
        return composedStart >= 0;
    }

    @Override
    public InputMethodRequests getInputMethodRequests() {
        return composition;
    }

    /** Changes the text without recording it for undo: for text that is only being composed. */
    private void replaceUnrecorded(int start, int end, String text) {
        applying = true;
        try {
            model().replace(start, end, text);
        } finally {
            applying = false;
        }
    }

    /** Takes the composed text out again, leaving the caret where it was. */
    private void removeComposedText() {
        if (isComposing()) {
            int start = composedStart;
            int end = composedEnd;
            composedStart = -1;
            composedEnd = -1;
            replaceUnrecorded(start, end, "");
            selectQuietly(start, start);
        }
    }

    /** Settles text that is being composed: the input method commits it, or failing that it is kept as it is. */
    private void finishComposition() {
        if (!isComposing()) {
            return;
        }
        InputContext context = getInputContext();
        if (context != null) {
            context.endComposition();
        }
        if (isComposing()) {
            String text = getModel().getText(composedStart, composedEnd);
            removeComposedText();
            replace(getCaretPosition(), getCaretPosition(), text, Kind.OTHER);
        }
    }

    /** Drops text that is being composed, as when another document is shown. */
    private void cancelComposition() {
        if (isComposing()) {
            composedStart = -1;
            composedEnd = -1;
            InputContext context = getInputContext();
            if (context != null) {
                context.endComposition();
            }
        }
    }

    /**
     * Composing text in place with an input method, as for Chinese, Japanese or Korean. The
     * text being composed is put into the document so that it is laid out and drawn like any
     * other, but it is kept out of the undo history; what the input method commits is then
     * entered as an ordinary edit.
     */
    private final class Composition implements InputMethodListener, InputMethodRequests {

        @Override
        public void inputMethodTextChanged(InputMethodEvent event) {
            StringBuilder committed = new StringBuilder();
            StringBuilder composed = new StringBuilder();
            AttributedCharacterIterator text = event.getText();
            if (text != null) {
                int index = 0;
                for (char c = text.first(); c != CharacterIterator.DONE; c = text.next()) {
                    (index++ < event.getCommittedCharacterCount() ? committed : composed).append(c);
                }
            }
            event.consume();
            if (!editable) {
                return;
            }
            removeComposedText();
            if (!committed.isEmpty()) {
                replace(getSelectionStart(), getSelectionEnd(), committed.toString(), Kind.OTHER);
            }
            if (!composed.isEmpty()) {
                if (getSelectionStart() != getSelectionEnd()) {
                    replace(getSelectionStart(), getSelectionEnd(), "", Kind.OTHER); // composing replaces the selection
                }
                int start = getCaretPosition();
                replaceUnrecorded(start, start, composed.toString());
                composedStart = start;
                composedEnd = start + composed.length();
                TextHitInfo caret = event.getCaret();
                int at = caret != null ? Math.min(caret.getInsertionIndex(), composed.length()) : composed.length();
                selectQuietly(start + at, start + at);
                scrollCaretIntoView();
            }
            repaint();
        }

        @Override
        public void caretPositionChanged(InputMethodEvent event) {
            TextHitInfo caret = event.getCaret();
            if (isComposing() && caret != null) {
                int at = composedStart + Math.min(caret.getInsertionIndex(), composedEnd - composedStart);
                selectQuietly(at, at);
            }
            event.consume();
        }

        /** Where on the screen the input method should put its window of candidates. */
        @Override
        public Rectangle getTextLocation(TextHitInfo offset) {
            int at = getCaretPosition();
            if (isComposing() && offset != null) {
                at = composedStart + Math.min(offset.getInsertionIndex(), composedEnd - composedStart);
            }
            Rectangle bounds = getOffsetBounds(at);
            if (isShowing()) {
                Point origin = getLocationOnScreen();
                bounds.translate(origin.x, origin.y);
            }
            return bounds;
        }

        @Override
        public TextHitInfo getLocationOffset(int x, int y) {
            return null;
        }

        @Override
        public int getInsertPositionOffset() {
            return isComposing() ? composedStart : getSelectionStart();
        }

        /** The document without the text being composed. */
        @Override
        public AttributedCharacterIterator getCommittedText(int beginIndex, int endIndex, Attribute[] attributes) {
            TextModel model = getModel();
            StringBuilder text = new StringBuilder();
            if (!isComposing()) {
                text.append(model.getText(beginIndex, endIndex));
            } else {
                int composedLength = composedEnd - composedStart;
                if (beginIndex < composedStart) {
                    text.append(model.getText(beginIndex, Math.min(endIndex, composedStart)));
                }
                if (endIndex > composedStart) {
                    text.append(model.getText(Math.max(beginIndex, composedStart) + composedLength, endIndex + composedLength));
                }
            }
            return new AttributedString(text.toString()).getIterator();
        }

        @Override
        public int getCommittedTextLength() {
            return getModel().length() - (isComposing() ? composedEnd - composedStart : 0);
        }

        @Override
        public AttributedCharacterIterator cancelLatestCommittedText(Attribute[] attributes) {
            return null;
        }

        @Override
        public AttributedCharacterIterator getSelectedText(Attribute[] attributes) {
            return new AttributedString(isComposing() ? "" : JCodeEditor.this.getSelectedText()).getIterator();
        }
    }

    /** The width the look and feel gives the caret of a text field, which is one pixel where it names none. */
    private static int caretWidth() {
        return UIManager.get("Caret.width") instanceof Integer width ? width : 1;
    }

    private void repaintCaret() {
        Rectangle caret = getOffsetBounds(getCaretPosition());
        repaint(caret.x - 1, caret.y, caretWidth() + 2, caret.height);
    }
}
