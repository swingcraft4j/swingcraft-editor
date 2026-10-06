package com.swingcraft4j.code.text;

/** A {@link TextModel} whose text can be changed. It is used from one thread only. */
public interface EditableTextModel extends TextModel {

    /** Replaces the chars {@code [start, end)} with the text and notifies the listeners. */
    void replace(int start, int end, CharSequence text);

    void addTextListener(TextListener listener);

    void removeTextListener(TextListener listener);
}
