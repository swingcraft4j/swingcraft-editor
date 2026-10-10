package com.swingcraft4j.code.format;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Pretty-prints JSON: each member and each element on a line of its own, indented by the depth
 * of the brackets around it. Comments are kept, as they are found in JSONC and JSON5, and so is
 * one blank line where there were any between two members.
 * <p>
 * It works on the tokens alone and does not check the grammar, so text with a comma missing is
 * still laid out. Text whose brackets do not match, or with a string or a comment that is not
 * closed, is returned unchanged.
 */
public final class JsonFormatter implements Formatter {

    private enum Kind {
        OPEN, CLOSE, COMMA, COLON, VALUE, LINE_COMMENT, BLOCK_COMMENT
    }

    /** @param breaksBefore the line breaks in the white space between the token before and this one */
    private record Token(Kind kind, String text, int breaksBefore) {

        boolean isComment() {
            return kind == Kind.LINE_COMMENT || kind == Kind.BLOCK_COMMENT;
        }
    }

    private enum Gap {
        NONE, SPACE, LINE
    }

    @Override
    public String format(String text, FormatOptions options) {
        List<Token> tokens = scan(text);
        if (tokens == null || tokens.isEmpty()) {
            return text;
        }
        StringBuilder out = new StringBuilder(text.length() + text.length() / 2);
        int depth = 0;
        Token previous = null;
        Token previousCode = null; // the last token that is not a comment
        for (Token token : tokens) {
            if (token.kind() == Kind.CLOSE) {
                depth--;
            }
            Gap gap = gap(previous, previousCode, token, depth);
            if (gap == Gap.SPACE) {
                out.append(' ');
            } else if (gap == Gap.LINE) {
                out.append(options.lineSeparator());
                if (token.breaksBefore() > 1 && previous.kind() != Kind.OPEN && token.kind() != Kind.CLOSE) {
                    out.append(options.lineSeparator());
                }
                out.append(options.indent(depth));
            }
            out.append(token.text());
            if (token.kind() == Kind.OPEN) {
                depth++;
            }
            previous = token;
            if (!token.isComment()) {
                previousCode = token;
            }
        }
        char last = text.charAt(text.length() - 1);
        if (last == '\n' || last == '\r') {
            out.append(options.lineSeparator());
        }
        return out.toString();
    }

    /** What goes between two tokens. */
    private static Gap gap(Token previous, Token previousCode, Token token, int depth) {
        if (previous == null) {
            return Gap.NONE;
        }
        if (previous.kind() == Kind.LINE_COMMENT) {
            return Gap.LINE;
        }
        if (token.isComment()) {
            // a comment stays at the end of the line it was written on
            return token.breaksBefore() == 0 ? Gap.SPACE : Gap.LINE;
        }
        if (previous.kind() == Kind.BLOCK_COMMENT) {
            if (token.breaksBefore() > 0) {
                return Gap.LINE;
            }
            // a comment within a line is laid out as if it were not there, but for a space after it
            Gap without = previousCode == null ? Gap.LINE : codeGap(previousCode, token, depth);
            return without == Gap.NONE ? Gap.SPACE : without;
        }
        if (previous.kind() == Kind.OPEN && token.kind() == Kind.CLOSE) {
            return Gap.NONE; // an empty object or array stays closed up
        }
        return codeGap(previous, token, depth);
    }

    private static Gap codeGap(Token previous, Token token, int depth) {
        switch (token.kind()) {
            case CLOSE:
                return Gap.LINE;
            case COMMA:
            case COLON:
                return Gap.NONE;
            default:
        }
        switch (previous.kind()) {
            case OPEN:
            case COMMA:
                return Gap.LINE;
            case COLON:
                return Gap.SPACE;
            default:
                // two values with nothing between them: a comma is missing, or outside all
                // brackets they are the documents of a file with one on each line
                return depth == 0 ? Gap.LINE : Gap.SPACE;
        }
    }

    /** The tokens of a text, or null where a string, a comment or a bracket is not closed. */
    private static List<Token> scan(String text) {
        List<Token> tokens = new ArrayList<>();
        Deque<Character> open = new ArrayDeque<>();
        int length = text.length();
        int breaks = 0;
        int i = 0;
        while (i < length) {
            char c = text.charAt(i);
            if (Character.isWhitespace(c)) {
                if (c == '\n' || (c == '\r' && (i + 1 == length || text.charAt(i + 1) != '\n'))) {
                    breaks++;
                }
                i++;
                continue;
            }
            int start = i;
            Kind kind;
            if (c == '{' || c == '[') {
                open.push(c == '{' ? '}' : ']');
                kind = Kind.OPEN;
                i++;
            } else if (c == '}' || c == ']') {
                if (open.isEmpty() || open.pop() != c) {
                    return null;
                }
                kind = Kind.CLOSE;
                i++;
            } else if (c == ',' || c == ':') {
                kind = c == ',' ? Kind.COMMA : Kind.COLON;
                i++;
            } else if (c == '"' || c == '\'') {
                i = stringEnd(text, i);
                if (i < 0) {
                    return null;
                }
                kind = Kind.VALUE;
            } else if (text.startsWith("//", i)) {
                while (i < length && text.charAt(i) != '\n' && text.charAt(i) != '\r') {
                    i++;
                }
                tokens.add(new Token(Kind.LINE_COMMENT, text.substring(start, i).stripTrailing(), breaks));
                breaks = 0;
                continue;
            } else if (text.startsWith("/*", i)) {
                int close = text.indexOf("*/", i + 2);
                if (close < 0) {
                    return null;
                }
                i = close + 2;
                kind = Kind.BLOCK_COMMENT;
            } else {
                // a number, a word such as true, or the bare name of a member in JSON5
                while (i < length && !endsWord(text, i)) {
                    i++;
                }
                kind = Kind.VALUE;
            }
            tokens.add(new Token(kind, text.substring(start, i), breaks));
            breaks = 0;
        }
        return open.isEmpty() ? tokens : null;
    }

    /** The index just past the string that starts at {@code start}, or -1 if its line ends first. */
    private static int stringEnd(String text, int start) {
        char quote = text.charAt(start);
        for (int i = start + 1; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == quote) {
                return i + 1;
            }
            if (c == '\n' || c == '\r') {
                return -1;
            }
            if (c == '\\') {
                i++;
            }
        }
        return -1;
    }

    private static boolean endsWord(String text, int i) {
        char c = text.charAt(i);
        return Character.isWhitespace(c) || "{}[],:\"'".indexOf(c) >= 0 || text.startsWith("//", i)
                || text.startsWith("/*", i);
    }
}
