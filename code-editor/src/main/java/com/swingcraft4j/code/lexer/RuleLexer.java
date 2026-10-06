package com.swingcraft4j.code.lexer;

import com.swingcraft4j.code.lexer.RuleLanguage.PatternRule;
import com.swingcraft4j.code.lexer.RuleLanguage.Region;
import com.swingcraft4j.code.text.CharArraySequence;

import java.util.List;
import java.util.regex.Matcher;

/** Lexer for a {@link RuleLanguage}. State {@code n > 0} means inside multi-line region {@code n - 1}. */
final class RuleLexer implements Lexer {

    private final RuleLanguage language;
    private final List<Region> regions;
    private final CharArraySequence line = new CharArraySequence();
    private final Matcher[] matchers;
    private final TokenType[] matcherTypes;

    RuleLexer(RuleLanguage language) {
        this.language = language;
        this.regions = language.regions;
        List<PatternRule> patterns = language.patterns;
        matchers = new Matcher[patterns.size()];
        matcherTypes = new TokenType[patterns.size()];
        for (int i = 0; i < matchers.length; i++) {
            // Transparent bounds let lookbehind and \b see the text before the position.
            matchers[i] = patterns.get(i).pattern().matcher(line).useTransparentBounds(true);
            matcherTypes[i] = patterns.get(i).type();
        }
    }

    @Override
    public int tokenize(char[] text, int start, int end, int state, TokenSink sink) {
        int i = start;
        if (state > 0) {
            Region region = regions.get(state - 1);
            int close = regionEnd(text, i, end, region);
            if (close < 0) {
                emit(sink, i, end, region.type());
                return state;
            }
            emit(sink, i, close, region.type());
            i = close;
        }
        line.set(text, end);

        positions:
        while (i < end) {
            char c = text[i];
            if (c == ' ' || c == '\t') {
                i++;
                continue;
            }
            for (int p = 0; p < matchers.length; p++) {
                Matcher matcher = matchers[p];
                matcher.region(i, end);
                if (matches(matcher) && matcher.end() > i) {
                    emit(sink, i, matcher.end(), matcherTypes[p]);
                    i = matcher.end();
                    continue positions;
                }
            }
            for (int r = 0; r < regions.size(); r++) {
                Region region = regions.get(r);
                if (!startsWith(text, i, end, region.open())) {
                    continue;
                }
                int close = region.close() == null ? end : regionEnd(text, i + region.open().length(), end, region);
                if (close < 0) {
                    emit(sink, i, end, region.type());
                    return region.multiline() ? r + 1 : INITIAL_STATE;
                }
                emit(sink, i, close, region.type());
                i = close;
                continue positions;
            }
            if (isDigit(c) || (c == '.' && i + 1 < end && isDigit(text[i + 1]))) {
                int close = numberEnd(text, i, end);
                emit(sink, i, close, TokenType.NUMBER);
                i = close;
            } else if (isWordStart(c)) {
                int close = i + 1;
                while (close < end && isWordPart(text[close])) {
                    close++;
                }
                emit(sink, i, close, classify(text, start, i, close, end));
                i = close;
            } else {
                if (language.operatorChars.indexOf(c) >= 0) {
                    emit(sink, i, i + 1, TokenType.OPERATOR);
                } else if (language.punctuationChars.indexOf(c) >= 0) {
                    emit(sink, i, i + 1, TokenType.PUNCTUATION);
                }
                i++;
            }
        }
        return INITIAL_STATE;
    }

    /**
     * Whether the pattern matches at the start of its region. An expression that overflows the
     * stack on a long line, as a choice inside a repetition does, counts as no match: thrown on,
     * it would stop the highlighting of all the lines after this one.
     */
    private static boolean matches(Matcher matcher) {
        try {
            return matcher.lookingAt();
        } catch (StackOverflowError e) {
            return false;
        }
    }

    private static void emit(TokenSink sink, int start, int end, TokenType type) {
        if (end > start) {
            sink.token(start, end - start, type);
        }
    }

    private static boolean startsWith(char[] text, int at, int end, String prefix) {
        int length = prefix.length();
        if (at + length > end) {
            return false;
        }
        for (int i = 0; i < length; i++) {
            if (text[at + i] != prefix.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    /** Index just past the closing delimiter, or -1 when the region is not closed on this line. */
    private static int regionEnd(char[] text, int from, int end, Region region) {
        for (int i = from; i < end; i++) {
            if (region.escape() != RuleLanguage.NO_ESCAPE && text[i] == region.escape()) {
                i++;
            } else if (startsWith(text, i, end, region.close())) {
                return i + region.close().length();
            }
        }
        return -1;
    }

    private static int numberEnd(char[] text, int from, int end) {
        boolean hex = from + 1 < end && text[from] == '0' && (text[from + 1] == 'x' || text[from + 1] == 'X');
        int i = from + 1;
        while (i < end) {
            char c = text[i];
            char previous = text[i - 1];
            boolean exponentSign = (c == '+' || c == '-') && !hex && (previous == 'e' || previous == 'E');
            boolean fraction = c == '.' && i + 1 < end && isDigit(text[i + 1]);
            if (!Character.isLetterOrDigit(c) && c != '_' && !exponentSign && !fraction) {
                break;
            }
            i++;
        }
        return i;
    }

    private TokenType classify(char[] text, int lineStart, int start, int end, int lineEnd) {
        // a word after a dot names a member, whatever the word: get in "headers.get" is no keyword
        boolean member = start > lineStart && text[start - 1] == '.';
        if (!member && end - start <= language.maxWordLength) {
            TokenType type = language.words.get(language.normalize(new String(text, start, end - start)));
            if (type != null) {
                return type;
            }
        }
        if (language.capitalizedTypes && Character.isUpperCase(text[start])) {
            for (int i = start + 1; i < end; i++) {
                if (Character.isLowerCase(text[i])) {
                    return TokenType.TYPE;
                }
            }
            return end - start > 1 ? TokenType.CONSTANT : TokenType.TYPE;
        }
        if (language.detectFunctions) {
            int i = end;
            while (i < lineEnd && text[i] == ' ') {
                i++;
            }
            if (i < lineEnd && text[i] == '(') {
                return TokenType.FUNCTION;
            }
        }
        return TokenType.IDENTIFIER;
    }

    private boolean isWordStart(char c) {
        return Character.isLetter(c) || c == '_' || language.extraWordChars.indexOf(c) >= 0;
    }

    private boolean isWordPart(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || language.extraWordChars.indexOf(c) >= 0;
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }
}
