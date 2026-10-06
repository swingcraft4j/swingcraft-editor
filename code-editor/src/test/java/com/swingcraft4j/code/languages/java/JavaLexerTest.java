package com.swingcraft4j.code.languages.java;

import com.swingcraft4j.code.lexer.Languages;
import com.swingcraft4j.code.lexer.Lexer;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JavaLexerTest {

    private final JavaLexer lexer = new JavaLexer();
    private int state = Lexer.INITIAL_STATE;

    /** Tokenizes one line, carrying the state over from the previous call. */
    private List<String> lex(String line) {
        List<String> tokens = new ArrayList<>();
        char[] text = line.toCharArray();
        state = lexer.tokenize(text, 0, text.length, state,
                (start, length, type) -> tokens.add(type + ":" + new String(text, start, length)));
        return tokens;
    }

    @Test
    void classifiesWordsInADeclaration() {
        assertEquals(List.of("KEYWORD:public", "KEYWORD:static", "KEYWORD:final", "KEYWORD:int", "CONSTANT:MAX_SIZE",
                        "OPERATOR:=", "NUMBER:10_000", "PUNCTUATION:;"),
                lex("public static final int MAX_SIZE = 10_000;"));
    }

    @Test
    void tellsTypesFunctionsAndIdentifiersApart() {
        assertEquals(List.of("TYPE:String", "IDENTIFIER:name", "OPERATOR:=", "IDENTIFIER:user", "PUNCTUATION:.",
                        "FUNCTION:getName", "PUNCTUATION:(", "PUNCTUATION:)", "PUNCTUATION:;"),
                lex("String name = user.getName();"));
    }

    @Test
    void readsStringsCharsAndNumbers() {
        assertEquals(List.of("STRING:\"a \\\" b\"", "OPERATOR:+", "STRING:'\\''", "OPERATOR:+", "NUMBER:0x1F",
                        "OPERATOR:+", "NUMBER:1.5e-3", "OPERATOR:+", "LITERAL:null"),
                lex("\"a \\\" b\" + '\\'' + 0x1F + 1.5e-3 + null"));
    }

    @Test
    void readsAnnotationsAndLineComments() {
        assertEquals(List.of("ANNOTATION:@Override", "COMMENT:// done"), lex("@Override // done"));
        assertEquals(List.of("KEYWORD:public", "KEYWORD:@interface", "TYPE:Marker", "PUNCTUATION:{", "PUNCTUATION:}"),
                lex("public @interface Marker {}"));
    }

    @Test
    void carriesBlockCommentsAcrossLines() {
        assertEquals(List.of("KEYWORD:int", "IDENTIFIER:a", "PUNCTUATION:;", "COMMENT:/* start"), lex("int a; /* start"));
        assertEquals(List.of("COMMENT:still inside"), lex("still inside"));
        assertEquals(List.of("COMMENT:end */", "KEYWORD:int", "IDENTIFIER:b", "PUNCTUATION:;"), lex("end */ int b;"));
        assertEquals(Lexer.INITIAL_STATE, state);
    }

    @Test
    void carriesDocCommentsAndTextBlocksAcrossLines() {
        assertEquals(List.of("DOC_COMMENT:/**"), lex("/**"));
        assertEquals(List.of("DOC_COMMENT: * text */"), lex(" * text */"));
        assertEquals(List.of("COMMENT:/**/", "IDENTIFIER:x"), lex("/**/ x"));

        assertEquals(List.of("TYPE:String", "IDENTIFIER:s", "OPERATOR:=", "STRING:\"\"\""), lex("String s = \"\"\""));
        assertEquals(List.of("STRING:    int notCode;"), lex("    int notCode;"));
        assertEquals(List.of("STRING:    \"\"\"", "PUNCTUATION:;"), lex("    \"\"\";"));
        assertEquals(Lexer.INITIAL_STATE, state);
    }

    @Test
    void isRegisteredAsAService() {
        assertTrue(Languages.forFileName("Main.java").isPresent());
        assertEquals("java", Languages.forFileName("Main.JAVA").orElseThrow().id());
    }
}
