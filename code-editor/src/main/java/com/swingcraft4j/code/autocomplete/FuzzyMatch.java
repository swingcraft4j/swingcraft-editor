package com.swingcraft4j.code.autocomplete;

/**
 * Matches what was typed against a suggestion, loosely: {@code tv} finds {@code totalValue}
 * and {@code ISE} finds {@code IllegalStateException}. The closer the match, the lower its
 * tier and the earlier the suggestion is listed.
 */
final class FuzzyMatch {

    /** The suggestion starts with the pattern, in the same case. */
    static final int EXACT_PREFIX = 0;
    /** The suggestion starts with the pattern apart from case. */
    static final int PREFIX = 1;
    /** Each char of the pattern starts a word of the suggestion or follows the char before it. */
    static final int WORD_STARTS = 2;
    /** The chars of the pattern appear in order, starting with the first char of the suggestion. */
    static final int SCATTERED = 3;

    /**
     * @param tier      how close the match is
     * @param positions the indexes in the suggestion of the chars that matched
     */
    record Result(int tier, int[] positions) {
    }

    private FuzzyMatch() {
    }

    /** How the pattern matches the text, or null when it does not. */
    static Result match(String pattern, String text) {
        int length = pattern.length();
        if (length == 0) {
            return new Result(EXACT_PREFIX, new int[0]);
        }
        if (length > text.length()) {
            return null;
        }
        if (text.regionMatches(true, 0, pattern, 0, length)) {
            int[] positions = new int[length];
            for (int i = 0; i < length; i++) {
                positions[i] = i;
            }
            return new Result(text.startsWith(pattern) ? EXACT_PREFIX : PREFIX, positions);
        }
        int[] positions = new int[length];
        for (int start = 0; start < text.length(); start++) {
            if (isWordStart(text, start) && same(text.charAt(start), pattern.charAt(0))
                    && matchWordStarts(pattern, text, start, positions)) {
                return new Result(WORD_STARTS, positions);
            }
        }
        // one char matches far too much when it may sit anywhere
        if (length >= 2 && same(text.charAt(0), pattern.charAt(0)) && matchScattered(pattern, text, positions)) {
            return new Result(SCATTERED, positions);
        }
        return null;
    }

    private static boolean matchWordStarts(String pattern, String text, int start, int[] positions) {
        positions[0] = start;
        int at = start + 1;
        for (int p = 1; p < pattern.length(); p++) {
            char wanted = pattern.charAt(p);
            // carry on within the word if possible, else jump to the next word starting with it
            if (at < text.length() && same(text.charAt(at), wanted) && !isWordStart(text, at)) {
                positions[p] = at++;
                continue;
            }
            int next = at;
            while (next < text.length() && !(isWordStart(text, next) && same(text.charAt(next), wanted))) {
                next++;
            }
            if (next == text.length()) {
                return false;
            }
            positions[p] = next;
            at = next + 1;
        }
        return true;
    }

    private static boolean matchScattered(String pattern, String text, int[] positions) {
        int at = 0;
        for (int p = 0; p < pattern.length(); p++) {
            while (at < text.length() && !same(text.charAt(at), pattern.charAt(p))) {
                at++;
            }
            if (at == text.length()) {
                return false;
            }
            positions[p] = at++;
        }
        return true;
    }

    /** Whether a word starts at the index: at the start, at a capital after a small letter, or after a separator. */
    private static boolean isWordStart(String text, int index) {
        if (index == 0) {
            return true;
        }
        char c = text.charAt(index);
        char previous = text.charAt(index - 1);
        return (Character.isUpperCase(c) && !Character.isUpperCase(previous))
                || (Character.isLetterOrDigit(c) && !Character.isLetterOrDigit(previous));
    }

    private static boolean same(char a, char b) {
        return a == b || Character.toLowerCase(a) == Character.toLowerCase(b);
    }
}
