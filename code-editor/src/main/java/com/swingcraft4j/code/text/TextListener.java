package com.swingcraft4j.code.text;

/** Notified after an {@link EditableTextModel} changed. */
@FunctionalInterface
public interface TextListener {

    void textChanged(TextChange change);
}
