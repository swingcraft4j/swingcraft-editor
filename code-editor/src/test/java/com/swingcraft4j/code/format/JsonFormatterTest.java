package com.swingcraft4j.code.format;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class JsonFormatterTest {

    private final JsonFormatter formatter = new JsonFormatter();

    private String format(String text) {
        return formatter.format(text, new FormatOptions(2, false));
    }

    @Test
    void putsEachMemberAndElementOnALineOfItsOwn() {
        String formatted = "{\n  \"name\": \"x\",\n  \"tags\": [\n    1,\n    true,\n    null\n  ],\n  \"empty\": {},\n  \"none\": []\n}";
        assertEquals(formatted, format("{\"name\":\"x\",\"tags\":[1,true,null],\"empty\":{ },\"none\":[\n]}"));
        assertEquals(formatted, format(formatted), "text that is formatted already stays as it is");
        assertEquals(formatted, format("  {  \"name\"  :\"x\"  ,\n\"tags\":\n[1\n,true,null]\n\n,\"empty\":{}\n,\"none\":[]}"),
                "however it was laid out before");
    }

    @Test
    void indentsAndEndsLinesAsTheOptionsSay() {
        assertEquals("{\r\n\t\"a\": [\r\n\t\t1\r\n\t]\r\n}\r\n",
                formatter.format("{\"a\":[1]}\r\n", new FormatOptions(4, true, "\r\n")));
        assertEquals("[\n    1\n]", formatter.format("[1]", new FormatOptions(4, false)));
        assertEquals("[\n  1\n]\n", format("\n\n[1]\n\n\n"), "a line break at the end is kept, as one");
    }

    @Test
    void leavesWhatIsInsideStringsAlone() {
        assertEquals("{\n  \"a\": \"x , : { [ // no comment \\\" y\"\n}", format("{\"a\":\"x , : { [ // no comment \\\" y\"}"));
    }

    @Test
    void keepsCommentsWhereTheyWere() {
        String commented = "// head\n{\n  \"a\": 1, // one\n  /* two */\n  \"b\": 2 /* tail */\n}";
        assertEquals(commented, format(commented));
        assertEquals("{\n  \"a\": 1, // one\n  \"b\": /* why */ 2\n}", format("{\"a\":1,// one\n\"b\":/* why */2}"));
        assertEquals("[\n  /* only\n     a comment */\n]", format("[\n/* only\n     a comment */ ]"));
    }

    @Test
    void keepsOneBlankLineBetweenMembers() {
        assertEquals("{\n  \"a\": 1,\n\n  \"b\": 2\n}", format("{\n\n  \"a\": 1,\n\n\n  \"b\": 2\n\n}"));
    }

    @Test
    void formatsWhatJson5AllowsAndSeveralDocumentsInAFile() {
        assertEquals("{\n  name: 'it\\'s',\n  n: +1.5e3,\n}", format("{name:'it\\'s',n:+1.5e3,}"));
        assertEquals("{\n  \"a\": 1\n}\n{\n  \"b\": 2\n}\n", format("{\"a\":1}\n{\"b\":2}\n"));
        assertEquals("[\n  1 2\n]", format("[1 2]"), "a missing comma is not for the formatter to mend");
    }

    @Test
    void returnsTextItCannotMakeSenseOfUnchanged() {
        for (String broken : new String[]{"{\"a\": [1, 2}", "{\"a\":1", "]", "{\"a\": \"open}", "{ /* open }", "", "  \n"}) {
            assertSame(broken, format(broken));
        }
    }
}
