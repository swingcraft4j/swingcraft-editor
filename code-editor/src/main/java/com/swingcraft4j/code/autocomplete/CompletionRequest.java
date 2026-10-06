package com.swingcraft4j.code.autocomplete;

import com.swingcraft4j.code.lexer.Language;
import com.swingcraft4j.code.text.TextModel;

/**
 * What a {@link CompletionProvider} is asked to complete.
 * <p>
 * Everything here except {@link #model()} is a snapshot that can be read from any thread. The
 * model is the live document: a provider running in the background must not touch it.
 *
 * @param model      the document; only for providers called on the event dispatch thread
 * @param offset     the caret position
 * @param prefix     the part of a word typed just before the caret; may be empty
 * @param language   the language of the document, or null for plain text
 * @param lineBefore the text of the caret's line up to the caret, the prefix included
 */
public record CompletionRequest(TextModel model, int offset, String prefix, Language language, String lineBefore) {

    /** No more of a very long line than this is kept before the caret. */
    private static final int MAX_LINE_BEFORE = 2000;

    /** The request for a caret position in a document. Call it on the event dispatch thread. */
    public static CompletionRequest at(TextModel model, int offset, Language language) {
        int lineStart = Math.max(model.lineStart(model.lineOfOffset(offset)), offset - MAX_LINE_BEFORE);
        String lineBefore = model.getText(lineStart, offset);
        int start = lineBefore.length();
        while (start > 0 && CompletionProviders.isWordPart(lineBefore.charAt(start - 1))) {
            start--;
        }
        // a word does not start with a digit, so a number on its own has nothing to complete
        while (start < lineBefore.length() && !CompletionProviders.isWordStart(lineBefore.charAt(start))) {
            start++;
        }
        return new CompletionRequest(model, offset, lineBefore.substring(start), language, lineBefore);
    }

    /** Where the word being completed starts. */
    public int prefixStart() {
        return offset - prefix.length();
    }

    /** The char just before the word being completed, or {@code '\0'} at the start of a line. */
    public char charBeforePrefix() {
        int index = lineBefore.length() - prefix.length() - 1;
        return index >= 0 ? lineBefore.charAt(index) : '\0';
    }

    /**
     * The word before the char that precedes the prefix: for {@code items.ad} with the caret
     * at the end it is {@code items}. Empty when there is no such word.
     */
    public String qualifier() {
        int end = lineBefore.length() - prefix.length() - 1;
        if (end < 0) {
            return "";
        }
        int start = end;
        while (start > 0 && CompletionProviders.isWordPart(lineBefore.charAt(start - 1))) {
            start--;
        }
        return lineBefore.substring(start, end);
    }
}
