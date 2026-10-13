package com.swingcraft4j.code.lexer;

import com.swingcraft4j.code.format.FormatOptions;
import com.swingcraft4j.code.format.Formatter;
import com.swingcraft4j.code.languages.json.JsonLanguage;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OverlayLanguageTest {

    private final Language json = new JsonLanguage();
    private final Language template = OverlayLanguage.over(json)
            .displayName("JSON with variables")
            .pattern(TokenType.VARIABLE, "\\{\\{[^{}]*\\}\\}")
            .pattern(TokenType.ERROR, "\\{\\{bad")
            .build();

    private static List<String> lex(Lexer lexer, int[] state, String line) {
        List<String> tokens = new ArrayList<>();
        char[] text = line.toCharArray();
        state[0] = lexer.tokenize(text, 0, text.length, state[0],
                (start, length, type) -> tokens.add(type + ":" + new String(text, start, length)));
        return tokens;
    }

    @Test
    void cutsItsTokensOutOfThoseOfTheBaseLanguage() {
        Lexer lexer = template.createLexer();
        assertEquals(List.of("ATTRIBUTE:\"url\"", "OPERATOR::", "STRING:\"", "VARIABLE:{{baseUrl}}", "STRING:/users/",
                        "VARIABLE:{{id}}", "STRING:\"", "PUNCTUATION:,"),
                lex(lexer, new int[1], "\"url\": \"{{baseUrl}}/users/{{id}}\","),
                "inside a string, which is split around them");
        assertEquals(List.of("ATTRIBUTE:\"age\"", "OPERATOR::", "VARIABLE:{{age}}", "PUNCTUATION:,"),
                lex(lexer, new int[1], "\"age\": {{age}},"),
                "and where the base language saw braces and a word");
        assertEquals(List.of("ATTRIBUTE:\"", "VARIABLE:{{key}}", "ATTRIBUTE:\"", "OPERATOR::", "LITERAL:true"),
                lex(lexer, new int[1], "\"{{key}}\": true"));
        assertEquals(List.of("PUNCTUATION:{", "PUNCTUATION:}"), lex(lexer, new int[1], "{}"), "nothing to do where nothing matches");
    }

    @Test
    void keepsTheBaseLanguagesStateAcrossLines() {
        Lexer lexer = template.createLexer();
        int[] state = new int[1];
        assertEquals(List.of("COMMENT:/* a ", "VARIABLE:{{x}}"), lex(lexer, state, "/* a {{x}}"));
        assertEquals(List.of("COMMENT:still ", "VARIABLE:{{y}}", "COMMENT: */", "LITERAL:null"), lex(lexer, state, "still {{y}} */ null"));
        assertEquals(Lexer.INITIAL_STATE, state[0]);
    }

    @Test
    void theMatchStartingFirstWins() {
        Lexer lexer = template.createLexer();
        assertEquals(List.of("VARIABLE:{{bad}}"), lex(lexer, new int[1], "{{bad}}"),
                "of two starting together, the pattern added first");
        assertEquals(List.of("ERROR:{{bad", "IDENTIFIER:x"), lex(lexer, new int[1], "{{bad x"), "the other shows where the first does not match");
    }

    @Test
    void takesAfterTheBaseLanguageUnlessToldOtherwise() {
        assertEquals("json", template.id());
        assertEquals("JSON with variables", template.displayName());
        assertEquals(json.fileExtensions(), template.fileExtensions());
        assertEquals("//", template.lineComment());
        assertEquals(json.keywords(), template.keywords());
        assertEquals("tpl", OverlayLanguage.over(json).id("tpl").extensions("tpl").build().fileExtensions().get(0));
    }

    @Test
    void formatsAsTheBaseLanguageWithItsMatchesKeptWhole() {
        FormatOptions options = new FormatOptions(2, false);
        assertEquals("{\n  \"id\": {{id}},\n  \"url\": \"{{baseUrl}}/users/{{ id }}\",\n  \"tags\": [\n    {{a}},\n    {{b}}\n  ]\n}",
                template.formatter().format("{\"id\":{{id}},\"url\":\"{{baseUrl}}/users/{{ id }}\",\"tags\":[{{a}},{{b}}]}", options));
        assertEquals("{\n  \"__overlay_0__\": {{id}}\n}", template.formatter().format("{\"__overlay_0__\":{{id}}}", options),
                "also where the text has what a match is put away as");
        assertEquals("{\n  \"a\": 1\n}", template.formatter().format("{\"a\":1}", options), "and a text without a match");

        String formatted = "{\n  \"id\": {{id}}\n}";
        assertSame(formatted, template.formatter().format(formatted, options), "a text that is formatted already stays as it is");
        String broken = "{\"id\": {{id}}";
        assertSame(broken, template.formatter().format(broken, options), "and so does one the formatter cannot make sense of");
    }

    @Test
    void hasTheFormatterOfTheBaseLanguageUnlessGivenAnother() {
        assertNull(OverlayLanguage.over(new PlainTextLanguage()).pattern(TokenType.VARIABLE, "\\{\\{[^{}]*\\}\\}").build().formatter(),
                "none where the base language has none");
        assertNull(OverlayLanguage.over(json).formatter(null).build().formatter());

        Formatter close = (text, options) -> text.replace(" ", "");
        Language closed = OverlayLanguage.over(json).pattern(TokenType.VARIABLE, "\\{\\{[^{}]*\\}\\}").formatter(close).build();
        assertEquals("{\"id\":{{ id }}}", closed.formatter().format("{\"id\": {{ id }}}", new FormatOptions(2, false)),
                "the matches are kept from that one too");
        Formatter losing = (text, options) -> text.replaceAll("\\w+", "x");
        Language lossy = OverlayLanguage.over(json).pattern(TokenType.VARIABLE, "\\{\\{[^{}]*\\}\\}").formatter(losing).build();
        String text = "{\"id\": {{id}}}";
        assertSame(text, lossy.formatter().format(text, new FormatOptions(2, false)), "a text whose matches the formatter lost stays as it was");
    }

    @Test
    void laysItsTokensOverPlainTextToo() {
        Language plain = new PlainTextLanguage();
        assertEquals(List.of(), lex(plain.createLexer(), new int[1], "port 8080, \"quoted\" and {braces}"), "nothing is highlighted in plain text");
        Language url = OverlayLanguage.over(plain).pattern(TokenType.VARIABLE, "\\{\\{[^{}]*\\}\\}").build();
        assertEquals(List.of("VARIABLE:{{host}}", "VARIABLE:{{id}}"),
                lex(url.createLexer(), new int[1], "{{host}}:8080/users/{{id}}?page=2"),
                "only what the patterns match, not the numbers a rule language would mark");
        assertEquals("text", url.id());
        assertTrue(Languages.byId("text").isEmpty(), "it is not a language to choose for a file");
    }
}
