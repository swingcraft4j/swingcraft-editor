package com.swingcraft4j.code.text;

/**
 * One replacement in an {@link EditableTextModel}, described after it happened.
 * <p>
 * In chars, {@code removedLength} chars at {@code offset} were replaced by
 * {@code insertedLength} chars. In lines, the old lines {@code firstLine} to
 * {@code firstLine + removedLines} were replaced by the lines {@code firstLine} to
 * {@code firstLine + insertedLines}; lines before are untouched and lines after only moved.
 * The line range may be wider than strictly necessary.
 */
public record TextChange(int offset, int removedLength, int insertedLength,
                         int firstLine, int removedLines, int insertedLines) {
}
