package com.swingcraft4j.code.lexer;

import com.swingcraft4j.code.lexer.OverlayLanguage.Overlay;
import com.swingcraft4j.code.text.CharArraySequence;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;

/**
 * Lexer for an {@link OverlayLanguage}: lets the base lexer tokenize the line, then cuts the
 * matches of the overlay patterns out of its tokens. The state between lines is the base
 * lexer's own, as a match never runs over a line break.
 */
final class OverlayLexer implements Lexer {

    private final Lexer base;
    private final Matcher[] matchers;
    private final TokenType[] matcherTypes;
    private final CharArraySequence line = new CharArraySequence();

    // the tokens of the base lexer for the line
    private int[] starts = new int[64];
    private int[] ends = new int[64];
    private TokenType[] types = new TokenType[64];
    private int count;
    private final TokenSink collector = (start, length, type) -> {
        if (count == starts.length) {
            starts = Arrays.copyOf(starts, count * 2);
            ends = Arrays.copyOf(ends, count * 2);
            types = Arrays.copyOf(types, count * 2);
        }
        starts[count] = start;
        ends[count] = start + length;
        types[count++] = type;
    };

    // the overlay matches on the line, in order and without overlaps
    private int[] overStarts = new int[16];
    private int[] overEnds = new int[16];
    private TokenType[] overTypes = new TokenType[16];
    private boolean[] overEmitted = new boolean[16];
    private int overCount;

    OverlayLexer(Lexer base, List<Overlay> overlays) {
        this.base = base;
        matchers = new Matcher[overlays.size()];
        matcherTypes = new TokenType[overlays.size()];
        for (int i = 0; i < matchers.length; i++) {
            matchers[i] = overlays.get(i).pattern().matcher(line);
            matcherTypes[i] = overlays.get(i).type();
        }
    }

    @Override
    public int tokenize(char[] text, int start, int end, int state, TokenSink sink) {
        count = 0;
        int endState = base.tokenize(text, start, end, state, collector);
        findOverlays(text, start, end);

        int over = 0;
        for (int i = 0; i < count; i++) {
            // overlays lying wholly before this token, in a gap the base lexer left
            while (over < overCount && overEnds[over] <= starts[i]) {
                emitOverlay(over++, sink);
            }
            int position = starts[i];
            while (position < ends[i] && over < overCount && overStarts[over] < ends[i]) {
                if (overStarts[over] > position) {
                    sink.token(position, overStarts[over] - position, types[i]);
                }
                emitOverlay(over, sink);
                position = Math.max(position, overEnds[over]);
                if (overEnds[over] > ends[i]) {
                    break; // it runs on into the next token
                }
                over++;
            }
            if (position < ends[i]) {
                sink.token(position, ends[i] - position, types[i]);
            }
        }
        while (over < overCount) {
            emitOverlay(over++, sink);
        }
        return endState;
    }

    private void emitOverlay(int index, TokenSink sink) {
        if (!overEmitted[index]) {
            overEmitted[index] = true;
            sink.token(overStarts[index], overEnds[index] - overStarts[index], overTypes[index]);
        }
    }

    /** Collects the matches of all patterns on the line, sorted, dropping any that overlap an earlier one. */
    private void findOverlays(char[] text, int start, int end) {
        overCount = 0;
        line.set(text, end);
        for (int m = 0; m < matchers.length; m++) {
            Matcher matcher = matchers[m];
            matcher.reset();
            matcher.region(start, end);
            while (matcher.find()) {
                if (matcher.end() > matcher.start()) {
                    insert(matcher.start(), matcher.end(), matcherTypes[m]);
                }
            }
        }
        // sorted by start with the earlier pattern first among equals; now drop the overlaps
        int kept = 0;
        for (int i = 0; i < overCount; i++) {
            if (kept == 0 || overStarts[i] >= overEnds[kept - 1]) {
                overStarts[kept] = overStarts[i];
                overEnds[kept] = overEnds[i];
                overTypes[kept] = overTypes[i];
                kept++;
            }
        }
        overCount = kept;
        Arrays.fill(overEmitted, 0, overCount, false);
    }

    /** Inserts a match keeping the list sorted by start; one starting where another does goes after it. */
    private void insert(int start, int end, TokenType type) {
        if (overCount == overStarts.length) {
            overStarts = Arrays.copyOf(overStarts, overCount * 2);
            overEnds = Arrays.copyOf(overEnds, overCount * 2);
            overTypes = Arrays.copyOf(overTypes, overCount * 2);
            overEmitted = Arrays.copyOf(overEmitted, overCount * 2);
        }
        int at = overCount;
        while (at > 0 && overStarts[at - 1] > start) {
            overStarts[at] = overStarts[at - 1];
            overEnds[at] = overEnds[at - 1];
            overTypes[at] = overTypes[at - 1];
            at--;
        }
        overStarts[at] = start;
        overEnds[at] = end;
        overTypes[at] = type;
        overCount++;
    }
}
