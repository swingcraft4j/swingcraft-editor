package com.swingcraft4j.code.lexer;

import com.swingcraft4j.code.text.TextModel;

import java.util.Arrays;

/**
 * {@link LineStateIndex} for a model that changes. It uses no thread of its own: the owner
 * reports each change with {@link #linesReplaced} and then calls {@link #process} in slices,
 * from the thread that edits the model, until it reports that nothing is left to do.
 * <p>
 * After a change the lines are lexed again from the first changed one. As soon as a line
 * past the change ends in the state it had before, the rest is known to be unchanged, so an
 * ordinary edit costs one line and only one that opens or closes a multi-line construct
 * runs further.
 */
public final class IncrementalLineStates implements LineStateIndex {

    private static final TokenSink DISCARD = (start, length, type) -> {
    };

    private final TextModel model;
    private final Lexer lexer;
    private int[] states;
    private int lineCount;
    /** Lines {@code [0, valid)} have a current start state. */
    private int valid = 1;
    /**
     * The states from this line on predate the pending work but follow from one another: if
     * lexing arrives here with the stored state, every later one is right too. Equal to the
     * line count when there are none.
     */
    private int chainStart;
    private char[] buffer = new char[256];

    /** @param lexer an instance used only by this object */
    public IncrementalLineStates(TextModel model, Lexer lexer) {
        this.model = model;
        this.lexer = lexer;
        this.lineCount = model.lineCount();
        this.states = new int[lineCount + 16];
        this.chainStart = lineCount;
        states[0] = Lexer.INITIAL_STATE;
    }

    /** Also answers with the earlier state of a line past the pending work, which is usually still right. */
    @Override
    public int startState(int line) {
        return line < valid || line >= chainStart ? states[line] : -1;
    }

    @Override
    public void dispose() {
    }

    /** Whether lines are still waiting to be lexed. */
    public boolean isPending() {
        return valid < lineCount;
    }

    /**
     * Records that the model replaced the lines {@code firstLine} to
     * {@code firstLine + removedLines} by the lines {@code firstLine} to
     * {@code firstLine + insertedLines}.
     */
    public void linesReplaced(int firstLine, int removedLines, int insertedLines) {
        int oldCount = lineCount;
        int newCount = oldCount - removedLines + insertedLines;
        int oldTail = firstLine + removedLines + 1;
        int newTail = firstLine + insertedLines + 1;
        if (newCount > states.length) {
            states = Arrays.copyOf(states, newCount + newCount / 8 + 16);
        }
        if (oldTail != newTail) {
            System.arraycopy(states, oldTail, states, newTail, oldCount - oldTail);
        }

        // The lines after this change carry on a chain of earlier states, unless work was
        // pending on a chain that starts even later.
        boolean pending = valid < oldCount;
        int chain = pending && chainStart >= oldTail ? chainStart - oldTail + newTail : newTail;
        valid = Math.min(pending ? valid : oldCount, firstLine + 1);
        lineCount = newCount;
        chainStart = Math.min(chain, newCount);
    }

    /**
     * Lexes pending lines for about the given time.
     *
     * @return whether lines are still pending
     */
    public boolean process(long nanos) {
        long deadline = System.nanoTime() + nanos;
        int sinceCheck = 0;
        while (valid < lineCount) {
            int line = valid - 1;
            int start = model.lineStart(line);
            int length = model.lineLength(line);
            if (length > buffer.length) {
                buffer = new char[Math.max(length, buffer.length * 2)];
            }
            model.getChars(start, start + length, buffer, 0);
            int state = lexer.tokenize(buffer, 0, length, states[line], DISCARD);
            if (valid >= chainStart && states[valid] == state) {
                valid = lineCount;
                break;
            }
            states[valid++] = state;
            sinceCheck += length + 1;
            if (sinceCheck >= 4096) {
                sinceCheck = 0;
                // nanoTime - deadline: correct even if the deadline overflowed
                if (System.nanoTime() - deadline >= 0) {
                    break;
                }
            }
        }
        if (valid >= lineCount) {
            chainStart = lineCount;
        }
        return valid < lineCount;
    }
}
