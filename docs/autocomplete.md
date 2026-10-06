# Code completion

`AutoCompletion` gives a [`JCodeEditor`](editor.md) a popup of suggestions that opens as a word is typed.
`ParameterHints` shows the parameters of a call while its arguments are typed.

- [Install](#install)
- [Suggestions of your own](#suggestions-of-your-own)
- [A suggestion](#a-suggestion)
- [Snippets](#snippets)
- [Members after a dot](#members-after-a-dot)
- [Only your suggestions](#only-your-suggestions)
- [Slow sources](#slow-sources)
- [Options](#options)
- [Parameter hints](#parameter-hints)
- [Keys](#keys)

## Install

```java
AutoCompletion completion = AutoCompletion.install(editor);
```

That offers the keywords of the [language](languages.md), the snippets that come with the library for it,
and the words in the document.

What is typed need not be the start of a suggestion: `tv` finds `totalValue`.

To choose the sources yourself, start empty:

```java
AutoCompletion completion = new AutoCompletion(editor);
completion.addProvider(CompletionProviders.keywords());
completion.addProvider(CompletionProviders.words());
```

`uninstall()` removes code completion from the editor again.

## Suggestions of your own

A `CompletionProvider` returns the suggestions for a request.

```java
completion.addProvider(request -> List.of(
        new Completion("println", CompletionKind.METHOD),
        new Completion("printf", CompletionKind.METHOD)));
```

A provider may return all it knows. Suggestions that do not match what was typed are dropped afterwards, and
so are duplicates. It is called on the event dispatch thread each time the popup opens or the word changes,
so it must answer quickly; for a source that cannot, see [Slow sources](#slow-sources).

The `CompletionRequest` tells where the caret is:

| Method               | What it gives                                                          |
|----------------------|------------------------------------------------------------------------|
| `prefix()`           | The part of a word typed just before the caret; may be empty           |
| `prefixStart()`      | The offset where that word starts                                      |
| `offset()`           | The caret position                                                     |
| `lineBefore()`       | The text of the caret's line up to the caret                           |
| `charBeforePrefix()` | The char just before the word, such as `.`                             |
| `qualifier()`        | The word before that char: `items` for `items.ad`                      |
| `language()`         | The language of the document, or `null`                                |
| `model()`            | The document; not for a provider that answers in the background        |

## A suggestion

A `Completion` is the text to insert and, if you like, more to show with it.

```java
new Completion("size", CompletionKind.METHOD)
        .withDetail("int")                               // shown beside the text, in grey
        .withDocumentation("Returns the number of elements.") // shown beside the list
        .withTemplate("size()");                         // inserted instead of the text
```

The kind sets the icon: `KEYWORD`, `SNIPPET`, `WORD`, `METHOD`, `FIELD`, `VARIABLE`, `CLASS` or `OTHER`.

## Snippets

A template is text with tab stops to fill in.

```java
Completion.snippet("for", "for (int ${i} = 0; ${i} < ${count}; ${i}++) {\n\t$0\n}")
```

| In the template | What it is                                                                 |
|-----------------|----------------------------------------------------------------------------|
| `${name}`       | A tab stop holding the text `name`                                         |
| `$0`            | Where the caret ends up; without it, the end of the snippet                |
| `\n`            | A line break, followed by the indentation of the line the snippet is on    |
| `\t`            | One level of the editor's indentation                                      |

After the snippet is inserted its first tab stop is selected. Tab moves to the next one and Shift+Tab to the
one before. Tab stops of the same name are linked: what is typed in one appears in the others, so the `i` of
the loop above is renamed in all three places at once. Escape ends it, and so does moving the caret out of
the snippet.

Any suggestion can have a template, through `withTemplate`. This one inserts a call and selects its
argument:

```java
new Completion("add", CompletionKind.METHOD).withTemplate("add(${item})")
```

While a snippet is selected in the popup, what it inserts is shown beside the list, highlighted as the editor
would show it.

`Snippets.forLanguage("java")` gives the snippets that come with the library for a language.

## Members after a dot

A provider can ask for the popup to open as soon as a char is typed, before any letter of a word.

```java
completion.addProvider(new CompletionProvider() {

    @Override
    public String triggerCharacters() {
        return ".";
    }

    @Override
    public List<Completion> complete(CompletionRequest request) {
        if (request.charBeforePrefix() != '.') {
            return List.of();
        }
        return membersOf(request.qualifier()); // "items" for "items."
    }
});
```

## Only your suggestions

In some places the keywords and the words of the document would be wrong, as inside a placeholder. A provider
says so with `isExclusive`, and then the other providers are not asked.

```java
@Override
public boolean isExclusive(CompletionRequest request) {
    String before = request.lineBefore();
    return before.substring(0, before.length() - request.prefix().length()).endsWith("{{");
}
```

## Slow sources

A source that cannot answer at once, such as a compiler or a server, is added with `addAsyncProvider`. It is
called on a background thread, and its suggestions join the list when they arrive, if the caret is still at
the same word.

```java
completion.addAsyncProvider(request -> server.complete(request.lineBefore(), request.prefix()));
```

Such a provider must not read `request.model()`. The rest of the request is a snapshot it may use freely.

## Options

| Method                           | Default | What it does                                              |
|----------------------------------|---------|-----------------------------------------------------------|
| `setAutoActivation(boolean)`     | `true`  | Opens the popup while typing; else only on Ctrl+Space     |
| `setAutoActivationLength(int)`   | `2`     | How many chars of a word are typed before the popup opens |

`showCompletions()` opens the popup and `hide()` closes it. `isShowing()` tells whether it is open.

The user resizes the popup by dragging the bar at its edge: up and down anywhere on it, and at its end,
where the three dots are, to the side as well. The list is then that wide, and grows no taller than that.
Between the list and the documentation beside it is a divider to drag, which gives the one what it takes
from the other. To remember the sizes from one run of the application to the next:

```java
completion.setPopupSize(savedSize); // null lets the popup size itself to its suggestions
completion.setDocumentationWidth(savedWidth);
completion.addPopupSizeListener(e -> save(completion.getPopupSize(), completion.getDocumentationWidth()));
```

A width or a height of 0 in the size is one the popup still chooses itself.

## Parameter hints

`ParameterHints` shows the parameters of a call while the caret is between its parentheses, with the one
being typed in bold. You tell it the parameters of a function by its name.

```java
ParameterHints.install(editor, name -> switch (name) {
    case "substring" -> List.of("int beginIndex", "int endIndex");
    case "indexOf" -> List.of("String text");
    default -> null; // not known: no hint
});
```

Where functions of one name have different parameters, implement `ParameterHints.Provider` and override the
method that is also given what the function is called on:

```java
ParameterHints.install(editor, new ParameterHints.Provider() {

    @Override
    public List<String> parameters(String functionName) {
        return null; // called on nothing: not known
    }

    @Override
    public List<String> parameters(String qualifier, String functionName) {
        // "app.headers" and "set" for app.headers.set(
        return qualifier.equals("app.headers") && functionName.equals("set")
                ? List.of("String name", "String value") : null;
    }
});
```

Only the line of the caret is looked at, so a call whose opening parenthesis is on an earlier line gets no
hint.

## Keys

Ctrl is Command on macOS.

| Key                           | What it does                              |
|-------------------------------|-------------------------------------------|
| Ctrl+Space                    | Open the popup                            |
| Up, Down, Page Up, Page Down  | Move through the suggestions              |
| Enter, Tab                    | Insert the selected suggestion            |
| Escape                        | Close the popup                           |
| Tab, Shift+Tab                | In a snippet: the next, the previous tab stop |

The focus stays in the editor while the popup is open.
