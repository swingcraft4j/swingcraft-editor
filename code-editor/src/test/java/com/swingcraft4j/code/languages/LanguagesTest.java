package com.swingcraft4j.code.languages;

import com.swingcraft4j.code.lexer.Language;
import com.swingcraft4j.code.lexer.Languages;
import com.swingcraft4j.code.lexer.Lexer;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LanguagesTest {

    /** Tokenizes the lines of a document in order, one list of tokens per line. */
    private static List<List<String>> lex(String languageId, String... lines) {
        Lexer lexer = Languages.byId(languageId).orElseThrow().createLexer();
        List<List<String>> result = new ArrayList<>();
        int state = Lexer.INITIAL_STATE;
        for (String line : lines) {
            List<String> tokens = new ArrayList<>();
            char[] text = line.toCharArray();
            state = lexer.tokenize(text, 0, text.length, state,
                    (start, length, type) -> tokens.add(type + ":" + new String(text, start, length)));
            result.add(tokens);
        }
        return result;
    }

    @Test
    void findsEveryLanguageByFileName() {
        assertEquals(List.of("css", "java", "javascript", "typescript", "json", "python", "sql", "xml"),
                Languages.installed().stream().map(Language::id).toList());
        assertEquals("xml", Languages.forFileName("pom.xml").orElseThrow().id());
        assertEquals("xml", Languages.forFileName("index.HTML").orElseThrow().id());
        assertEquals("typescript", Languages.forFileName("app.component.ts").orElseThrow().id());
        assertEquals("python", Languages.forFileName("setup.py").orElseThrow().id());
    }

    @Test
    void css() {
        assertEquals(List.of(
                        List.of("KEYWORD:@media", "IDENTIFIER:screen", "PUNCTUATION:{", "COMMENT:/* wide */"),
                        List.of("IDENTIFIER:a", "OPERATOR::", "IDENTIFIER:hover", "PUNCTUATION:{", "ATTRIBUTE:color",
                                "OPERATOR::", "NUMBER:#fff", "PUNCTUATION:;", "ATTRIBUTE:margin-top", "OPERATOR::",
                                "NUMBER:10px", "KEYWORD:!important", "PUNCTUATION:}"),
                        List.of("VARIABLE:--gap", "OPERATOR::", "FUNCTION:var", "PUNCTUATION:(", "VARIABLE:--size",
                                "PUNCTUATION:)", "PUNCTUATION:;", "ATTRIBUTE:font", "OPERATOR::", "STRING:'Inter'",
                                "PUNCTUATION:,", "LITERAL:inherit", "PUNCTUATION:;")),
                lex("css", "@media screen { /* wide */", "a:hover { color: #fff; margin-top: 10px !important }",
                        "--gap: var(--size); font: 'Inter', inherit;"));
    }

    @Test
    void json() {
        assertEquals(List.of(List.of("PUNCTUATION:{", "ATTRIBUTE:\"name\"", "OPERATOR::", "STRING:\"a \\\"b\\\"\"",
                        "PUNCTUATION:,", "ATTRIBUTE:\"n\"", "OPERATOR::", "PUNCTUATION:[", "NUMBER:1.5", "PUNCTUATION:,",
                        "LITERAL:true", "PUNCTUATION:,", "LITERAL:null", "PUNCTUATION:]", "PUNCTUATION:}")),
                lex("json", "{\"name\": \"a \\\"b\\\"\", \"n\" : [1.5, true, null]}"));
    }

    @Test
    void jsonReadsAVeryLongString() {
        String value = "x\\\"".repeat(6000);
        assertEquals(List.of(List.of("ATTRIBUTE:\"" + value + "\"", "OPERATOR::", "STRING:\"" + value + "\"", "PUNCTUATION:,")),
                lex("json", "\"" + value + "\": \"" + value + "\","));
    }

    @Test
    void sql() {
        assertEquals(List.of(
                        List.of("KEYWORD:SELECT", "FUNCTION:count", "PUNCTUATION:(", "OPERATOR:*", "PUNCTUATION:)",
                                "KEYWORD:FROM", "IDENTIFIER:users", "COMMENT:-- all"),
                        List.of("KEYWORD:where", "IDENTIFIER:name", "OPERATOR:=", "STRING:'it''s'", "KEYWORD:and",
                                "IDENTIFIER:id", "OPERATOR:=", "CONSTANT::id")),
                lex("sql", "SELECT count(*) FROM users -- all", "where name = 'it''s' and id = :id")
                        .stream().map(LanguagesTest::mergeAdjacentStrings).toList());
    }

    /** A doubled quote inside an SQL string reads as two adjacent strings; join them for comparison. */
    private static List<String> mergeAdjacentStrings(List<String> tokens) {
        List<String> merged = new ArrayList<>();
        for (String token : tokens) {
            int last = merged.size() - 1;
            if (token.startsWith("STRING:") && last >= 0 && merged.get(last).startsWith("STRING:")) {
                merged.set(last, merged.get(last) + token.substring("STRING:".length()));
            } else {
                merged.add(token);
            }
        }
        return merged;
    }

    @Test
    void python() {
        assertEquals(List.of(
                        List.of("ANNOTATION:@app.route", "PUNCTUATION:(", "STRING:'/'", "PUNCTUATION:)"),
                        List.of("KEYWORD:def", "FUNCTION:home", "PUNCTUATION:(", "CONSTANT:self", "PUNCTUATION:)",
                                "OPERATOR::", "COMMENT:# view"),
                        List.of("STRING:\"\"\"Doc"),
                        List.of("STRING:    string.\"\"\""),
                        List.of("KEYWORD:return", "LITERAL:None", "KEYWORD:if", "IDENTIFIER:x", "KEYWORD:else",
                                "TYPE:Response", "PUNCTUATION:(", "NUMBER:404", "PUNCTUATION:)")),
                lex("python", "@app.route('/')", "def home(self): # view", "    \"\"\"Doc", "    string.\"\"\"",
                        "    return None if x else Response(404)"));
    }

    @Test
    void javaScriptAndTypeScript() {
        assertEquals(List.of(
                        List.of("KEYWORD:const", "IDENTIFIER:$el", "OPERATOR:=", "STRING:`a ${b}"),
                        List.of("STRING:c`", "PUNCTUATION:;", "COMMENT:// done")),
                lex("javascript", "const $el = `a ${b}", "c`; // done"));
        assertEquals(List.of(List.of("KEYWORD:interface", "TYPE:User", "PUNCTUATION:{", "KEYWORD:readonly",
                        "IDENTIFIER:id", "OPERATOR::", "TYPE:number", "PUNCTUATION:}")),
                lex("typescript", "interface User { readonly id: number }"));
    }

    @Test
    void xml() {
        assertEquals(List.of(
                        List.of("PREPROCESSOR:<?xml version=\"1.0\"?>"),
                        List.of("PUNCTUATION:<", "TAG:a:item", "ATTRIBUTE:id", "OPERATOR:=", "STRING:\"1\"",
                                "ATTRIBUTE:name", "OPERATOR:=", "STRING:'two"),
                        List.of("STRING:lines'", "PUNCTUATION:/", "PUNCTUATION:>", "LITERAL:&amp;", "COMMENT:<!-- note"),
                        List.of("COMMENT:more -->", "PUNCTUATION:</", "TAG:root", "PUNCTUATION:>"),
                        List.of("STRING:<![CDATA[ <raw> ]]>")),
                lex("xml", "<?xml version=\"1.0\"?>", "<a:item id=\"1\" name='two", "lines' /> text &amp; <!-- note",
                        "more --></root>", "<![CDATA[ <raw> ]]> a & b"));
    }
}
