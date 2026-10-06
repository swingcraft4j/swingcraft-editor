package com.swingcraft4j.code.text;

/**
 * A reusable {@link CharSequence} view of the start of a char array, for running regular
 * expressions over line buffers without copying them.
 */
public final class CharArraySequence implements CharSequence {

    private char[] chars = new char[0];
    private int length;

    /** Points the view at {@code chars[0, length)}. */
    public void set(char[] chars, int length) {
        this.chars = chars;
        this.length = length;
    }

    @Override
    public int length() {
        return length;
    }

    @Override
    public char charAt(int index) {
        return chars[index];
    }

    @Override
    public CharSequence subSequence(int start, int end) {
        return new String(chars, start, end - start);
    }

    @Override
    public String toString() {
        return new String(chars, 0, length);
    }
}
