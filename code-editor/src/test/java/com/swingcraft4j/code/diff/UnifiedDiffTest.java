package com.swingcraft4j.code.diff;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UnifiedDiffTest {

    @Test
    void writesWhatGitDiffPrints() {
        String original = "one\ntwo\nthree\nfour\nfive\nsix\nseven\neight\nnine\nten\n";
        String revised = "one\n2\nthree\nfour\nfive\nsix\nseven\neight\nnine\nten\neleven\n";
        assertEquals("""
                --- a/n.txt
                +++ b/n.txt
                @@ -1,5 +1,5 @@
                 one
                -two
                +2
                 three
                 four
                 five
                @@ -8,3 +8,4 @@
                 eight
                 nine
                 ten
                +eleven
                """, UnifiedDiff.of(original, revised).names("a/n.txt", "b/n.txt").format());
    }

    @Test
    void joinsChangesWhoseContextMeets() {
        assertEquals("""
                --- a
                +++ b
                @@ -1,4 +1,4 @@
                -1
                +x
                 2
                 3
                -4
                +y
                """, UnifiedDiff.of("1\n2\n3\n4\n", "x\n2\n3\ny\n").context(1).format());
    }

    @Test
    void writesAnEmptyRangeAsTheLineBefore() {
        assertEquals("""
                --- a
                +++ b
                @@ -0,0 +1,2 @@
                +a
                +b
                """, UnifiedDiff.of("", "a\nb\n").format());
        assertEquals("""
                --- a
                +++ b
                @@ -1,2 +1 @@
                 a
                -b
                """, UnifiedDiff.of("a\nb\n", "a\n").format());
    }

    @Test
    void marksALastLineWithoutALineBreak() {
        assertEquals("""
                --- a
                +++ b
                @@ -1 +1 @@
                -a
                \\ No newline at end of file
                +a
                """, UnifiedDiff.of("a", "a\n").format());
    }

    @Test
    void equalTextsGiveNothing() {
        assertEquals("", UnifiedDiff.of("a\nb", "a\nb").format());
    }
}
