# Languages

A language tells the viewer and the editor how to highlight a text, which words are its keywords and how
its comments are written.

- [The languages that come with the library](#the-languages-that-come-with-the-library)
- [A language from rules](#a-language-from-rules)
- [More words for a language](#more-words-for-a-language)
- [Syntax laid over a language](#syntax-laid-over-a-language)
- [A lexer of your own](#a-lexer-of-your-own)
- [Register a language](#register-a-language)
- [Token types](#token-types)

## The languages that come with the library

| Id           | Language   | File extensions                          |
|--------------|------------|------------------------------------------|
| `c`          | C          | `c`, `h`                                 |
| `cpp`        | C++        | `cpp`, `cc`, `cxx`, `hpp`, `hh`, `hxx`   |
| `csharp`     | C#         | `cs`                                     |
| `css`        | CSS        | `css`                                    |
| `dockerfile` | Dockerfile | `dockerfile`, and the name `Dockerfile`  |
| `env`        | Env        | `env`, as in `.env`                      |
| `go`         | Go         | `go`                                     |
| `groovy`     | Groovy     | `groovy`, `gradle`, `gvy`                |
| `html`       | HTML       | `html`, `htm`, `xhtml`                   |
| `ini`        | INI        | `ini`, `cfg`                             |
| `java`       | Java       | `java`                                   |
| `javascript` | JavaScript | `js`, `mjs`, `cjs`, `jsx`                |
| `typescript` | TypeScript | `ts`, `tsx`, `mts`, `cts`                |
| `json`       | JSON       | `json`, `jsonc`, `json5`                 |
| `kotlin`     | Kotlin     | `kt`, `kts`                              |
| `markdown`   | Markdown   | `md`, `markdown`                         |
| `php`        | PHP        | `php`, `phtml`                           |
| `properties` | Properties | `properties`                             |
| `python`     | Python     | `py`, `pyw`, `pyi`                       |
| `rust`       | Rust       | `rs`                                     |
| `shell`      | Shell      | `sh`, `bash`, `zsh`, `ksh`               |
| `sql`        | SQL        | `sql`, `ddl`, `dml`                      |
| `toml`       | TOML       | `toml`                                   |
| `xml`        | XML        | `xml`, `xsd`, `xsl`, `xslt`, `svg`, `fxml` |
| `yaml`       | YAML       | `yaml`, `yml`                            |

HTML reads what is inside its `script` elements as JavaScript and what is inside its `style` elements as CSS.
Markdown reads the code of a fenced block in the language the fence names, where that is one of these.

`Languages.forFileName` goes by the extension. A name without a dot is looked up as it is, so `Dockerfile`
finds its language.

```java
Optional<Language> java = Languages.byId("java");
Optional<Language> byFile = Languages.forFileName("query.sql");
List<Language> all = Languages.installed();

viewer.setLanguage(java.orElse(null)); // null shows the text plain
```

## A language from rules

Most languages need no lexer written by hand. `RuleLanguage` is described by word lists, the delimiters of
comments and strings, and regular expressions.

```java
Language conf = RuleLanguage.builder("conf", "Conf")
        .extensions("conf")
        .lineComment(";")
        .pattern(TokenType.TAG, "\\[[^\\]]*\\]")
        .pattern(TokenType.ATTRIBUTE, "[\\w.]+(?=\\s*=)")
        .string("\"")
        .literals("true", "false")
        .build();

viewer.setLanguage(conf);
```

| Method                           | What it adds                                                        |
|----------------------------------|---------------------------------------------------------------------|
| `extensions(String...)`          | The file extensions, for `Languages.forFileName`                    |
| `keywords(String...)`            | Words shown as keywords                                             |
| `literals(String...)`            | Words shown as literals, such as `true` and `null`                  |
| `types(String...)`               | Words shown as types                                                |
| `words(TokenType, String...)`    | Words shown as any other token type                                 |
| `ignoreCase()`                   | The words match in any case, as in SQL                              |
| `lineComment(String)`            | A comment that runs to the end of the line                          |
| `blockComment(String, String)`   | A comment between two delimiters, over any number of lines          |
| `string(String)`                 | A string on one line between two of the quote, with `\` escapes     |
| `multilineString(String, String)`| A string between two delimiters, over any number of lines           |
| `region(TokenType, String, String, char, boolean)` | Any other text between two delimiters: its type, the delimiters, the escape char or `RuleLanguage.NO_ESCAPE`, and whether it may span lines |
| `pattern(TokenType, String)`     | A regular expression, matched within one line                       |
| `operators(String)`              | The chars shown as operators                                        |
| `punctuation(String)`            | The chars shown as punctuation                                      |
| `wordChars(String)`              | Chars that are part of a word besides letters, digits and `_`       |
| `detectFunctions()`              | A word followed by `(` is shown as a function                       |
| `capitalizedTypes()`             | A capitalised word is shown as a type, an all-caps word as a constant |

At each position the patterns are tried first, in the order they were added, then the comments and strings,
then numbers, words, and single operator and punctuation chars.

A word right after a dot names a member, so it is never taken from the word lists: `get` in `headers.get`
is not shown as a keyword. With `detectFunctions()` it is shown as a function when a `(` follows.

Write a pattern without a choice inside a repetition, such as `(?:a|b)*`: on a line of a few thousand chars
Java runs out of stack with it. `[ab]*` matches the same and does not. A pattern that does overflow is taken
as not matching.

The line comment and the block comment are also what the editor uses for
[comment toggle](editor.md#change-the-text), and the keywords are offered by
[code completion](autocomplete.md).

## More words for a language

`toBuilder()` starts from a rule language that exists, so only what differs has to be added. This is how the
names of an API of your application are shown in a language, such as the scripts of an application in
JavaScript:

```java
Language script = new JavaScriptLanguage().toBuilder()
        .keywords("app")
        .words(TokenType.ATTRIBUTE, "request", "response")
        .build();

editor.setLanguage(script);
```

The new language has the id and the name of the one it started from; `id(String)` and `displayName(String)`
of the builder set others. The languages that come with the library are rule languages, all but Java, HTML,
Markdown and XML.

## Syntax laid over a language

`OverlayLanguage` adds syntax of your own to a language that exists. Wherever a pattern matches, its token
replaces whatever the base language made of that text, also inside a string or a comment. This is how
placeholders such as `{{name}}` are shown in a document that is otherwise JSON.

```java
Language json = Languages.byId("json").orElseThrow();
Language template = OverlayLanguage.over(json)
        .displayName("JSON with variables")
        .pattern(TokenType.VARIABLE, "\\{\\{[^{}]*\\}\\}")
        .build();

editor.setLanguage(template);
```

A match lies within one line. Where two matches overlap, the one that starts first is kept. The keywords and
the comments are those of the base language.

`VariablesDemoApp` in the `demo` module shows this, with a colour of its own for the variables, markers for
those without a value and code completion after `{{`.

## A lexer of your own

For a language that rules cannot describe, implement `Language` and `Lexer`.

A lexer reads one line at a time. Whatever must carry over a line break, such as being inside a block
comment, is an `int` state: the lexer gets the state at the start of the line and returns the state at its
end. It keeps nothing else between calls. That is what lets the viewer highlight any line of a large file
without reading the lines before it again.

```java
public final class MyLexer implements Lexer {

    private static final int IN_COMMENT = 1;

    @Override
    public int tokenize(char[] text, int start, int end, int state, TokenSink sink) {
        // text[start, end) is one line without its line break
        // report each token with sink.token(tokenStart, tokenLength, type)
        // chars not covered by a token are shown as plain text
        return Lexer.INITIAL_STATE; // or IN_COMMENT if the line ends inside a comment
    }
}
```

```java
public final class MyLanguage implements Language {

    @Override
    public String id() {
        return "my";
    }

    @Override
    public String displayName() {
        return "My language";
    }

    @Override
    public List<String> fileExtensions() {
        return List.of("my");
    }

    @Override
    public Lexer createLexer() {
        return new MyLexer();
    }
}
```

`keywords()`, `lineComment()` and `blockComment()` have defaults for a language without them; override them
for code completion and comment toggle.

## Register a language

A language passed to `setLanguage` needs no registration. To have it found by `Languages.byId`,
`Languages.forFileName` and `Languages.installed`, list its class in a file named
`META-INF/services/com.swingcraft4j.code.lexer.Language` in your resources:

```
com.example.MyLanguage
```

The class needs a public constructor without parameters. For a `RuleLanguage`, make a subclass:

```java
public final class ConfLanguage extends RuleLanguage {

    public ConfLanguage() {
        super(builder("conf", "Conf")
                .extensions("conf")
                .lineComment(";"));
    }
}
```

## Token types

A lexer names what each piece of text is with a `TokenType`; a [theme](themes.md) gives each type its colour.

`TEXT`, `KEYWORD`, `LITERAL`, `TYPE`, `FUNCTION`, `CONSTANT`, `IDENTIFIER`, `STRING`, `NUMBER`, `COMMENT`,
`DOC_COMMENT`, `ANNOTATION`, `OPERATOR`, `PUNCTUATION`, `TAG`, `ATTRIBUTE`, `PREPROCESSOR`, `VARIABLE`, `ERROR`
