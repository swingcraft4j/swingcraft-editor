package com.swingcraft4j.code.lexer;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RuleLanguageTest {

    private final Lexer lexer = RuleLanguage.builder("toy", "Toy")
            .keywords("let", "print")
            .literals("yes", "no")
            .lineComment("#")
            .blockComment("{-", "-}")
            .string("\"")
            .multilineString("\"\"\"", "\"\"\"")
            .pattern(TokenType.ANNOTATION, "@\\w+")
            .detectFunctions()
            .capitalizedTypes()
            .build()
            .createLexer();
    private int state = Lexer.INITIAL_STATE;

    private List<String> lex(String line) {
        List<String> tokens = new ArrayList<>();
        char[] text = line.toCharArray();
        state = lexer.tokenize(text, 0, text.length, state,
                (start, length, type) -> tokens.add(type + ":" + new String(text, start, length)));
        return tokens;
    }

    @Test
    void appliesWordListsNumbersAndOperators() {
        assertEquals(List.of("KEYWORD:let", "IDENTIFIER:x", "OPERATOR:=", "NUMBER:1.5e-3", "OPERATOR:+", "NUMBER:0xFF",
                        "PUNCTUATION:;", "LITERAL:yes"),
                lex("let x = 1.5e-3 + 0xFF; yes"));
    }

    @Test
    void classifiesOtherWordsByShape() {
        assertEquals(List.of("FUNCTION:area", "PUNCTUATION:(", "TYPE:Shape", "PUNCTUATION:,", "CONSTANT:MAX",
                        "PUNCTUATION:)", "ANNOTATION:@pure"),
                lex("area(Shape, MAX) @pure"));
    }

    @Test
    void readsAWordAfterADotAsAMember() {
        assertEquals(List.of("IDENTIFIER:a", "PUNCTUATION:.", "FUNCTION:print", "PUNCTUATION:(", "PUNCTUATION:)",
                        "PUNCTUATION:.", "IDENTIFIER:let", "PUNCTUATION:.", "IDENTIFIER:yes", "PUNCTUATION:.", "TYPE:Shape"),
                lex("a.print().let.yes.Shape"));
        // a dot at the start of a line continues the line before
        assertEquals(List.of("PUNCTUATION:.", "IDENTIFIER:let", "KEYWORD:let"), lex(".let let"));
    }

    @Test
    void extendsALanguageWithMoreWords() {
        RuleLanguage toy = RuleLanguage.builder("toy", "Toy").keywords("let").lineComment("#").detectFunctions().build();
        RuleLanguage more = toy.toBuilder().id("toy2").keywords("app").words(TokenType.ATTRIBUTE, "request").build();
        assertEquals("toy2", more.id());
        assertEquals("Toy", more.displayName());
        assertEquals("#", more.lineComment());
        List<String> tokens = new ArrayList<>();
        char[] text = "let app.request = f() # x".toCharArray();
        more.createLexer().tokenize(text, 0, text.length, Lexer.INITIAL_STATE,
                (start, length, type) -> tokens.add(type + ":" + new String(text, start, length)));
        assertEquals(List.of("KEYWORD:let", "KEYWORD:app", "PUNCTUATION:.", "IDENTIFIER:request", "OPERATOR:=",
                "FUNCTION:f", "PUNCTUATION:(", "PUNCTUATION:)", "COMMENT:# x"), tokens);
        assertEquals("toy", toy.id(), "the language it started from is as it was");
        assertEquals(1, toy.keywords().size());
    }

    @Test
    void goesOnAfterAPatternThatOverflowsTheStack() {
        Lexer choice = RuleLanguage.builder("toy", "Toy").pattern(TokenType.TAG, "<(?:a|b)*>").keywords("let").build().createLexer();
        List<String> tokens = new ArrayList<>();
        char[] text = ("<" + "ab".repeat(20_000) + "> let").toCharArray();
        choice.tokenize(text, 0, text.length, Lexer.INITIAL_STATE,
                (start, length, type) -> tokens.add(type + ":" + (length > 10 ? "..." : new String(text, start, length))));
        assertEquals("KEYWORD:let", tokens.get(tokens.size() - 1));
    }

    @Test
    void readsCommentsAndStrings() {
        assertEquals(List.of("KEYWORD:print", "STRING:\"a \\\" # b\"", "COMMENT:# rest \"x\""),
                lex("print \"a \\\" # b\" # rest \"x\""));
        // a string left open ends with its line
        assertEquals(List.of("STRING:\"open"), lex("\"open"));
        assertEquals(List.of("KEYWORD:let"), lex("let"));
    }

    @Test
    void carriesMultilineRegionsAcrossLines() {
        assertEquals(List.of("KEYWORD:let", "COMMENT:{- one"), lex("let {- one"));
        assertEquals(List.of("COMMENT:two"), lex("two"));
        assertEquals(List.of("COMMENT:three -}", "KEYWORD:let"), lex("three -} let"));

        // the longer delimiter wins over the quote it starts with
        assertEquals(List.of("STRING:\"\"\"text"), lex("\"\"\"text"));
        assertEquals(List.of("STRING:let \"\"\"", "KEYWORD:let"), lex("let \"\"\" let"));
        assertEquals(Lexer.INITIAL_STATE, state);
    }

    @Test
    void canIgnoreCase() {
        Lexer sql = RuleLanguage.builder("mini-sql", "Mini SQL").ignoreCase().keywords("select", "from").build().createLexer();
        List<String> tokens = new ArrayList<>();
        char[] text = "SELECT a From b".toCharArray();
        sql.tokenize(text, 0, text.length, Lexer.INITIAL_STATE,
                (start, length, type) -> tokens.add(type + ":" + new String(text, start, length)));
        assertEquals(List.of("KEYWORD:SELECT", "IDENTIFIER:a", "KEYWORD:From", "IDENTIFIER:b"), tokens);
    }
}
