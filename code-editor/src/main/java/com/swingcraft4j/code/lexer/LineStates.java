package com.swingcraft4j.code.lexer;

import com.swingcraft4j.code.text.TextModel;

/**
 * The lexer state at the start of every line of an unchanging model.
 * <p>
 * The first part of the document is lexed by the constructor so that small files are ready at
 * once; the rest is filled in by a background thread. Lines whose state is not known yet
 * report {@code -1} and can be shown unhighlighted until the progress callback fires.
 */
public final class LineStates implements LineStateIndex {

    /** Chars lexed on the constructing thread before handing over to the worker. */
    private static final int SYNC_CHARS = 1 << 18;
    private static final int NOTIFY_LINES = 50_000;
    private static final TokenSink DISCARD = (start, length, type) -> {
    };

    private final TextModel model;
    private final Lexer lexer;
    private final Runnable onProgress;
    private final int[] states;
    /** Lines {@code [0, ready)} have a known start state. Written after the array slot. */
    private volatile int ready = 1;
    private volatile boolean disposed;

    /**
     * @param lexer      an instance used only by this object
     * @param onProgress called on the worker thread as more lines become ready
     */
    public LineStates(TextModel model, Lexer lexer, Runnable onProgress) {
        this.model = model;
        this.lexer = lexer;
        this.onProgress = onProgress;
        this.states = new int[model.lineCount()];
        states[0] = Lexer.INITIAL_STATE;
        int next = lex(0, SYNC_CHARS, false);
        if (next < states.length - 1) {
            Thread worker = new Thread(() -> {
                lex(next, Long.MAX_VALUE, true);
                if (!disposed) {
                    onProgress.run();
                }
            }, "code-line-states");
            worker.setDaemon(true);
            worker.setPriority(Thread.NORM_PRIORITY - 1);
            worker.start();
        }
    }

    @Override
    public int startState(int line) {
        return line < ready ? states[line] : -1;
    }

    @Override
    public void dispose() {
        disposed = true;
    }

    private int lex(int line, long charBudget, boolean notify) {
        char[] buffer = new char[256];
        int last = states.length - 1;
        while (line < last && charBudget > 0 && !disposed) {
            int start = model.lineStart(line);
            int length = model.lineLength(line);
            if (length > buffer.length) {
                buffer = new char[Math.max(length, buffer.length * 2)];
            }
            model.getChars(start, start + length, buffer, 0);
            states[line + 1] = lexer.tokenize(buffer, 0, length, states[line], DISCARD);
            line++;
            ready = line + 1;
            charBudget -= length + 1;
            if (notify && line % NOTIFY_LINES == 0) {
                onProgress.run();
            }
        }
        return line;
    }
}
