package com.swingcraft4j.code.languages.html;

import com.swingcraft4j.code.languages.xml.XmlLexer;
import com.swingcraft4j.code.lexer.Lexer;
import com.swingcraft4j.code.lexer.TokenSink;
import com.swingcraft4j.code.lexer.TokenType;

/**
 * Lexer for HTML: the markup is read by the lexer of XML, what is inside a {@code script}
 * element by the one of JavaScript and what is inside a {@code style} element by the one of CSS.
 * <p>
 * The state is the part of the document the line starts in, times 256, plus the state of the
 * lexer that reads that part.
 */
final class HtmlLexer implements Lexer {

    private static final int MARKUP = 0;
    private static final int SCRIPT = 1;
    private static final int STYLE = 2;
    /** Inside the opening tag of a script or a style element, which may run over lines. */
    private static final int SCRIPT_TAG = 3;
    private static final int STYLE_TAG = 4;
    private static final int PARTS = 256;

    private final Lexer markup = new XmlLexer();
    private final Lexer script;
    private final Lexer style;

    // the tokens of the markup of one line, looked through for a script or a style tag before they are passed on
    private int[] starts = new int[64];
    private int[] lengths = new int[64];
    private TokenType[] types = new TokenType[64];
    private int count;
    private final TokenSink recorder = (start, length, type) -> {
        if (count == starts.length) {
            starts = java.util.Arrays.copyOf(starts, count * 2);
            lengths = java.util.Arrays.copyOf(lengths, count * 2);
            types = java.util.Arrays.copyOf(types, count * 2);
        }
        starts[count] = start;
        lengths[count] = length;
        types[count++] = type;
    };

    HtmlLexer(Lexer script, Lexer style) {
        this.script = script;
        this.style = style;
    }

    @Override
    public int tokenize(char[] text, int start, int end, int state, TokenSink sink) {
        int part = state / PARTS;
        int inner = state % PARTS;
        int position = start;
        while (true) {
            if (part == SCRIPT || part == STYLE) {
                // as a browser does it: the element ends at its closing tag wherever that stands, also in a string
                int close = indexOfIgnoreCase(text, position, end, part == SCRIPT ? "</script" : "</style");
                inner = (part == SCRIPT ? script : style).tokenize(text, position, close < 0 ? end : close, inner, sink);
                if (close < 0) {
                    return part * PARTS + inner;
                }
                part = MARKUP;
                inner = INITIAL_STATE;
                position = close;
                continue;
            }
            count = 0;
            int endState = markup.tokenize(text, position, end, inner, recorder);
            // the index of the > that ends the opening tag of a script or a style element, if there is one
            int opened = -1;
            for (int i = 0; i < count && opened < 0; i++) {
                if (types[i] != TokenType.PUNCTUATION || lengths[i] != 1) {
                    continue;
                }
                char c = text[starts[i]];
                if (c == '<' && i + 1 < count && types[i + 1] == TokenType.TAG) {
                    part = is(text, starts[i + 1], lengths[i + 1], "script") ? SCRIPT_TAG
                            : is(text, starts[i + 1], lengths[i + 1], "style") ? STYLE_TAG : MARKUP;
                } else if (c == '>' && part != MARKUP) {
                    boolean selfClosed = i > 0 && lengths[i - 1] == 1 && text[starts[i - 1]] == '/' && starts[i - 1] + 1 == starts[i];
                    if (selfClosed) {
                        part = MARKUP;
                    } else {
                        opened = i;
                    }
                }
            }
            int passed = opened < 0 ? count : opened + 1;
            for (int i = 0; i < passed; i++) {
                sink.token(starts[i], lengths[i], types[i]);
            }
            if (opened < 0) {
                return part * PARTS + endState;
            }
            part = part == SCRIPT_TAG ? SCRIPT : STYLE;
            inner = INITIAL_STATE;
            position = starts[opened] + 1;
        }
    }

    /** Whether the chars are the word, in any case. */
    private static boolean is(char[] text, int start, int length, String word) {
        if (length != word.length()) {
            return false;
        }
        for (int i = 0; i < length; i++) {
            if (Character.toLowerCase(text[start + i]) != word.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    private static int indexOfIgnoreCase(char[] text, int from, int end, String word) {
        for (int i = from; i + word.length() <= end; i++) {
            if (is(text, i, word.length(), word)) {
                return i;
            }
        }
        return -1;
    }
}
