package com.swingcraft4j.code.languages.diff;

import com.swingcraft4j.code.lexer.Language;
import com.swingcraft4j.code.lexer.Lexer;
import com.swingcraft4j.code.lexer.TokenSink;
import com.swingcraft4j.code.lexer.TokenType;

import java.util.List;

/**
 * The unified diffs that {@code git diff} prints and that {@link com.swingcraft4j.code.diff.UnifiedDiff}
 * writes, and the patches made of them. A line put in is {@link TokenType#INSERTED}, a line
 * taken away {@link TokenType#DELETED}, the header of a hunk a {@link TokenType#TYPE} and the
 * header of a file a {@link TokenType#KEYWORD}.
 * <p>
 * Inside a hunk the lexer counts the lines its header gives, so a line taken away that starts
 * with {@code --}, as a comment of SQL does, is not mistaken for the header of the next file.
 */
public final class DiffLanguage implements Language {

    @Override
    public String id() {
        return "diff";
    }

    @Override
    public String displayName() {
        return "Diff";
    }

    @Override
    public List<String> fileExtensions() {
        return List.of("diff", "patch");
    }

    @Override
    public Lexer createLexer() {
        return new DiffLexer();
    }

    /**
     * State 0 is outside a hunk. Inside one, the state holds how many lines of the original and
     * of the revised version are still to come: {@code 1 + (original << 15 | revised)}. A hunk
     * too long to count that way gets {@link #UNCOUNTED}, where every line is read by its mark.
     */
    private static final class DiffLexer implements Lexer {

        private static final int BITS = 15;
        private static final int MAX_COUNT = (1 << BITS) - 1;
        private static final int UNCOUNTED = Integer.MAX_VALUE;

        /** The starts of the lines of the header of a file, as git writes them. */
        private static final String[] FILE_HEADERS = {
                "diff ", "index ", "--- ", "+++ ", "new file mode ", "deleted file mode ", "old mode ", "new mode ",
                "similarity index ", "dissimilarity index ", "rename from ", "rename to ", "copy from ", "copy to ",
                "Binary files "};

        @Override
        public int tokenize(char[] text, int start, int end, int state, TokenSink sink) {
            if (start == end) {
                // an empty line in a hunk is an unchanged empty line to a tool that dropped the blank
                return state == INITIAL_STATE || state == UNCOUNTED ? state : count(state, true, true);
            }
            if (startsWith(text, start, end, "@@")) {
                return hunkHeader(text, start, end, sink);
            }
            char mark = text[start];
            if (state != INITIAL_STATE) {
                switch (mark) {
                    case '+':
                        sink.token(start, end - start, TokenType.INSERTED);
                        return count(state, false, true);
                    case '-':
                        sink.token(start, end - start, TokenType.DELETED);
                        return count(state, true, false);
                    case ' ':
                        return count(state, true, true);
                    case '\\':
                        sink.token(start, end - start, TokenType.COMMENT);
                        return state;
                    default:
                        // the hunk ended early: read the line as one outside a hunk, which ends in state 0
                        break;
                }
            }
            for (String header : FILE_HEADERS) {
                if (startsWith(text, start, end, header)) {
                    sink.token(start, end - start, TokenType.KEYWORD);
                    return INITIAL_STATE;
                }
            }
            if (mark == '+') {
                sink.token(start, end - start, TokenType.INSERTED);
            } else if (mark == '-') {
                sink.token(start, end - start, TokenType.DELETED);
            } else if (mark == '\\') {
                sink.token(start, end - start, TokenType.COMMENT);
            }
            return INITIAL_STATE;
        }

        /** Shows {@code @@ -1,4 +1,5 @@} as a type and what follows, such as the name of a function, as text; and starts the count. */
        private static int hunkHeader(char[] text, int start, int end, TokenSink sink) {
            int close = indexOf(text, start + 2, end, "@@");
            int headerEnd = close < 0 ? end : close + 2;
            sink.token(start, headerEnd - start, TokenType.TYPE);
            int i = start + 2;
            int[] original = range(text, i, headerEnd, '-');
            int[] revised = range(text, i, headerEnd, '+');
            if (original == null || revised == null) {
                return UNCOUNTED;
            }
            return inHunk(original[1], revised[1]);
        }

        /** The start and length after the mark, as in {@code -12,3}; the length is 1 where it is left out. Null where there is none. */
        private static int[] range(char[] text, int from, int end, char mark) {
            int i = from;
            while (i < end && text[i] != mark) {
                i++;
            }
            i++;
            long startLine = 0;
            int digits = 0;
            while (i < end && isDigit(text[i])) {
                startLine = Math.min(startLine * 10 + (text[i] - '0'), Integer.MAX_VALUE);
                i++;
                digits++;
            }
            if (digits == 0) {
                return null;
            }
            long length = 1;
            if (i < end && text[i] == ',') {
                i++;
                length = 0;
                digits = 0;
                while (i < end && isDigit(text[i])) {
                    length = Math.min(length * 10 + (text[i] - '0'), Integer.MAX_VALUE);
                    i++;
                    digits++;
                }
                if (digits == 0) {
                    return null;
                }
            }
            return new int[]{(int) startLine, (int) length};
        }

        private static int inHunk(int original, int revised) {
            if (original == 0 && revised == 0) {
                return INITIAL_STATE;
            }
            if (original > MAX_COUNT || revised > MAX_COUNT) {
                return UNCOUNTED;
            }
            return 1 + (original << BITS | revised);
        }

        /** The state after a line of the hunk that belongs to the original, the revised version or both. */
        private static int count(int state, boolean inOriginal, boolean inRevised) {
            if (state == UNCOUNTED) {
                return state;
            }
            int original = (state - 1) >>> BITS;
            int revised = (state - 1) & MAX_COUNT;
            if (inOriginal) {
                original = Math.max(0, original - 1);
            }
            if (inRevised) {
                revised = Math.max(0, revised - 1);
            }
            return inHunk(original, revised);
        }

        private static boolean isDigit(char c) {
            return c >= '0' && c <= '9';
        }

        private static boolean startsWith(char[] text, int at, int end, String prefix) {
            if (at + prefix.length() > end) {
                return false;
            }
            for (int i = 0; i < prefix.length(); i++) {
                if (text[at + i] != prefix.charAt(i)) {
                    return false;
                }
            }
            return true;
        }

        private static int indexOf(char[] text, int from, int end, String what) {
            for (int i = from; i + what.length() <= end; i++) {
                if (startsWith(text, i, end, what)) {
                    return i;
                }
            }
            return -1;
        }
    }
}
