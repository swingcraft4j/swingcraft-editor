package com.swingcraft4j.code.languages.xml;

import com.swingcraft4j.code.lexer.Lexer;
import com.swingcraft4j.code.lexer.TokenSink;
import com.swingcraft4j.code.lexer.TokenType;

/** Lexer for XML and HTML markup. */
public final class XmlLexer implements Lexer {

    private static final int TEXT = INITIAL_STATE;
    private static final int IN_COMMENT = 1;
    private static final int IN_CDATA = 2;
    private static final int IN_TAG = 3;
    private static final int IN_DOUBLE_QUOTES = 4;
    private static final int IN_SINGLE_QUOTES = 5;
    /** Inside {@code <!DOCTYPE ...>} or a {@code <?...?>} processing instruction. */
    private static final int IN_DECLARATION = 6;

    @Override
    public int tokenize(char[] text, int start, int end, int state, TokenSink sink) {
        int i = start;
        while (i < end) {
            switch (state) {
                case IN_COMMENT, IN_CDATA, IN_DECLARATION, IN_DOUBLE_QUOTES, IN_SINGLE_QUOTES -> {
                    // Continues a construct opened on an earlier line.
                    int close = indexAfter(text, i, end, terminator(state));
                    if (close < 0) {
                        emit(sink, i, end, type(state));
                        return state;
                    }
                    emit(sink, i, close, type(state));
                    i = close;
                    state = state == IN_DOUBLE_QUOTES || state == IN_SINGLE_QUOTES ? IN_TAG : TEXT;
                }
                case IN_TAG -> {
                    char c = text[i];
                    if (c == '>') {
                        emit(sink, i, i + 1, TokenType.PUNCTUATION);
                        i++;
                        state = TEXT;
                    } else if (c == '"' || c == '\'') {
                        int quoted = c == '"' ? IN_DOUBLE_QUOTES : IN_SINGLE_QUOTES;
                        int close = indexAfter(text, i + 1, end, terminator(quoted));
                        if (close < 0) {
                            emit(sink, i, end, TokenType.STRING);
                            return quoted;
                        }
                        emit(sink, i, close, TokenType.STRING);
                        i = close;
                    } else if (c == '=') {
                        emit(sink, i, i + 1, TokenType.OPERATOR);
                        i++;
                    } else if (c == '/' || c == '?') {
                        emit(sink, i, i + 1, TokenType.PUNCTUATION);
                        i++;
                    } else if (isNameChar(c)) {
                        int close = nameEnd(text, i, end);
                        emit(sink, i, close, TokenType.ATTRIBUTE);
                        i = close;
                    } else {
                        i++;
                    }
                }
                default -> {
                    char c = text[i];
                    if (c == '<') {
                        int opened = openedBy(text, i, end);
                        if (opened != IN_TAG) {
                            int close = indexAfter(text, i + 2, end, terminator(opened));
                            if (close < 0) {
                                emit(sink, i, end, type(opened));
                                return opened;
                            }
                            emit(sink, i, close, type(opened));
                            i = close;
                        } else {
                            int name = i + 1 < end && text[i + 1] == '/' ? i + 2 : i + 1;
                            emit(sink, i, name, TokenType.PUNCTUATION);
                            int close = nameEnd(text, name, end);
                            emit(sink, name, close, TokenType.TAG);
                            i = close;
                            state = IN_TAG;
                        }
                    } else if (c == '&') {
                        int close = entityEnd(text, i, end);
                        emit(sink, i, close, close > i + 1 ? TokenType.LITERAL : TokenType.TEXT);
                        i = close;
                    } else {
                        i++;
                    }
                }
            }
        }
        return state;
    }

    private static void emit(TokenSink sink, int start, int end, TokenType type) {
        if (end > start && type != TokenType.TEXT) {
            sink.token(start, end - start, type);
        }
    }

    /** The state entered by the {@code <} at the index. */
    private static int openedBy(char[] text, int at, int end) {
        if (startsWith(text, at, end, "<!--")) {
            return IN_COMMENT;
        }
        if (startsWith(text, at, end, "<![CDATA[")) {
            return IN_CDATA;
        }
        if (startsWith(text, at, end, "<!") || startsWith(text, at, end, "<?")) {
            return IN_DECLARATION;
        }
        return IN_TAG;
    }

    private static String terminator(int state) {
        return switch (state) {
            case IN_COMMENT -> "-->";
            case IN_CDATA -> "]]>";
            case IN_DOUBLE_QUOTES -> "\"";
            case IN_SINGLE_QUOTES -> "'";
            default -> ">";
        };
    }

    private static TokenType type(int state) {
        return switch (state) {
            case IN_COMMENT -> TokenType.COMMENT;
            case IN_DECLARATION -> TokenType.PREPROCESSOR;
            default -> TokenType.STRING;
        };
    }

    /** Index just past the next occurrence of the terminator, or -1. */
    private static int indexAfter(char[] text, int from, int end, String terminator) {
        for (int i = from; i < end; i++) {
            if (startsWith(text, i, end, terminator)) {
                return i + terminator.length();
            }
        }
        return -1;
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

    private static int nameEnd(char[] text, int from, int end) {
        int i = from;
        while (i < end && isNameChar(text[i])) {
            i++;
        }
        return i;
    }

    /** Index just past an entity such as {@code &amp;}, or just past the {@code &} when it is not one. */
    private static int entityEnd(char[] text, int from, int end) {
        int i = from + 1;
        while (i < end && i - from <= 10 && (Character.isLetterOrDigit(text[i]) || text[i] == '#')) {
            i++;
        }
        return i < end && text[i] == ';' && i > from + 1 ? i + 1 : from + 1;
    }

    private static boolean isNameChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == ':' || c == '.';
    }
}
