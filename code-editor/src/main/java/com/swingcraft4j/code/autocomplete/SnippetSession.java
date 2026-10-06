package com.swingcraft4j.code.autocomplete;

import com.swingcraft4j.code.editor.JCodeEditor;
import com.swingcraft4j.code.text.EditableTextModel;
import com.swingcraft4j.code.text.TextChange;
import com.swingcraft4j.code.text.TextListener;
import com.swingcraft4j.code.text.TextModel;

import javax.swing.AbstractAction;
import javax.swing.KeyStroke;
import javax.swing.event.ChangeListener;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * A snippet inserted into an editor, with tab stops to fill in.
 * <p>
 * In the template, {@code ${name}} is a tab stop holding the text {@code name}, and
 * {@code $0} is where the caret ends up; without it that is the end of the snippet. A line
 * break is followed by the indentation of the line the snippet is inserted on, and a tab
 * becomes one level of the editor's indentation:
 * <pre>{@code
 * for (int ${i} = 0; ${i} < ${count}; ${i}++) {\n\t$0\n}
 * }</pre>
 * After the insertion the first tab stop is selected. Tab moves to the next one and
 * Shift+Tab to the one before; after the last, Tab puts the caret at its final place and the
 * session is over. It also ends on Escape or when the caret leaves the snippet.
 * <p>
 * Tab stops of the same name are linked: what is typed in one appears in the others, so the
 * {@code i} of the loop above is renamed in all three places at once.
 */
public final class SnippetSession {

    private static final String NEXT = "snippet-next";
    private static final String PREVIOUS = "snippet-previous";
    private static final String END = "snippet-end";

    private final JCodeEditor editor;
    private final EditableTextModel model;
    private final Runnable beforeEnd;
    private final KeyOverrides keys;
    /** The tab stops in order, the caret's final place last, as offsets that follow the edits. */
    private final int[] starts;
    private final int[] ends;
    /** The name each tab stop was written with; null for the caret's final place. */
    private final String[] names;
    private int current;
    private int snippetStart;
    private int snippetEnd;
    /** The length of the document when the tab stops were last brought up to date. */
    private int knownLength;
    private boolean active;
    private boolean moving;
    /** Whether the current tab stop was edited and those linked to it have yet to follow. */
    private boolean linksPending;
    private boolean updatingLinks;

    private final TextListener textListener = this::textChanged;
    private final ChangeListener caretListener = e -> caretMoved();
    private final ChangeListener editListener = e -> updateLinks();

    /**
     * Replaces the chars {@code [start, end)} of the editor with a snippet, as one undo step.
     *
     * @return the session, already over if the template has no tab stops
     */
    public static SnippetSession start(JCodeEditor editor, int start, int end, String template) {
        return start(editor, start, end, template, () -> {
        });
    }

    static SnippetSession start(JCodeEditor editor, int start, int end, String template, Runnable beforeEnd) {
        TextModel model = editor.getModel();
        int lineStart = model.lineStart(model.lineOfOffset(start));
        int indentEnd = lineStart;
        while (indentEnd < start && (model.charAt(indentEnd) == ' ' || model.charAt(indentEnd) == '\t')) {
            indentEnd++;
        }
        String lineBreak = (model.lineCount() > 1 ? model.getText(model.lineEnd(0), model.lineStart(1)) : "\n")
                + model.getText(lineStart, indentEnd);
        String indentUnit = editor.isTabsToSpaces() ? " ".repeat(editor.getTabSize()) : "\t";

        StringBuilder text = new StringBuilder();
        List<int[]> stops = new ArrayList<>();
        List<String> names = new ArrayList<>();
        int finalPlace = -1;
        for (int i = 0; i < template.length(); i++) {
            char c = template.charAt(i);
            int close = c == '$' && i + 1 < template.length() && template.charAt(i + 1) == '{' ? template.indexOf('}', i) : -1;
            if (close > 0) {
                String name = template.substring(i + 2, close);
                stops.add(new int[]{text.length(), text.length() + name.length()});
                names.add(name);
                text.append(name);
                i = close;
            } else if (c == '$' && i + 1 < template.length() && template.charAt(i + 1) == '0') {
                finalPlace = text.length();
                i++;
            } else if (c == '\n') {
                text.append(lineBreak);
            } else if (c == '\t') {
                text.append(indentUnit);
            } else if (c != '\r') {
                text.append(c);
            }
        }
        if (finalPlace < 0) {
            finalPlace = text.length();
        }
        stops.add(new int[]{finalPlace, finalPlace});
        names.add(null);

        editor.replaceRange(start, end, text.toString());
        SnippetSession session = new SnippetSession(editor, start, text.length(), stops, names, beforeEnd);
        // the edit can fail, as in a read-only editor; then there is nothing to step through
        if (editor.getCaretPosition() == start + text.length() && editor.getModel() instanceof EditableTextModel) {
            session.begin();
        }
        return session;
    }

    private SnippetSession(JCodeEditor editor, int start, int length, List<int[]> stops, List<String> names,
                           Runnable beforeEnd) {
        this.editor = editor;
        this.model = editor.getModel() instanceof EditableTextModel editable ? editable : null;
        this.beforeEnd = beforeEnd;
        this.keys = new KeyOverrides(editor);
        this.snippetStart = start;
        this.snippetEnd = start + length;
        this.starts = new int[stops.size()];
        this.ends = new int[stops.size()];
        this.names = names.toArray(new String[0]);
        for (int i = 0; i < starts.length; i++) {
            starts[i] = start + stops.get(i)[0];
            ends[i] = start + stops.get(i)[1];
        }
    }

    private void begin() {
        if (starts.length == 1) {
            editor.select(starts[0], starts[0]); // no tab stops: only the final place
            return;
        }
        active = true;
        action(NEXT, this::next);
        action(PREVIOUS, this::previous);
        action(END, this::end);
        keys.replace(KeyStroke.getKeyStroke(KeyEvent.VK_TAB, 0), NEXT);
        keys.replace(KeyStroke.getKeyStroke(KeyEvent.VK_TAB, InputEvent.SHIFT_DOWN_MASK), PREVIOUS);
        keys.replace(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), END);
        knownLength = model.length();
        model.addTextListener(textListener);
        editor.addSelectionListener(caretListener);
        editor.addEditListener(editListener);
        go(0);
    }

    private void action(String name, Runnable action) {
        editor.getActionMap().put(name, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                action.run();
            }
        });
    }

    /** Whether the session is itself editing the document just now, to bring linked tab stops into line. */
    boolean isUpdatingLinks() {
        return updatingLinks;
    }

    /** Whether tab stops are still to be stepped through. */
    public boolean isActive() {
        return active;
    }

    /**
     * Moves to the next tab stop, passing over those linked to one already visited; from the
     * last one, to the caret's final place, which ends the session.
     */
    public void next() {
        if (!active) {
            return;
        }
        updateLinks();
        int stop = current + 1;
        while (stop < starts.length - 1 && isLinkedToEarlier(stop)) {
            stop++;
        }
        go(stop);
    }

    public void previous() {
        if (!active) {
            return;
        }
        updateLinks();
        int stop = current - 1;
        while (stop > 0 && isLinkedToEarlier(stop)) {
            stop--;
        }
        if (stop >= 0) {
            go(stop);
        }
    }

    /** Whether the stop repeats the name of one before it, and so is filled in from there. */
    private boolean isLinkedToEarlier(int stop) {
        for (int i = 0; i < stop; i++) {
            if (names[stop] != null && names[stop].equals(names[i])) {
                return true;
            }
        }
        return false;
    }

    private void go(int stop) {
        current = stop;
        moving = true;
        try {
            editor.select(starts[stop], ends[stop]);
        } finally {
            moving = false;
        }
        if (stop == starts.length - 1) {
            end();
        }
    }

    /** Stops stepping through the tab stops and gives the keys back; the text stays as it is. */
    public void end() {
        if (!active) {
            return;
        }
        updateLinks();
        active = false;
        beforeEnd.run(); // lets a popup opened on top give its keys back first
        keys.restore();
        model.removeTextListener(textListener);
        editor.removeSelectionListener(caretListener);
        editor.removeEditListener(editListener);
    }

    private void caretMoved() {
        if (moving) {
            return;
        }
        if (editor.getModel() != model) {
            end();
            return;
        }
        // During an edit the caret is reported before the tab stops have followed the change.
        // Judging it then would be judging it against where the snippet used to end, so wait
        // for the caret event that the edit finishes with.
        if (model.length() != knownLength) {
            return;
        }
        int caret = editor.getCaretPosition();
        if (caret < snippetStart || caret > snippetEnd) {
            end();
        }
    }

    /** Keeps the tab stops on their text while it is edited. */
    private void textChanged(TextChange change) {
        int from = change.offset();
        int to = from + change.removedLength();
        int shift = change.insertedLength() - change.removedLength();
        boolean inCurrent = from >= starts[current] && to <= ends[current];
        for (int i = 0; i < starts.length; i++) {
            // Text typed at the current stop belongs to it: its start stays and its end moves.
            // The same place is the end of an earlier stop, which stays, and the start of a
            // later one, which moves.
            starts[i] = moved(starts[i], from, to, shift, i > current);
            ends[i] = moved(ends[i], from, to, shift, i >= current);
        }
        snippetStart = moved(snippetStart, from, to, shift, false);
        snippetEnd = moved(snippetEnd, from, to, shift, true);
        knownLength = model.length();
        if (inCurrent && !moving && hasLinks(current)) {
            // The linked stops cannot be changed from inside this notification of a change;
            // they follow once the editor reports the edit as complete.
            linksPending = true;
        }
    }

    private boolean hasLinks(int stop) {
        for (int i = 0; i < names.length; i++) {
            if (i != stop && names[stop] != null && names[stop].equals(names[i])) {
                return true;
            }
        }
        return false;
    }

    /** Copies the text of the current tab stop into those of the same name, as part of the edit that changed it. */
    private void updateLinks() {
        if (!active || !linksPending) {
            return;
        }
        linksPending = false;
        String text = model.getText(starts[current], ends[current]);
        int anchor = editor.getSelectionAnchor() - starts[current];
        int caret = editor.getCaretPosition() - starts[current];
        moving = true;
        updatingLinks = true;
        try {
            editor.appendToLastEdit(() -> {
                for (int i = 0; i < names.length; i++) {
                    if (i == current || !names[current].equals(names[i])
                            || model.getText(starts[i], ends[i]).equals(text)) {
                        continue;
                    }
                    int start = starts[i];
                    editor.replaceRange(start, ends[i], text);
                    // exactly the new text, whatever an empty stop made of the change
                    starts[i] = start;
                    ends[i] = start + text.length();
                }
            });
            // back to where the user is typing; the linked stops before it have moved it along
            editor.select(starts[current] + anchor, starts[current] + caret);
        } finally {
            moving = false;
            updatingLinks = false;
        }
        knownLength = model.length();
    }

    /** @param sticksToEnd whether a position exactly at an insertion moves along with the inserted text */
    private static int moved(int position, int from, int to, int shift, boolean sticksToEnd) {
        if (position < from || (position == from && !(sticksToEnd && to == from))) {
            return position;
        }
        return position >= to ? position + shift : from;
    }
}
