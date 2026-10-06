package com.swingcraft4j.code.languages.markdown;

import com.swingcraft4j.code.lexer.Language;
import com.swingcraft4j.code.lexer.Languages;
import com.swingcraft4j.code.lexer.Lexer;
import com.swingcraft4j.code.lexer.TokenSink;
import com.swingcraft4j.code.lexer.TokenType;

import java.util.List;

/**
 * Lexer for Markdown. What a line is shown as:
 * <ul>
 * <li>a heading and the line under a heading: keyword</li>
 * <li>a quote, a rule, a fence and an HTML comment: comment</li>
 * <li>the marker of a list item and the box of a task: number</li>
 * <li>code, in a line or in a fence of a language that is not known: string</li>
 * <li>strong emphasis: constant; emphasis: annotation</li>
 * <li>the text of a link: attribute; where it leads, and a bare address: literal</li>
 * <li>an HTML tag: tag; the language named by a fence: type</li>
 * </ul>
 * The state of a line inside a fenced block holds the fence that opened it, the language of the
 * block and the state of the lexer of that language.
 */
final class MarkdownLexer implements Lexer {

    private static final int IN_COMMENT = 1;
    /** Set in the state of a line inside a fenced block. */
    private static final int FENCE = 1 << 28;
    private static final int INNER_MASK = 0xFFFF;
    private static final int LANGUAGE_SHIFT = 16;
    private static final int LANGUAGE_MASK = 0x7F;
    private static final int TILDE = 1 << 23;
    private static final int LENGTH_SHIFT = 24;
    private static final int LENGTH_MASK = 0xF;


    private final List<Language> languages = Languages.installed();
    /** The lexers of the languages of the fenced blocks, each made when it is first needed. */
    private final Lexer[] lexers = new Lexer[languages.size()];

    @Override
    public int tokenize(char[] text, int start, int end, int state, TokenSink sink) {
        if ((state & FENCE) != 0) {
            return fencedLine(text, start, end, state, sink);
        }
        if (state == IN_COMMENT) {
            int close = indexOf(text, start, end, "-->");
            if (close < 0) {
                emit(sink, start, end, TokenType.COMMENT);
                return IN_COMMENT;
            }
            emit(sink, start, close + 3, TokenType.COMMENT);
            return inline(text, close + 3, close + 3, end, sink);
        }
        int first = skipBlanks(text, start, end);
        if (first == end) {
            return INITIAL_STATE;
        }
        char c = text[first];
        boolean block = first - start < 4; // further in, it is the text of a list item or code
        if (block && (c == '`' || c == '~')) {
            int run = run(text, first, end, c);
            // a fence of backticks has no backtick after it: that would be code in the line
            if (run >= 3 && (c == '~' || indexOf(text, first + run, end, "`") < 0)) {
                return openFence(text, first, run, end, c, sink);
            }
        }
        if (block && c == '#') {
            int run = run(text, first, end, c);
            if (run <= 6 && (first + run == end || text[first + run] == ' ' || text[first + run] == '\t')) {
                emit(sink, first, end, TokenType.KEYWORD);
                return INITIAL_STATE;
            }
        }
        if (block && isRule(text, first, end)) {
            // === is under a heading; --- may be, or is a rule across the page
            emit(sink, first, end, c == '=' ? TokenType.KEYWORD : TokenType.COMMENT);
            return INITIAL_STATE;
        }
        if (block && c == '>') {
            emit(sink, first, end, TokenType.COMMENT);
            return INITIAL_STATE;
        }
        int content = first;
        int marker = listMarkerEnd(text, first, end);
        if (marker > first) {
            emit(sink, first, marker, TokenType.NUMBER);
            content = skipBlanks(text, marker, end);
            // the box of a task: [ ] or [x]
            if (content + 3 <= end && text[content] == '[' && text[content + 2] == ']'
                    && (text[content + 1] == ' ' || text[content + 1] == 'x' || text[content + 1] == 'X')
                    && (content + 3 == end || text[content + 3] == ' ')) {
                emit(sink, content, content + 3, TokenType.NUMBER);
                content += 3;
            }
        }
        return inline(text, content, skipBlanks(text, content, end), end, sink);
    }

    // ---- Fenced code ----

    private int openFence(char[] text, int first, int run, int end, char c, TokenSink sink) {
        emit(sink, first, first + run, TokenType.COMMENT);
        int info = skipBlanks(text, first + run, end);
        int infoEnd = info;
        while (infoEnd < end && text[infoEnd] != ' ' && text[infoEnd] != '\t' && text[infoEnd] != '{') {
            infoEnd++;
        }
        emit(sink, info, infoEnd, TokenType.TYPE);
        int language = languageOf(new String(text, info, infoEnd - info)) + 1;
        return FENCE | Math.min(run, LENGTH_MASK) << LENGTH_SHIFT | (c == '~' ? TILDE : 0) | language << LANGUAGE_SHIFT;
    }

    private int fencedLine(char[] text, int start, int end, int state, TokenSink sink) {
        char c = (state & TILDE) != 0 ? '~' : '`';
        int first = skipBlanks(text, start, end);
        if (first < end && text[first] == c) {
            int run = run(text, first, end, c);
            if (run >= (state >>> LENGTH_SHIFT & LENGTH_MASK) && skipBlanks(text, first + run, end) == end) {
                emit(sink, first, first + run, TokenType.COMMENT);
                return INITIAL_STATE;
            }
        }
        int language = (state >>> LANGUAGE_SHIFT & LANGUAGE_MASK) - 1;
        if (language < 0 || language >= lexers.length) {
            emit(sink, first, end, TokenType.STRING);
            return state;
        }
        if (lexers[language] == null) {
            lexers[language] = languages.get(language).createLexer();
        }
        int inner = lexers[language].tokenize(text, start, end, state & INNER_MASK, sink);
        return state & ~INNER_MASK | inner & INNER_MASK;
    }

    /** The index among the installed languages of the one a fence names, or -1. */
    private int languageOf(String name) {
        Language language = Languages.forName(name).orElse(null);
        // not Markdown in Markdown: the state of its lexer does not fit in the state of this one
        if (language == null || language.id().equals("markdown")) {
            return -1;
        }
        int index = languages.indexOf(language);
        return index >= LANGUAGE_MASK ? -1 : index;
    }

    // ---- Blocks ----

    /** Whether the line is nothing but three or more of one of {@code - * _}, blanks apart, or any number of {@code =}. */
    private static boolean isRule(char[] text, int first, int end) {
        char c = text[first];
        if (c != '-' && c != '*' && c != '_' && c != '=') {
            return false;
        }
        int marks = 0;
        for (int i = first; i < end; i++) {
            if (text[i] == c) {
                marks++;
            } else if (c == '=' || (text[i] != ' ' && text[i] != '\t')) {
                return false;
            }
        }
        return c == '=' || marks >= 3;
    }

    /** The index just past the marker of a list item that starts at the index, or -1: a bullet, or a number with a dot. */
    private static int listMarkerEnd(char[] text, int first, int end) {
        int i = first;
        char c = text[i];
        if (c == '-' || c == '*' || c == '+') {
            i++;
        } else {
            while (i < end && i - first < 9 && text[i] >= '0' && text[i] <= '9') {
                i++;
            }
            if (i == first || i == end || (text[i] != '.' && text[i] != ')')) {
                return -1;
            }
            i++;
        }
        return i < end && (text[i] == ' ' || text[i] == '\t') ? i : -1;
    }

    // ---- Text ----

    /**
     * Reads the text of a line from an index on.
     *
     * @param lineContent where the text of the line starts, after its indentation and its list marker
     * @return the state at the end of the line: inside an HTML comment or not
     */
    private int inline(char[] text, int from, int lineContent, int end, TokenSink sink) {
        int i = from;
        while (i < end) {
            char c = text[i];
            if (c == '\\') {
                i += 2; // the char after a backslash is itself
            } else if (c == '`') {
                int run = run(text, i, end, c);
                int close = closingRun(text, i + run, end, c, run);
                if (close >= 0) {
                    emit(sink, i, close + run, TokenType.STRING);
                    i = close + run;
                } else {
                    i += run;
                }
            } else if (c == '*' || c == '_') {
                i = emphasis(text, i, end, c, sink);
            } else if (c == '[' || (c == '!' && i + 1 < end && text[i + 1] == '[')) {
                int after = link(text, i, lineContent, end, sink);
                if (after < 0) {
                    return INITIAL_STATE; // the rest of the line was where a reference leads
                }
                i = after;
            } else if (c == '<') {
                if (startsWith(text, i, end, "<!--")) {
                    int close = indexOf(text, i + 4, end, "-->");
                    if (close < 0) {
                        emit(sink, i, end, TokenType.COMMENT);
                        return IN_COMMENT;
                    }
                    emit(sink, i, close + 3, TokenType.COMMENT);
                    i = close + 3;
                } else {
                    i = angle(text, i, end, sink);
                }
            } else if (c == 'h' && (i == from || !Character.isLetterOrDigit(text[i - 1]))
                    && (startsWith(text, i, end, "http://") || startsWith(text, i, end, "https://"))) {
                int close = i;
                while (close < end && !Character.isWhitespace(text[close]) && ")>]\"'".indexOf(text[close]) < 0) {
                    close++;
                }
                while (close > i && ".,;:!?".indexOf(text[close - 1]) >= 0) {
                    close--; // the full stop that ends the sentence is not part of the address
                }
                emit(sink, i, close, TokenType.LITERAL);
                i = close;
            } else {
                i++;
            }
        }
        return INITIAL_STATE;
    }

    /** Reads emphasis that opens at the index, if it is closed on the line. Returns where to go on. */
    private static int emphasis(char[] text, int i, int end, char c, TokenSink sink) {
        int run = run(text, i, end, c);
        // an underscore inside a word is a part of it, as in snake_case
        boolean insideWord = c == '_' && i > 0 && Character.isLetterOrDigit(text[i - 1]);
        if (insideWord || run > 3 || i + run >= end || Character.isWhitespace(text[i + run])) {
            return i + run;
        }
        for (int j = i + run; j < end; j++) {
            if (text[j] == '\\') {
                j++;
            } else if (text[j] == c) {
                int closing = run(text, j, end, c);
                boolean closes = closing >= run && !Character.isWhitespace(text[j - 1])
                        && (c != '_' || j + closing == end || !Character.isLetterOrDigit(text[j + closing]));
                if (closes) {
                    emit(sink, i, j + closing, run >= 2 ? TokenType.CONSTANT : TokenType.ANNOTATION);
                    return j + closing;
                }
                j += closing - 1;
            }
        }
        return i + run;
    }

    /**
     * Reads a link or an image that opens at the index. Returns where to go on, or -1 when the
     * rest of the line was read as where a reference leads.
     */
    private static int link(char[] text, int i, int lineContent, int end, TokenSink sink) {
        int open = text[i] == '!' ? i + 1 : i;
        int close = closing(text, open + 1, end, '[', ']');
        if (close < 0) {
            return open + 1;
        }
        int after = close + 1;
        if (after < end && text[after] == '(') {
            int paren = closing(text, after + 1, end, '(', ')');
            if (paren >= 0) {
                emit(sink, i, after, TokenType.ATTRIBUTE);
                emit(sink, after, paren + 1, TokenType.LITERAL);
                return paren + 1;
            }
        } else if (after < end && text[after] == '[') {
            int reference = closing(text, after + 1, end, '[', ']');
            if (reference >= 0) {
                emit(sink, i, reference + 1, TokenType.ATTRIBUTE);
                return reference + 1;
            }
        } else if (after < end && text[after] == ':' && i == lineContent) {
            // what a reference leads to: [id]: https://example.com
            emit(sink, i, after + 1, TokenType.ATTRIBUTE);
            int target = skipBlanks(text, after + 1, end);
            emit(sink, target, end, TokenType.LITERAL);
            return -1;
        }
        return open + 1;
    }

    /** Reads an address or an HTML tag in angle brackets that opens at the index. Returns where to go on. */
    private static int angle(char[] text, int i, int end, TokenSink sink) {
        int close = indexOf(text, i + 1, end, ">");
        if (close < 0 || close == i + 1) {
            return i + 1;
        }
        boolean blank = false;
        boolean address = false;
        for (int j = i + 1; j < close; j++) {
            blank |= text[j] == ' ';
            address |= text[j] == '@' || startsWith(text, j, close, "://");
        }
        char first = text[i + 1];
        if (address && !blank) {
            emit(sink, i, close + 1, TokenType.LITERAL);
        } else if (Character.isLetter(first) || (first == '/' && i + 2 < close && Character.isLetter(text[i + 2]))) {
            emit(sink, i, close + 1, TokenType.TAG);
        } else {
            return i + 1;
        }
        return close + 1;
    }

    // ---- Helpers ----

    private static void emit(TokenSink sink, int start, int end, TokenType type) {
        if (end > start) {
            sink.token(start, end - start, type);
        }
    }

    private static int skipBlanks(char[] text, int from, int end) {
        int i = from;
        while (i < end && (text[i] == ' ' || text[i] == '\t')) {
            i++;
        }
        return i;
    }

    /** How many of the char stand at the index, one after the other. */
    private static int run(char[] text, int from, int end, char c) {
        int i = from;
        while (i < end && text[i] == c) {
            i++;
        }
        return i - from;
    }

    /** The index of the next run of exactly that many of the char, or -1. */
    private static int closingRun(char[] text, int from, int end, char c, int length) {
        for (int i = from; i < end; i++) {
            if (text[i] == c) {
                int run = run(text, i, end, c);
                if (run == length) {
                    return i;
                }
                i += run - 1;
            }
        }
        return -1;
    }

    /** The index of the bracket that closes one opened before the index, with those opened after it closed first, or -1. */
    private static int closing(char[] text, int from, int end, char open, char close) {
        int depth = 0;
        for (int i = from; i < end; i++) {
            char c = text[i];
            if (c == '\\') {
                i++;
            } else if (c == open) {
                depth++;
            } else if (c == close) {
                if (depth == 0) {
                    return i;
                }
                depth--;
            }
        }
        return -1;
    }

    private static int indexOf(char[] text, int from, int end, String word) {
        for (int i = from; i + word.length() <= end; i++) {
            if (startsWith(text, i, end, word)) {
                return i;
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
}
