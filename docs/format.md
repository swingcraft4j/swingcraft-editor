# Format

An editor lays its text out afresh with Shift+Alt+F, or when `format()` is called. What that does depends on
the language of the document, which gives a `Formatter` or none.

- [Format the text](#format-the-text)
- [Languages](#languages)
- [Code with braces](#code-with-braces)
- [JSON](#json)
- [Markdown tables](#markdown-tables)
- [A formatter of your own](#a-formatter-of-your-own)

## Format the text

```java
editor.format();

formatItem.setEnabled(editor.canFormat()); // false for a language without a formatter
```

With a selection, the lines it touches are formatted; without one, the whole text. The lines of a selection
keep the indentation they have in common.

Formatting is one step for undo. Only the part of the text that comes out different is replaced, so text that
is formatted already is not an edit, and the caret stays with the text it was at, on the same row of the
screen. The text is indented as the editor indents it: `setTabSize` and `setTabsToSpaces` apply.

`format()` does nothing for a language without a formatter, and nothing when the editor is not editable.

## Languages

| Language                                                                | What formatting does                                        |
|-------------------------------------------------------------------------|-------------------------------------------------------------|
| Java, JavaScript, TypeScript, C, C++, C#, Kotlin, Go, Rust, Groovy, CSS | Indents the lines and puts the spaces right                 |
| JSON                                                                    | Puts each member and element on a line of its own, indented |
| Markdown                                                                | Aligns the columns of the tables                            |

Python and YAML have no formatter, since their indentation is their meaning. Neither have Shell, Dockerfile,
INI, Properties, TOML and Env. HTML, XML, SQL and PHP have none yet.

## Code with braces

```java
public class Counter{
private final Map<String,Integer> counts=new HashMap<>();
public int add(String key,int n){
if(n<0||key==null){
return -1;
}
return counts.merge(key,n,
Integer::sum);
}
}
```

becomes

```java
public class Counter {
    private final Map<String, Integer> counts = new HashMap<>();
    public int add(String key, int n) {
        if (n < 0 || key == null) {
            return -1;
        }
        return counts.merge(key, n,
                Integer::sum);
    }
}
```

- Each line is indented by the brackets around it. A line that goes on with the statement of the line before,
  or lies inside parentheses, is indented further: by 8 in Java, C, C++ and Groovy, by 4 in the others. The
  labels of a `switch` and their statements are indented, and so is the statement of an `if` without braces.
- Operators, commas, semicolons, keywords and braces get the spaces they should have. Several spaces become
  one.
- Every line break is kept. No line is wrapped and none are joined. More than two blank lines become two.
- Strings and comments are not changed. The lines of a block comment move with its first line. A comment on a
  line of its own is indented as the code below it, and the spaces before a comment at the end of a line are
  kept.
- A line under an open parenthesis that was lined up with what follows the parenthesis stays lined up.

The formatter reads the tokens of the code and not its grammar. So code that is half typed is formatted too,
and where the tokens do not tell what a char means, the space around it is left as it was written:

| Language               | What is special                                                                 |
|------------------------|---------------------------------------------------------------------------------|
| Java                   | Type arguments, `List<String>`, are told from comparisons, `a < b`              |
| JavaScript, TypeScript | A regular expression, `/a+b/`, is left alone. A file with JSX in it is not formatted |
| C, C++                 | Preprocessor lines are copied. The space at a `*` or `&` is left as written, since it may point or multiply |
| C++                    | `public:` and its like stand at the level of the class                          |
| C#, Kotlin, Rust       | The space at a `?` is left as written, since it may mark a type                 |
| Go                     | Tabs, one blank line, `case` at the level of the `switch`, and columns lined up by gofmt stay lined up |
| CSS                    | Only commas, semicolons, opening braces and the colon of a declaration are spaced: `a:hover` and `a > b` are left |

For Java this follows the defaults of IntelliJ IDEA.

To format a language of your own this way, build a formatter for it:

```java
@Override
public Formatter formatter() {
    return BraceFormatter.of(this).generics().ternary().build();
}
```

The methods of the builder say what the language has: `generics()`, `ternary()`, `pointers()`,
`regexLiterals()`, `noSemicolons()`, `continuationIndent(levels)` and others; see the Javadoc of
`BraceFormatter.Builder`.

## JSON

```json
{"name":"x","tags":[1,2],"empty":{}}
```

becomes

```json
{
    "name": "x",
    "tags": [
        1,
        2
    ],
    "empty": {}
}
```

- Comments are kept: one at the end of a line stays at the end of that line, and one on a line of its own
  stays on a line of its own.
- One blank line between two members is kept; more than one becomes one.
- What JSON5 allows is formatted too: names without quotes, strings in single quotes, a comma after the last
  member.
- The grammar is not checked. Text with a comma missing is still laid out, and the comma is not added.
- Text whose brackets do not match, or with a string or a comment that is not closed, is left as it is.

## Markdown tables

```markdown
| Name | Qty | Price |
|:--|:-:|--:|
| Tea | 2 | 3.50 |
| 咖啡 | 10 | 12.00 |
```

becomes

```markdown
| Name | Qty | Price |
| :--- | :-: | ----: |
| Tea  |  2  |  3.50 |
| 咖啡 | 10  | 12.00 |
```

- Each column gets the width of its widest cell. Width is measured in cells of a monospaced font: Chinese,
  Japanese and Korean chars and emoji count as two.
- The row under the header keeps its colons, and the cells are padded on the side the colons ask for.
- A pipe after a backslash, `\|`, and a pipe inside a code span do not end a cell.
- Every row gets a pipe at each end. A row with cells missing gets them, empty.
- Tables inside fenced code blocks are not touched, and neither is any text outside a table.

A table is a line with a pipe in it, then a row of dashes with as many cells, then the lines up to the first
one that is blank or has no pipe.

## A formatter of your own

A `Formatter` takes a text and returns it formatted. Give it to the editor through the language:

```java
public final class MyLanguage implements Language {

    // id(), displayName(), fileExtensions(), createLexer()

    @Override
    public Formatter formatter() {
        return (text, options) -> text.replaceAll("[ \\t]+(?=\\R|$)", ""); // no blanks at the ends of lines
    }
}
```

`FormatOptions` tells how the editor indents:

| Method            | What it gives                                                    |
|-------------------|------------------------------------------------------------------|
| `tabSize()`       | The cells of a tab stop, and of one level of indentation         |
| `useTabs()`       | Whether a level is a tab char instead of spaces                  |
| `lineSeparator()` | What ends the lines of the document; use it for the lines you add |
| `indent(levels)`  | The white space for a number of levels                           |

Two rules for a formatter:

- It never loses text. Where it cannot make sense of the text, it returns the text unchanged.
- It may be given some lines from the middle of a document, when the user formats a selection. They come
  without the indentation they share, which the editor puts back.

A formatter does not need an editor: `new JsonFormatter().format(text, new FormatOptions(2, false))`.
