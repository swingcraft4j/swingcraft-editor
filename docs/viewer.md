# Viewer

`JCodeViewer` shows source code that is not to be changed. It is cheap to show: only the visible rows are
measured, tokenized and painted, so a file of a million lines opens and scrolls like a small one.

- [Show code](#show-code)
- [Large files](#large-files)
- [When the text is replaced](#when-the-text-is-replaced)
- [Options](#options)
- [Font](#font)
- [Selection](#selection)
- [Find](#find)
- [Go to a line](#go-to-a-line)
- [Folding](#folding)
- [Keys](#keys)

All the methods must be called on the event dispatch thread.

## Show code

```java
JCodeViewer viewer = new JCodeViewer();
viewer.setText(source);
viewer.setLanguage(Languages.byId("java").orElse(null));
frame.add(new JScrollPane(viewer));
```

Put the viewer in a `JScrollPane`. The line numbers install themselves as the row header of the scroll pane.

A language is looked up by its id or by a file name; see [Languages](languages.md). With no language the
text is shown plain.

```java
viewer.setLanguage(Languages.forFileName("build.xml").orElse(null));
```

## Large files

The text of a viewer is a `TextModel`. `ArrayTextModel` is the one for text that does not change: it reads a
file straight into one array.

```java
TextModel model = ArrayTextModel.load(path, StandardCharsets.UTF_8);
viewer.setDocument(model, Languages.forFileName(path.getFileName().toString()).orElse(null));
```

`setDocument` sets the text and the language in one step. Loading may be done on a background thread; only
`setDocument` has to be called on the event dispatch thread.

A line longer than 20,000 chars is shown without highlighting, to keep painting it cheap.

## When the text is replaced

`setText`, `setModel` and `setDocument` each show another model. That is reported as the property `model`:

```java
viewer.addPropertyChangeListener("model", e -> status.setText(viewer.getLineCount() + " lines"));
```

An edit in an editor is not another model; for edits, see [Listen to edits](editor.md#listen-to-edits).

## Options

| Method                           | Default | What it does                                                       |
|----------------------------------|---------|--------------------------------------------------------------------|
| `setLineWrap(boolean)`           | `false` | Wraps lines at the width of the view instead of scrolling sideways |
| `setWrapStyleWord(boolean)`      | `false` | Wraps after the last blank that fits, not after the last char      |
| `setLineNumbersVisible(boolean)` | `true`  | Shows the gutter with the line numbers                             |
| `setFoldingEnabled(boolean)`     | `true`  | Shows the fold arrows in the gutter                                |
| `setBracketMatching(boolean)`    | `true`  | Highlights the bracket at the caret and its partner                |
| `setRoundedSelection(boolean)`   | `true`  | Draws the selection with rounded corners                           |
| `setTabSize(int)`                | `4`     | The width of a tab, in chars                                       |
| `setBottomPadding(int)`          | `0`     | Empty space below the last line, in pixels                         |
| `setTheme(CodeTheme)`            |         | The colours; see [Themes](themes.md). Reported as the property `theme` |

With a bottom padding the end of the text can be scrolled up, out from under what lies over the bottom of the
view, such as a floating toolbar.

Wrapping at words reads better, but every line that wraps then has to be read whenever the width changes. A
document with very many long lines re-wraps more slowly with it.

## Font

The font is the default font of the look and feel, and it changes with the look and feel. With FlatLaf that
is the font under the key `defaultFont`:

```java
FlatRobotoFont.install();
UIManager.put("defaultFont", FontUtils.getCompositeFont(FlatRobotoFont.FAMILY, Font.PLAIN, 13));
FlatLightLaf.setup();
```

Set a font of your own with `setFont`. From then on the viewer keeps it; `setFont(null)` goes back to the
font of the look and feel.

```java
viewer.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
```

Code is usually shown in a monospaced font, and the viewer is at its fastest with one: text lies on a grid of
equal cells. Any other font works too. Then every char is measured, columns of code do not line up, and bold
text is placed at the widths of the plain font.

## Selection

Offsets are counted in chars from the start of the document.

```java
viewer.select(start, end);
String text = viewer.getSelectedText();
int caret = viewer.getCaretPosition();
viewer.addSelectionListener(e -> update());
```

`selectAll()` and `copy()` do what the keys and the popup menu do. `getOffsetBounds(offset)` gives the place
of an offset in the component, and `offsetAt(x, y)` the offset nearest to a point.

## Find

`JCodeFindBar` is a find bar for a viewer or an editor. Add it above or below the scroll pane; it stays
hidden until it is opened.

```java
JCodeFindBar findBar = new JCodeFindBar(viewer);
frame.add(new JScrollPane(viewer), BorderLayout.CENTER);
frame.add(findBar, BorderLayout.SOUTH);
```

Ctrl+F opens it and Escape closes it. It finds as you type, with options for match case, whole word and
regular expression. Enter moves to the next match and Shift+Enter to the previous one.

To search without the bar, set a `TextSearch`:

```java
viewer.setSearch(new TextSearch("total", false, true, false)); // query, match case, whole word, regex
viewer.findNext();
viewer.findPrevious();
```

All the matches in view are highlighted. `findNext()` and `findPrevious()` return `false` when there is no
match. `setSearch(null)` removes the highlights.

## Go to a line

```java
viewer.goToLine(119);        // moves the caret to the line and brings it into view
viewer.scrollToLine(119);    // scrolls the line to the top; the caret stays
viewer.showGoToLineDialog(); // asks for the line
```

Lines are counted from zero in the methods, so line 120 of the gutter is `119`.

## Folding

A line is foldable when the lines after it are indented more. A click on the arrow in the gutter collapses it.
A collapsed fold shows a `...` mark after its first line, and a click on the mark expands it again. The
colours of the mark are part of the [theme](themes.md#a-theme-of-your-own).

From code:

```java
if (viewer.isFoldable(line)) {
    viewer.collapseFold(line); // zero-based
}
viewer.expandFold(line);
viewer.toggleFold(line);
viewer.expandAllFolds();
```

## Keys

Ctrl is Command on macOS.

| Key                  | What it does             |
|----------------------|--------------------------|
| Ctrl+C               | Copy                     |
| Ctrl+A               | Select all               |
| Ctrl+F               | Open the find bar        |
| F3, Shift+F3         | Find next, find previous |
| Ctrl+G               | Go to a line             |
| Ctrl+Minus           | Collapse the fold        |
| Ctrl+Equals          | Expand the fold          |
| Ctrl+Shift+Equals    | Expand all the folds     |

The viewer has a popup menu with the main commands. Going to a line is not among them; it has its key, Ctrl+G.
