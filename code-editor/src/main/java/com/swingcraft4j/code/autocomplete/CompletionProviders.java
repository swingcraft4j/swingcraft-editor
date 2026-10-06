package com.swingcraft4j.code.autocomplete;

import com.swingcraft4j.code.text.TextModel;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** The providers that come with the library. All of them answer on the event dispatch thread. */
public final class CompletionProviders {

    /** How far above and below the caret the document is read for words. */
    private static final int WORD_SCAN_LINES = 1500;
    private static final int MIN_WORD_LENGTH = 3;

    private CompletionProviders() {
    }

    /**
     * Offers the keywords of the document's language. When the prefix is typed in capitals
     * and the keyword is in lower case, as for SQL, the keyword is offered in capitals.
     */
    public static CompletionProvider keywords() {
        return request -> {
            List<Completion> result = new ArrayList<>();
            // after a dot comes a member, not a keyword
            if (request.language() == null || request.charBeforePrefix() == '.') {
                return result;
            }
            String prefix = request.prefix();
            boolean capitals = !prefix.isEmpty() && prefix.equals(prefix.toUpperCase(Locale.ROOT))
                    && !prefix.equals(prefix.toLowerCase(Locale.ROOT));
            for (String keyword : request.language().keywords()) {
                boolean lowerCase = keyword.equals(keyword.toLowerCase(Locale.ROOT));
                result.add(new Completion(capitals && lowerCase ? keyword.toUpperCase(Locale.ROOT) : keyword,
                        CompletionKind.KEYWORD));
            }
            return result;
        };
    }

    /** Offers the snippets that come with the library for the document's language; see {@link Snippets}. */
    public static CompletionProvider snippets() {
        return request -> request.language() == null || request.charBeforePrefix() == '.'
                ? List.of()
                : Snippets.forLanguage(request.language().id());
    }

    /**
     * Offers the words already in the document, nearest to the caret first. Only the lines
     * around the caret are read, which keeps it quick in a very large document.
     */
    public static CompletionProvider words() {
        return request -> {
            if (request.prefix().isEmpty() && request.charBeforePrefix() == '.') {
                return List.of(); // right after a dot only members make sense, not every word around
            }
            TextModel model = request.model();
            int caretLine = model.lineOfOffset(request.offset());
            int first = Math.max(0, caretLine - WORD_SCAN_LINES);
            int last = Math.min(model.lineCount() - 1, caretLine + WORD_SCAN_LINES);
            Set<String> words = new LinkedHashSet<>();
            char[] buffer = new char[256];
            // outwards from the caret line, so that the nearest words come first
            for (int distance = 0; caretLine - distance >= first || caretLine + distance <= last; distance++) {
                for (int line : distance == 0 ? new int[]{caretLine} : new int[]{caretLine - distance, caretLine + distance}) {
                    if (line < first || line > last) {
                        continue;
                    }
                    int start = model.lineStart(line);
                    int length = model.lineLength(line);
                    if (length > buffer.length) {
                        buffer = new char[Math.max(length, buffer.length * 2)];
                    }
                    model.getChars(start, start + length, buffer, 0);
                    collectWords(buffer, length, request.offset() - start, words);
                }
            }
            List<Completion> result = new ArrayList<>(words.size());
            for (String word : words) {
                result.add(new Completion(word));
            }
            return result;
        };
    }

    /**
     * @param caret the index of the caret in the line, or a value outside it: the word the
     *              caret is in or right after is the one being typed, and is left out
     */
    private static void collectWords(char[] text, int length, int caret, Set<String> into) {
        int i = 0;
        while (i < length) {
            if (!isWordStart(text[i])) {
                i++;
                continue;
            }
            int end = i + 1;
            while (end < length && isWordPart(text[end])) {
                end++;
            }
            boolean beingTyped = caret >= i && caret <= end;
            if (end - i >= MIN_WORD_LENGTH && !beingTyped) {
                into.add(new String(text, i, end - i));
            }
            i = end;
        }
    }

    static boolean isWordStart(char c) {
        return Character.isLetter(c) || c == '_' || c == '$';
    }

    static boolean isWordPart(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '$';
    }
}
