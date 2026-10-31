# Config files

An `EditorConfig` reads a [theme](themes.md) and the settings of a viewer or an editor from an XML file, so
that they can be changed without changing the code. It needs nothing beyond the JDK.

- [Read a file](#read-a-file)
- [The file](#the-file)
- [The theme](#the-theme)
- [The settings](#the-settings)
- [Errors](#errors)

## Read a file

```java
EditorConfig config = EditorConfig.read(Path.of("editor.xml"));
config.applyTo(editor);
```

`applyTo` sets the theme, the font and every setting the file gives; what the file does not say stays as the
viewer or editor has it. One file serves a viewer and an editor alike: the settings only an editor has are left
out on a viewer.

A config can also come from a resource of the application, a `Reader`, or a string:

```java
try (InputStream in = App.class.getResourceAsStream("editor.xml")) {
    EditorConfig.read(in).applyTo(viewer);
}
EditorConfig.parse("<editor><settings><tabSize>2</tabSize></settings></editor>").applyTo(viewer);
```

`config.theme()` gives the theme alone, as an `Optional`, and `config.settings()` the settings by name.

## The file

```xml
<?xml version="1.0" encoding="UTF-8"?>
<editor>
    <theme base="One Dark" name="My Dark">
        <background>#1E1E1E</background>
        <selectionBackground>#264F78</selectionBackground>
        <style token="keyword" color="#C586C0" font="bold"/>
        <style token="comment" color="#6A9955" font="italic"/>
        <markerColor severity="warning">#CCA700</markerColor>
    </theme>
    <settings>
        <font family="JetBrains Mono" size="14"/>
        <lineNumbersVisible>true</lineNumbersVisible>
        <roundedSelection>false</roundedSelection>
        <tabSize>4</tabSize>
        <lineWrap>true</lineWrap>
        <tabsToSpaces>true</tabsToSpaces>
    </settings>
</editor>
```

Both `<theme>` and `<settings>` may be left out. A file whose root is `<theme>` holds a theme alone, to be
shared on its own:

```xml
<theme base="Nord" name="Nord, brighter">
    <foreground>#ECEFF4</foreground>
</theme>
```

## The theme

`base` names one of the [built-in themes](themes.md#the-themes) to start from, so that only what differs has to
be given; case, spaces and dashes do not count, so `One Dark`, `oneDark` and `one-dark` are the same theme.
Without a base the theme starts from `CodeTheme.builder()`. `name` is what the theme shows in a picker; without
one it keeps the name of its base.

Each colour is an element named after its [builder method](themes.md#a-theme-of-your-own): `background`,
`foreground`, `selectionBackground`, `searchBackground`, `currentLineBackground`, `bracketMatchBackground`,
`gutterBackground`, `gutterForeground`, `gutterBorder`, `stickyLinesBorder`, `foldMarkerBackground`, `foldMarkerBorder`,
`indentGuide`, `activeIndentGuide`, `insertedLineBackground`, `insertedTextBackground`, `deletedLineBackground`
and `deletedTextBackground`.

A colour is written as in CSS: `#RGB`, `#RRGGBB`, or `#RRGGBBAA` with alpha.

| Element                                                  | What it sets                                         |
|----------------------------------------------------------|------------------------------------------------------|
| `<style token="keyword" color="#0033B3" font="bold"/>`   | The tokens of one [type](languages.md#token-types); `font` is `plain` (the default), `bold`, `italic` or `bold italic` |
| `<markerColor severity="error">#D1242F</markerColor>`    | The [markers](markers.md) of one severity: `error`, `warning` or `info` |

The token type is written as its name in `TokenType`, in any case: `doc_comment` is `DOC_COMMENT`.

The demo reads ten themes from files of this kind, to start from or to copy: One Light, Gruvbox Light and
Dark, Catppuccin Latte and Mocha, GitHub Dark and Dark Dimmed, Solarized Dark, Tokyo Night and Kanagawa, in
[demo/src/main/resources/com/swingcraft4j/code/demo/themes](../demo/src/main/resources/com/swingcraft4j/code/demo/themes).
Its Theme menu also opens a theme file of one's own.

## The settings

Each setting is named after the setter it calls: `<lineWrap>true</lineWrap>` calls `setLineWrap(true)`. They are
applied in the order of this table, whatever the order in the file.

| Setting                | Value          | On     | What it does                                                 |
|------------------------|----------------|--------|--------------------------------------------------------------|
| `font`                 | see below      | both   | The font of the text                                         |
| `tabSize`              | 1 or more      | both   | The width of a tab                                           |
| `lineNumbersVisible`   | `true`/`false` | both   | The line numbers in the gutter                               |
| `gutterBorderVisible`  | `true`/`false` | both   | The line between the gutter and the text                     |
| `lineWrap`             | `true`/`false` | both   | Lines wrapped at the width of the view                       |
| `wrapStyleWord`        | `true`/`false` | both   | Wrapped lines broken at words                                |
| `roundedSelection`     | `true`/`false` | both   | The selection drawn with rounded corners                     |
| `roundedHighlights`    | `true`/`false` | both   | [Highlights](viewer.md#highlights) drawn with rounded corners |
| `highlightRadius`      | 0 or more      | both   | The radius of the corners of rounded highlights              |
| `bracketMatching`      | `true`/`false` | both   | The bracket at the caret and its partner marked              |
| `indentGuides`         | `true`/`false` | both   | The indent guides                                            |
| `foldingEnabled`       | `true`/`false` | both   | [Folding](viewer.md#folding)                                 |
| `stickyLinesVisible`   | `true`/`false` | both   | [Sticky lines](viewer.md#sticky-lines)                       |
| `stickyLinesMaxCount`  | 1 or more      | both   | How many sticky lines there are at most                      |
| `markerDots`           | `true`/`false` | both   | A dot in the gutter for a line with a [marker](markers.md)   |
| `markerStripe`         | `true`/`false` | both   | The stripe of markers at the right                           |
| `markerStripeWidth`    | 6 or more      | both   | The width of that stripe                                     |
| `topPadding`           | 0 or more      | both   | Empty space above the first line                             |
| `bottomPadding`        | 0 or more      | both   | Empty space below the last line                              |
| `leftPadding`          | 0 or more      | both   | Empty space at the left of the text                          |
| `rightPadding`         | 0 or more      | both   | Empty space at the right of the text                         |
| `editable`             | `true`/`false` | editor | Whether the text can be changed                              |
| `tabsToSpaces`         | `true`/`false` | editor | Tab inserts spaces                                           |
| `autoClosePairs`       | `true`/`false` | editor | Brackets and quotes closed as they are typed                 |
| `highlightCurrentLine` | `true`/`false` | editor | The line of the caret on the background of the theme         |

The [viewer](viewer.md#options) and the [editor](editor.md#options) say more of each.

`<font>` takes the attributes `family`, `size` and `style` (`plain`, `bold`, `italic` or `bold italic`), each of
which may be left out: `<font size="16"/>` makes the font that is there larger and keeps its family and style.

## Errors

A file that is not well-formed XML, or that says something `EditorConfig` does not know, throws an
`EditorConfigException`, an `IOException`, before anything is applied. Its message says what is wrong and lists
what would be right, and `getLineNumber()` gives the line:

```text
line 3: unknown setting <lineNumbers>; the settings are font, tabSize, lineNumbersVisible, ...
```

An unknown element or attribute is an error rather than being passed over, so that a misspelt setting does not
go unnoticed. Attributes in a namespace, such as `xsi:noNamespaceSchemaLocation`, are passed over.

The parser reads no DTD and no external entity, so a config file from a user or from the network can be read
safely.
