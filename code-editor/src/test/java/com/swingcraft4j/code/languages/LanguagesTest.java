package com.swingcraft4j.code.languages;

import com.swingcraft4j.code.lexer.Language;
import com.swingcraft4j.code.lexer.Languages;
import com.swingcraft4j.code.lexer.Lexer;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        assertEquals(List.of("c", "cpp", "csharp", "css", "dockerfile", "env", "go", "groovy", "html", "ini", "java", "javascript",
                        "typescript", "json", "kotlin", "markdown", "php", "properties", "python", "rust", "shell", "sql", "toml", "xml", "yaml"),
                Languages.installed().stream().map(Language::id).toList());
        assertEquals("xml", Languages.forFileName("pom.xml").orElseThrow().id());
        assertEquals("html", Languages.forFileName("index.HTML").orElseThrow().id());
        assertEquals("xml", Languages.forFileName("icon.svg").orElseThrow().id());
        assertEquals("markdown", Languages.forFileName("README.md").orElseThrow().id());
        assertEquals("typescript", Languages.forFileName("app.component.ts").orElseThrow().id());
        assertEquals("python", Languages.forFileName("setup.py").orElseThrow().id());
        assertEquals("groovy", Languages.forFileName("build.gradle").orElseThrow().id());
        assertEquals("cpp", Languages.forFileName("item.hpp").orElseThrow().id());
        assertEquals("env", Languages.forFileName(".env").orElseThrow().id());
        assertEquals("yaml", Languages.forFileName("docker-compose.yml").orElseThrow().id());
        assertEquals("shell", Languages.forFileName("deploy.sh").orElseThrow().id());
        // a name without a dot is looked up as it is
        assertEquals("dockerfile", Languages.forFileName("Dockerfile").orElseThrow().id());
        assertTrue(Languages.forFileName("notes.unknown").isEmpty());
        assertTrue(Languages.forFileName("README").isEmpty());
    }

    /** The tokens of one line, joined, which reads better than a list where a line has many. */
    private static String line(String languageId, String line) {
        return String.join(" | ", lex(languageId, line).get(0));
    }

    @Test
    void cAndCpp() {
        assertEquals("PREPROCESSOR:#include | STRING:<stdio.h>", line("c", "#include <stdio.h>"));
        assertEquals("KEYWORD:return | IDENTIFIER:a | PUNCTUATION:[ | NUMBER:0 | PUNCTUATION:] | PUNCTUATION:. | IDENTIFIER:name"
                        + " | OPERATOR:= | OPERATOR:= | LITERAL:NULL | OPERATOR:? | CONSTANT:MAX | OPERATOR:: | FUNCTION:f"
                        + " | PUNCTUATION:( | NUMBER:1 | PUNCTUATION:) | PUNCTUATION:;",
                line("c", "return a[0].name == NULL ? MAX : f(1);"));
        assertEquals("IDENTIFIER:std | OPERATOR:: | OPERATOR:: | TYPE:vector | OPERATOR:< | TYPE:T | OPERATOR:> | IDENTIFIER:v"
                        + " | OPERATOR:= | LITERAL:nullptr | PUNCTUATION:; | COMMENT:// x",
                line("cpp", "std::vector<T> v = nullptr; // x"));
    }

    @Test
    void csharpAndGoShowACapitalisedMemberAsAFunction() {
        assertEquals("IDENTIFIER:Console | PUNCTUATION:. | FUNCTION:WriteLine | PUNCTUATION:( | STRING:$\"a {b}\" | PUNCTUATION:,"
                        + " | STRING:@\"c:\\d\" | PUNCTUATION:) | PUNCTUATION:; | DOC_COMMENT:/// doc",
                line("csharp", "Console.WriteLine($\"a {b}\", @\"c:\\d\"); /// doc"));
        assertEquals("IDENTIFIER:fmt | PUNCTUATION:. | FUNCTION:Println | PUNCTUATION:( | IDENTIFIER:x | PUNCTUATION:,"
                        + " | STRING:`raw` | PUNCTUATION:, | LITERAL:nil | PUNCTUATION:)",
                line("go", "fmt.Println(x, `raw`, nil)"));
    }

    @Test
    void rustTellsACharFromALifetime() {
        assertEquals("KEYWORD:fn | IDENTIFIER:f | OPERATOR:< | ANNOTATION:'a | OPERATOR:> | PUNCTUATION:( | IDENTIFIER:s"
                        + " | OPERATOR:: | OPERATOR:& | ANNOTATION:'a | TYPE:str | PUNCTUATION:) | OPERATOR:- | OPERATOR:>"
                        + " | TYPE:char | PUNCTUATION:{ | FUNCTION:println! | PUNCTUATION:( | STRING:\"hi\" | PUNCTUATION:)"
                        + " | PUNCTUATION:; | STRING:'x' | PUNCTUATION:}",
                line("rust", "fn f<'a>(s: &'a str) -> char { println!(\"hi\"); 'x' }"));
    }

    @Test
    void phpGroovyAndKotlin() {
        assertEquals("KEYWORD:$this | OPERATOR:- | OPERATOR:> | IDENTIFIER:price | OPERATOR:= | FUNCTION:strlen | PUNCTUATION:("
                        + " | IDENTIFIER:$x | PUNCTUATION:) | PUNCTUATION:; | COMMENT:# c",
                line("php", "$this->price = strlen($x); # c"));
        assertEquals("KEYWORD:def | IDENTIFIER:items | OPERATOR:= | PUNCTUATION:[ | IDENTIFIER:bolt | OPERATOR:: | NUMBER:120"
                        + " | PUNCTUATION:] | COMMENT:// @x",
                line("groovy", "def items = [bolt: 120] // @x"));
        assertEquals("ANNOTATION:@Suppress | PUNCTUATION:( | STRING:\"x\" | PUNCTUATION:) | KEYWORD:val | IDENTIFIER:value"
                        + " | OPERATOR:= | TYPE:Foo | PUNCTUATION:( | NUMBER:1 | PUNCTUATION:)",
                line("kotlin", "@Suppress(\"x\") val value = Foo(1)"));
    }

    @Test
    void propertiesTakeAllAfterTheSeparatorAsTheValue() {
        assertEquals("ATTRIBUTE:ui.accent.color | OPERATOR:= | STRING:#0969da", line("properties", "ui.accent.color = #0969da"));
        assertEquals("ATTRIBUTE:db.user | OPERATOR:: | STRING:inventory", line("properties", "db.user: inventory"));
        assertEquals("COMMENT:# note", line("properties", "  # note"));
    }

    @Test
    void iniAndToml() {
        assertEquals("TAG:[app]", line("ini", "[app]"));
        assertEquals("ATTRIBUTE:pool size | OPERATOR:= | NUMBER:10 | COMMENT:; n", line("ini", "pool size = 10 ; n"));
        assertEquals("ATTRIBUTE:debug | OPERATOR:= | LITERAL:YES", line("ini", "debug = YES"));
        assertEquals("TAG:[[items]]", line("toml", "[[items]]"));
        // an array of values is not a table, though it stands in brackets too
        assertEquals("ATTRIBUTE:ports | OPERATOR:= | PUNCTUATION:[ | NUMBER:5432 | PUNCTUATION:, | NUMBER:5433 | PUNCTUATION:]"
                        + " | COMMENT:# p",
                line("toml", "ports = [5432, 5433] # p"));
        assertEquals("ATTRIBUTE:a.b | OPERATOR:= | STRING:'c'", line("toml", "a.b = 'c'"));
    }

    @Test
    void envStartsACommentOnlyAfterABlank() {
        assertEquals("KEYWORD:export | ATTRIBUTE:API_TOKEN | OPERATOR:= | IDENTIFIER:abc123#not-a-comment | COMMENT:# this is one",
                line("env", "export API_TOKEN=abc123#not-a-comment   # this is one"));
        assertEquals("ATTRIBUTE:URL | OPERATOR:= | VARIABLE:${HOST} | IDENTIFIER:: | VARIABLE:$PORT", line("env", "URL=${HOST}:$PORT"));
    }

    /** The tokens of the lines of a document, each line joined. */
    private static List<String> lines(String languageId, String... lines) {
        return lex(languageId, lines).stream().map(tokens -> String.join(" | ", tokens)).toList();
    }

    @Test
    void htmlReadsItsScriptsAsJavaScriptAndItsStylesAsCss() {
        assertEquals(List.of(
                        // a script within a line, which ends at its closing tag and not before
                        "PUNCTUATION:< | TAG:p | ATTRIBUTE:class | OPERATOR:= | STRING:\"a\" | PUNCTUATION:> | PUNCTUATION:</ | TAG:p"
                                + " | PUNCTUATION:> | PUNCTUATION:< | TAG:script | PUNCTUATION:> | KEYWORD:let | IDENTIFIER:a | OPERATOR:="
                                + " | STRING:\"</b>\" | PUNCTUATION:; | PUNCTUATION:</ | TAG:script | PUNCTUATION:> | PUNCTUATION:<"
                                + " | TAG:b | PUNCTUATION:>",
                        "PUNCTUATION:< | TAG:style | PUNCTUATION:> | IDENTIFIER:a | PUNCTUATION:{ | ATTRIBUTE:color | OPERATOR::"
                                + " | IDENTIFIER:red | PUNCTUATION:}",
                        // the style goes on over the lines
                        "PUNCTUATION:. | IDENTIFIER:b | OPERATOR:: | IDENTIFIER:hover | PUNCTUATION:,",
                        // the opening tag of a script may run over lines too
                        "PUNCTUATION:</ | TAG:style | PUNCTUATION:> | PUNCTUATION:< | TAG:script",
                        "ATTRIBUTE:src | OPERATOR:= | STRING:\"x.js\" | PUNCTUATION:>",
                        "KEYWORD:var | IDENTIFIER:x | OPERATOR:= | NUMBER:1 | PUNCTUATION:; | COMMENT:/* c",
                        // a comment of the script carried over, and the markup after the script
                        "COMMENT:*/ | FUNCTION:f | PUNCTUATION:( | PUNCTUATION:) | PUNCTUATION:; | PUNCTUATION:</ | TAG:SCRIPT"
                                + " | PUNCTUATION:> | LITERAL:&amp;",
                        // a script closed in its own tag has nothing inside
                        "PUNCTUATION:< | TAG:script | PUNCTUATION:/ | PUNCTUATION:> | PUNCTUATION:< | TAG:i | PUNCTUATION:>"),
                lines("html", "<p class=\"a\">x</p><script>let a = \"</b>\";</script><b>", "<style>a { color: red }", ".b:hover,",
                        "</style><script", " src=\"x.js\">", "var x = 1; /* c", "*/ f();</SCRIPT> y &amp; z", "<script/><i>"));
    }

    @Test
    void markdown() {
        assertEquals(List.of(
                        "KEYWORD:## Title",
                        "NUMBER:- | NUMBER:[x] | CONSTANT:**bold** | ANNOTATION:_it_ | STRING:`code` | ATTRIBUTE:[a] | LITERAL:(b)",
                        // the code of a fence is read in the language it names, with the state of that language
                        "COMMENT:``` | TYPE:js",
                        "KEYWORD:const | IDENTIFIER:a | OPERATOR:= | STRING:`x",
                        "STRING:${b}` | PUNCTUATION:;",
                        "COMMENT:```",
                        "NUMBER:1. | TAG:<b> | TAG:</b> | LITERAL:<https://a.b> | LITERAL:http://c.d",
                        "COMMENT:<!-- a",
                        "COMMENT:b --> | ANNOTATION:*i*",
                        "KEYWORD:===",
                        "ATTRIBUTE:[id]: | LITERAL:http://x",
                        // a fence is closed only by one like it: the backticks are text here
                        "COMMENT:~~~~",
                        "STRING:```",
                        "COMMENT:~~~~",
                        ""),
                lines("markdown", "## Title", "- [x] **bold** and _it_ `code` [a](b) snake_case", "```js", "const a = `x", "${b}`;",
                        "```", "1. <b>x</b> <https://a.b> http://c.d. \\*no\\*", "<!-- a", "b --> *i*", "===", "[id]: http://x",
                        "~~~~", "```", "~~~~", "    # not a heading"));
    }

    @Test
    void yaml() {
        // yes and no are values only on their own, not as words of a text
        assertEquals("ATTRIBUTE:name | OPERATOR:: | IDENTIFIER:Turn | IDENTIFIER:on | IDENTIFIER:the | IDENTIFIER:light | COMMENT:# no",
                line("yaml", "name: Turn on the light # no"));
        assertEquals("OPERATOR:- | ATTRIBUTE:enabled | OPERATOR:: | LITERAL:Yes", line("yaml", "  - enabled: Yes"));
        assertEquals("ATTRIBUTE:list | OPERATOR:: | PUNCTUATION:[ | LITERAL:true | PUNCTUATION:, | IDENTIFIER:a | PUNCTUATION:,"
                        + " | LITERAL:~ | PUNCTUATION:]",
                line("yaml", "list: [true, a, ~]"));
        // a colon and a # inside a value are a part of it
        assertEquals("ATTRIBUTE:base | OPERATOR:: | ANNOTATION:&base | PUNCTUATION:{ | ATTRIBUTE:url | OPERATOR::"
                        + " | IDENTIFIER:http://x/#top | PUNCTUATION:, | ATTRIBUTE:\"a b\" | OPERATOR:: | STRING:'c' | PUNCTUATION:}",
                line("yaml", "base: &base {url: http://x/#top, \"a b\": 'c'}"));
        assertEquals("ATTRIBUTE:<< | OPERATOR:: | ANNOTATION:*base", line("yaml", "<<: *base"));
        assertEquals("PREPROCESSOR:---", line("yaml", "---"));
        assertEquals("ATTRIBUTE:date | OPERATOR:: | TYPE:!!timestamp | NUMBER:2024-03-01", line("yaml", "date: !!timestamp 2024-03-01"));
    }

    @Test
    void shell() {
        assertEquals("PREPROCESSOR:#!/bin/sh", line("shell", "#!/bin/sh"));
        assertEquals("ATTRIBUTE:NAME | OPERATOR:= | VARIABLE:${1:-x} | COMMENT:# set", line("shell", "NAME=${1:-x} # set"));
        // $# is a variable and a#b a word: neither starts a comment
        assertEquals("KEYWORD:if | PUNCTUATION:[ | ATTRIBUTE:-f | STRING:\"$f\" | PUNCTUATION:] | OPERATOR:; | KEYWORD:then"
                        + " | FUNCTION:echo | VARIABLE:$# | IDENTIFIER:a#b | OPERATOR:; | KEYWORD:fi",
                line("shell", "if [ -f \"$f\" ]; then echo $# a#b; fi"));
        assertEquals("IDENTIFIER:-sources.jar | PUNCTUATION:) | IDENTIFIER:cp | ATTRIBUTE:-r | IDENTIFIER:./a | STRING:'b'"
                        + " | OPERATOR:; | OPERATOR:;",
                line("shell", "*-sources.jar) cp -r ./a 'b' ;;"));
        assertEquals("FUNCTION:log | PUNCTUATION:( | PUNCTUATION:) | PUNCTUATION:{ | FUNCTION:printf | STRING:'%s' | STRING:\"$@\""
                        + " | OPERATOR:; | PUNCTUATION:}",
                line("shell", "log() { printf '%s' \"$@\"; }"));
    }

    @Test
    void dockerfileKnowsAnInstructionOnlyAsTheFirstWordOfALine() {
        assertEquals("KEYWORD:RUN | IDENTIFIER:apk | IDENTIFIER:add | ATTRIBUTE:--no-cache | IDENTIFIER:curl",
                line("dockerfile", "RUN apk add --no-cache curl"));
        assertEquals("KEYWORD:COPY | ATTRIBUTE:--from | OPERATOR:= | IDENTIFIER:build | IDENTIFIER:/app.jar | VARIABLE:$APP_HOME"
                        + " | IDENTIFIER:/app.jar",
                line("dockerfile", "COPY --from=build /app.jar $APP_HOME/app.jar"));
        assertEquals("KEYWORD:FROM | IDENTIFIER:eclipse-temurin:21-jdk | KEYWORD:AS | IDENTIFIER:build",
                line("dockerfile", "FROM eclipse-temurin:21-jdk AS build"));
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
