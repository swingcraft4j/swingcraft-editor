package com.swingcraft4j.code.languages.java;

import com.swingcraft4j.code.lexer.Lexer;
import com.swingcraft4j.code.lexer.TokenSink;
import com.swingcraft4j.code.lexer.TokenType;

import java.util.Set;

/**
 * Lexer for Java source. Identifiers are classified by convention rather than by resolving
 * them: a capitalised name is a type, an all-caps name a constant, a name before {@code (}
 * a function.
 */
public final class JavaLexer implements Lexer {

    private static final int NORMAL = INITIAL_STATE;
    private static final int IN_BLOCK_COMMENT = 1;
    private static final int IN_DOC_COMMENT = 2;
    private static final int IN_TEXT_BLOCK = 3;

    private static final int MAX_KEYWORD_LENGTH = 12;

    private static final Set<String> KEYWORDS = Set.of(
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const",
            "continue", "default", "do", "double", "else", "enum", "extends", "final", "finally", "float",
            "for", "goto", "if", "implements", "import", "instanceof", "int", "interface", "long", "native",
            "new", "package", "private", "protected", "public", "return", "short", "static", "strictfp",
            "super", "switch", "synchronized", "this", "throw", "throws", "transient", "try", "void",
            "volatile", "while", "var", "record", "sealed", "permits", "yield");

    private static final Set<String> LITERALS = Set.of("true", "false", "null");

    /** The keywords and literals of the language. */
    static Set<String> reservedWords() {
        Set<String> words = new java.util.TreeSet<>(KEYWORDS);
        words.addAll(LITERALS);
        return words;
    }

    @Override
    public int tokenize(char[] text, int start, int end, int state, TokenSink sink) {
        int i = start;
        if (state == IN_BLOCK_COMMENT || state == IN_DOC_COMMENT) {
            TokenType type = state == IN_DOC_COMMENT ? TokenType.DOC_COMMENT : TokenType.COMMENT;
            int close = commentEnd(text, i, end);
            if (close < 0) {
                emit(sink, i, end, type);
                return state;
            }
            emit(sink, i, close, type);
            i = close;
        } else if (state == IN_TEXT_BLOCK) {
            int close = textBlockEnd(text, i, end);
            if (close < 0) {
                emit(sink, i, end, TokenType.STRING);
                return state;
            }
            emit(sink, i, close, TokenType.STRING);
            i = close;
        }

        while (i < end) {
            char c = text[i];
            char next = i + 1 < end ? text[i + 1] : '\0';
            if (c == ' ' || c == '\t') {
                i++;
            } else if (c == '/' && next == '/') {
                emit(sink, i, end, TokenType.COMMENT);
                return NORMAL;
            } else if (c == '/' && next == '*') {
                boolean doc = i + 2 < end && text[i + 2] == '*' && !(i + 3 < end && text[i + 3] == '/');
                TokenType type = doc ? TokenType.DOC_COMMENT : TokenType.COMMENT;
                int close = commentEnd(text, i + 2, end);
                if (close < 0) {
                    emit(sink, i, end, type);
                    return doc ? IN_DOC_COMMENT : IN_BLOCK_COMMENT;
                }
                emit(sink, i, close, type);
                i = close;
            } else if (c == '"') {
                if (next == '"' && i + 2 < end && text[i + 2] == '"') {
                    int close = textBlockEnd(text, i + 3, end);
                    if (close < 0) {
                        emit(sink, i, end, TokenType.STRING);
                        return IN_TEXT_BLOCK;
                    }
                    emit(sink, i, close, TokenType.STRING);
                    i = close;
                } else {
                    int close = quotedEnd(text, i + 1, end, '"');
                    emit(sink, i, close, TokenType.STRING);
                    i = close;
                }
            } else if (c == '\'') {
                int close = quotedEnd(text, i + 1, end, '\'');
                emit(sink, i, close, TokenType.STRING);
                i = close;
            } else if (isDigit(c) || (c == '.' && isDigit(next))) {
                int close = numberEnd(text, i, end);
                emit(sink, i, close, TokenType.NUMBER);
                i = close;
            } else if (Character.isJavaIdentifierStart(c)) {
                int close = identifierEnd(text, i + 1, end);
                emit(sink, i, close, classify(text, i, close, end));
                i = close;
            } else if (c == '@' && Character.isJavaIdentifierStart(next)) {
                int close = identifierEnd(text, i + 2, end);
                boolean declaration = close - i == 10 && new String(text, i, 10).equals("@interface");
                emit(sink, i, close, declaration ? TokenType.KEYWORD : TokenType.ANNOTATION);
                i = close;
            } else if ("+-*/%=<>!&|^~?:".indexOf(c) >= 0) {
                emit(sink, i, i + 1, TokenType.OPERATOR);
                i++;
            } else if ("(){}[];,.".indexOf(c) >= 0) {
                emit(sink, i, i + 1, TokenType.PUNCTUATION);
                i++;
            } else {
                i++;
            }
        }
        return NORMAL;
    }

    private static void emit(TokenSink sink, int start, int end, TokenType type) {
        if (end > start) {
            sink.token(start, end - start, type);
        }
    }

    /** Index just past the next {@code *}{@code /}, or -1 when the comment stays open. */
    private static int commentEnd(char[] text, int from, int end) {
        for (int i = from; i + 1 < end; i++) {
            if (text[i] == '*' && text[i + 1] == '/') {
                return i + 2;
            }
        }
        return -1;
    }

    /** Index just past the closing triple quote, or -1 when the text block stays open. */
    private static int textBlockEnd(char[] text, int from, int end) {
        for (int i = from; i < end; i++) {
            if (text[i] == '\\') {
                i++;
            } else if (text[i] == '"' && i + 2 < end && text[i + 1] == '"' && text[i + 2] == '"') {
                return i + 3;
            }
        }
        return -1;
    }

    /** Index just past the closing quote; an unterminated literal ends with the line. */
    private static int quotedEnd(char[] text, int from, int end, char quote) {
        for (int i = from; i < end; i++) {
            if (text[i] == '\\') {
                i++;
            } else if (text[i] == quote) {
                return i + 1;
            }
        }
        return end;
    }

    private static int numberEnd(char[] text, int from, int end) {
        boolean hex = from + 1 < end && text[from] == '0' && (text[from + 1] == 'x' || text[from + 1] == 'X');
        int i = from + 1;
        while (i < end) {
            char c = text[i];
            char previous = text[i - 1];
            boolean exponentSign = (c == '+' || c == '-')
                    && (previous == 'p' || previous == 'P' || (!hex && (previous == 'e' || previous == 'E')));
            boolean fraction = c == '.' && i + 1 < end && isDigit(text[i + 1]);
            if (!Character.isLetterOrDigit(c) && c != '_' && !exponentSign && !fraction) {
                break;
            }
            i++;
        }
        return i;
    }

    private static int identifierEnd(char[] text, int from, int end) {
        int i = from;
        while (i < end && Character.isJavaIdentifierPart(text[i])) {
            i++;
        }
        return i;
    }

    private static TokenType classify(char[] text, int start, int end, int lineEnd) {
        if (end - start <= MAX_KEYWORD_LENGTH && isLowerCaseWord(text, start, end)) {
            String word = new String(text, start, end - start);
            if (KEYWORDS.contains(word)) {
                return TokenType.KEYWORD;
            }
            if (LITERALS.contains(word)) {
                return TokenType.LITERAL;
            }
        }
        if (Character.isUpperCase(text[start])) {
            return end - start > 1 && isUpperCaseWord(text, start, end) ? TokenType.CONSTANT : TokenType.TYPE;
        }
        int i = end;
        while (i < lineEnd && text[i] == ' ') {
            i++;
        }
        return i < lineEnd && text[i] == '(' ? TokenType.FUNCTION : TokenType.IDENTIFIER;
    }

    private static boolean isLowerCaseWord(char[] text, int start, int end) {
        for (int i = start; i < end; i++) {
            if (text[i] < 'a' || text[i] > 'z') {
                return false;
            }
        }
        return true;
    }

    private static boolean isUpperCaseWord(char[] text, int start, int end) {
        for (int i = start; i < end; i++) {
            if (Character.isLowerCase(text[i])) {
                return false;
            }
        }
        return true;
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }
}
