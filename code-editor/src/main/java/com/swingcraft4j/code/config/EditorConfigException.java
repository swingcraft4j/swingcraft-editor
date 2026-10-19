package com.swingcraft4j.code.config;

import java.io.IOException;

/** A config file that is not well-formed XML, or says something an {@link EditorConfig} does not know. */
public final class EditorConfigException extends IOException {

    private final int lineNumber;

    EditorConfigException(String message, int lineNumber) {
        super(lineNumber > 0 ? "line " + lineNumber + ": " + message : message);
        this.lineNumber = lineNumber;
    }

    /** The one-based line of the file where the problem is, or -1 when it is not known. */
    public int getLineNumber() {
        return lineNumber;
    }
}
