package com.swingcraft4j.code.lexer;

import com.swingcraft4j.code.languages.json.JsonLanguage;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
