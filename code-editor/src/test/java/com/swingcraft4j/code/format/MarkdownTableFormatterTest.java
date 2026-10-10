package com.swingcraft4j.code.format;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class MarkdownTableFormatterTest {

    private final MarkdownTableFormatter formatter = new MarkdownTableFormatter();

    private String format(String text) {
        return formatter.format(text, new FormatOptions(4, false));
    }

    @Test
    void givesEachColumnTheWidthOfItsWidestCell() {
        String formatted = "| Name      | Age |\n| --------- | --- |\n| Alexander | 7   |\n| Bo        | 12  |";
        assertEquals(formatted, format("| Name | Age |\n|---|---|\n| Alexander | 7 |\n| Bo | 12 |"));
        assertEquals(formatted, format("|Name|Age|\n|-|-----------|\n|   Alexander   |7|\n|Bo|12|"));
        assertSame(formatted, format(formatted), "a table that is aligned already stays as it is");
    }

    @Test
    void keepsTheAlignmentOfTheColumnsAndPadsTheCellsToMatch() {
        assertEquals("| Left | Mid | Right |\n| :--- | :-: | ----: |\n| a    |  b  |     c |",
                format("| Left | Mid | Right |\n|:--|:-:|--:|\n| a | b | c |"));
    }

    @Test
    void measuresWideCharsAsTwoCells() {
        assertEquals("| 名前 | n   |\n| ---- | --- |\n| ab   | 😀  |", format("| 名前 | n |\n|---|---|\n| ab | 😀 |"));
    }

    @Test
    void doesNotSplitACellAtAnEscapedPipeOrOneInsideACodeSpan() {
        assertEquals("| a \\| b | `x | y` | ``p`|`q`` |\n| ------ | ------- | --------- |\n| 1      | 2       | 3         |",
                format("| a \\| b | `x | y` | ``p`|`q`` |\n|---|---|---|\n| 1 | 2 | 3 |"));
        assertEquals("| a   | \\`  | b`  |\n| --- | --- | --- |", format("|a|\\`|b`|\n|-|-|-|"),
                "a backtick that is escaped, or has no partner, opens no code span");
    }

    @Test
    void addsTheBordersAndTheCellsThatAreMissing() {
        assertEquals("| a   | b   |\n| --- | --- |\n| 1   |     |\n| 2   | 3   | 4 |", format("a | b\n--|--\n1 |\n| 2 | 3 | 4"),
                "a cell too many is kept, though no column is made for it");
    }

    @Test
    void leavesTablesInsideFencedCodeBlocksAlone() {
        String fenced = "```text\n| a | b |\n|-|-|\n```\n~~~~\n| a | b |\n|-|-|\n~~~\nstill inside\n|c|d|\n|-|-|\n~~~~\n";
        assertEquals(fenced + "| a   | b   |\n| --- | --- |", format(fenced + "| a | b |\n|-|-|"));
    }

    @Test
    void leavesTheRestOfTheTextAsItIs() {
        assertEquals("# T\r\n\r\n  | a         | b   |\r\n  | --------- | --- |\r\n  | long cell | x   |\r\n\r\nText | no table\r\n---\r\n",
                format("# T\r\n\r\n  | a | b |\r\n  |---|---|\r\n  | long cell | x |\r\n\r\nText | no table\r\n---\r\n"),
                "the indentation of a table and the ends of its lines are kept");
        for (String plain : new String[]{"", "a | b\n", "a | b\n---\n", "| a | b |\n|---|\n", "> | a |\n> |---|\n", "one\n\n  two  \n"}) {
            assertSame(plain, format(plain));
        }
        assertEquals("| a   |\n| --- |\nnot a row", format("|a|\n|-|\nnot a row"), "the table ends at a line without a pipe");
    }
}
