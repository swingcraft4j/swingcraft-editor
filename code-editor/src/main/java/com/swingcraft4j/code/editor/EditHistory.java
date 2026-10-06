package com.swingcraft4j.code.editor;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * The undo and redo stacks of an editor. Each entry is a group of edits undone together:
 * a run of typed chars, a run of deletions, or the edits made by one compound action.
 */
final class EditHistory {

    private static final int MAX_GROUPS = 5000;

    /**
     * One replacement: {@code removed} at {@code offset} became {@code inserted}. The
     * selection before it is kept so that undo can restore it.
     */
    record Edit(int offset, String removed, String inserted, int anchorBefore, int caretBefore) {
    }

    /** How an edit may join the group before it. */
    enum Kind {
        OTHER, TYPING, DELETE_BACKWARD, DELETE_FORWARD
    }

    private static final class Group {
        final List<Edit> edits = new ArrayList<>();
        final Kind kind;
        boolean closed;

        Group(Kind kind) {
            this.kind = kind;
        }
    }

    private final Deque<Group> undoStack = new ArrayDeque<>();
    private final Deque<Group> redoStack = new ArrayDeque<>();
    private int compoundDepth;
    private Group compound;
    private boolean joining;

    /** Stands for the top of an empty undo stack. */
    private static final Group NOTHING_DONE = new Group(Kind.OTHER);
    /** Stands for a saved state that undo and redo cannot get back to. */
    private static final Group OUT_OF_REACH = new Group(Kind.OTHER);
    /** The group on top of the undo stack when the text was saved. */
    private Group savedTop = NOTHING_DONE;

    void record(Edit edit, Kind kind) {
        redoStack.clear();
        if (joining) {
            Group last = undoStack.peekLast();
            (last != null ? last : push(Kind.OTHER)).edits.add(edit);
            return;
        }
        if (compoundDepth > 0) {
            if (compound == null) {
                compound = push(Kind.OTHER);
            }
            compound.edits.add(edit);
            return;
        }
        Group last = undoStack.peekLast();
        if (last != null && !last.closed && last.kind == kind && kind != Kind.OTHER
                && continues(last.edits.get(last.edits.size() - 1), edit, kind)) {
            last.edits.add(edit);
        } else {
            push(kind).edits.add(edit);
        }
    }

    private Group push(Kind kind) {
        Group group = new Group(kind);
        undoStack.addLast(group);
        if (undoStack.size() > MAX_GROUPS) {
            Group dropped = undoStack.removeFirst();
            // the state before the dropped group, or right after it, can no longer be undone to
            if (savedTop == NOTHING_DONE || savedTop == dropped) {
                savedTop = OUT_OF_REACH;
            }
        }
        return group;
    }

    /** Remembers the present state as the one that was saved. */
    void markSaved() {
        closeGroup(); // further typing must not slip into the group that was saved
        savedTop = undoStack.isEmpty() ? NOTHING_DONE : undoStack.peekLast();
    }

    /** Whether the text is as it was when last marked as saved, undo and redo taken into account. */
    boolean isAtSavedState() {
        return savedTop == (undoStack.isEmpty() ? NOTHING_DONE : undoStack.peekLast());
    }

    private static boolean continues(Edit previous, Edit edit, Kind kind) {
        switch (kind) {
            case TYPING:
                if (!edit.removed().isEmpty() || previous.inserted().isEmpty() || edit.inserted().isEmpty()
                        || edit.offset() != previous.offset() + previous.inserted().length()) {
                    return false;
                }
                // a new word starts a new group, so undo steps back word by word
                char before = previous.inserted().charAt(previous.inserted().length() - 1);
                return !(Character.isWhitespace(before) && !Character.isWhitespace(edit.inserted().charAt(0)));
            case DELETE_BACKWARD:
                return edit.offset() + edit.removed().length() == previous.offset();
            case DELETE_FORWARD:
                return edit.offset() == previous.offset();
            default:
                return false;
        }
    }

    /** Stops the last group from growing, as after the caret was moved. */
    void closeGroup() {
        Group last = undoStack.peekLast();
        if (last != null) {
            last.closed = true;
        }
    }

    /** Until the matching {@link #endJoin()}, every edit joins the group of the edit before it. */
    void beginJoin() {
        joining = true;
    }

    void endJoin() {
        joining = false;
    }

    /** Until the matching {@link #endCompound()}, every edit joins one group. */
    void beginCompound() {
        if (compoundDepth++ == 0) {
            compound = null;
        }
    }

    void endCompound() {
        if (--compoundDepth == 0) {
            compound = null;
            closeGroup();
        }
    }

    boolean canUndo() {
        return !undoStack.isEmpty();
    }

    boolean canRedo() {
        return !redoStack.isEmpty();
    }

    /** The edits to revert, in the order they were made, or null when there is nothing to undo. */
    List<Edit> undo() {
        Group group = undoStack.pollLast();
        if (group == null) {
            return null;
        }
        group.closed = true;
        redoStack.addLast(group);
        return group.edits;
    }

    /** The edits to apply again, in order, or null when there is nothing to redo. */
    List<Edit> redo() {
        Group group = redoStack.pollLast();
        if (group == null) {
            return null;
        }
        undoStack.addLast(group);
        return group.edits;
    }

    /** Forgets every edit. The saved state can then not be got back to, until it is marked again. */
    void clear() {
        undoStack.clear();
        redoStack.clear();
        compound = null;
        savedTop = OUT_OF_REACH;
    }
}
