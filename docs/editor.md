# Editor

`JCodeEditor` is a [`JCodeViewer`](viewer.md) with editing. All that the viewer does, the editor does too:
the options, the font, find, folding and the themes are described in [Viewer](viewer.md).

- [Edit code](#edit-code)
- [Options](#options)
- [Change the text](#change-the-text)
- [Undo and redo](#undo-and-redo)
- [Modified](#modified)
- [Listen to edits](#listen-to-edits)
- [Large files](#large-files)
- [Keys](#keys)

All the methods must be called on the event dispatch thread.

## Edit code

```java
JCodeEditor editor = new JCodeEditor();
editor.setText(source);
editor.setLanguage(Languages.byId("java").orElse(null));
frame.add(new JScrollPane(editor));

String text = editor.getText();
```

While typing, the editor keeps the indentation of the line on Enter, indents one level more after an opening
bracket, closes brackets and quotes, and steps over a closing one that is already there. Text typed with an
input method is composed in place.

For code completion, see [Code completion](autocomplete.md). For errors and warnings, see
[Markers](markers.md).

## Options

| Method                             | Default | What it does                                                |
|------------------------------------|---------|-------------------------------------------------------------|
| `setEditable(boolean)`             | `true`  | Whether the text can be changed                             |
| `setTabsToSpaces(boolean)`         | `true`  | Tab inserts spaces up to the next tab stop instead of a tab |
| `setAutoClosePairs(boolean)`       | `true`  | Typing an opening bracket or a quote adds the closing one   |
| `setHighlightCurrentLine(boolean)` | `true`  | Gives the line of the caret the background of the theme     |

The options of the viewer apply as well, such as `setLineWrap`, `setTabSize` and `setTheme`.

## Change the text

```java
editor.replaceSelection("text");      // replaces the selection, or inserts at the caret
editor.replaceRange(start, end, "x"); // replaces the chars from start to end
editor.toggleComment();               // comments the selected lines, or uncomments them
editor.format();                      // formats the selected lines, or the whole text
editor.moveLinesUp();
editor.moveLinesDown();
editor.cut();
editor.paste();
```

Each call is one step for undo. `toggleComment()` uses the line comment of the language, or its block comment
if it has no line comment. `format()` uses the formatter of the language; see [Format](format.md).

## Undo and redo

```java
if (editor.canUndo()) {
    editor.undo();
}
editor.redo();
```

Chars typed one after the other are undone together, not one at a time.

## Modified

The editor knows whether the text differs from what it was when it was shown, or last saved.

```java
editor.addPropertyChangeListener("modified", e -> saveButton.setEnabled(editor.isModified()));

Files.writeString(path, editor.getText());
editor.markSaved();
```

Undoing back to the saved text makes the editor unmodified again.

## Listen to edits

```java
editor.addEditListener(e -> preview.setText(editor.getText()));
```

The listener is called after each change the editor makes to the text. It is not called when `setText` or
`setDocument` shows another text; for that, see [When the text is replaced](viewer.md#when-the-text-is-replaced).

To change the text further as part of the same edit, so that one undo takes both back, wrap the change in
`appendToLastEdit`:

```java
editor.addEditListener(e -> {
    if (needsFix()) {
        editor.appendToLastEdit(() -> editor.replaceRange(start, end, fixed));
    }
});
```

## Large files

`setText` copies the text into an editable model. To edit a document that is already loaded, pass a
`GapTextModel`:

```java
GapTextModel model = new GapTextModel(text);
editor.setDocument(model, Languages.byId("java").orElse(null));
```

A model that is not editable, such as an `ArrayTextModel`, is copied into one when it is set. After an edit
only the lines that changed are measured and highlighted again.

## Keys

Ctrl is Command on macOS. The keys of the [viewer](viewer.md#keys) work too.

| Key                          | What it does                                     | Action                                           |
|------------------------------|--------------------------------------------------|--------------------------------------------------|
| Ctrl+Z                       | Undo                                             | `ACTION_UNDO`                                    |
| Ctrl+Y, Ctrl+Shift+Z         | Redo                                             | `ACTION_REDO`                                    |
| Ctrl+X, Ctrl+V               | Cut, paste                                       | `ACTION_CUT`, `ACTION_PASTE`                     |
| Tab, Shift+Tab               | Indent, unindent; with a selection, its lines    | `"insert-tab"`, `"unindent"`                     |
| Ctrl+D                       | Duplicate the line, or the selected lines        | `ACTION_DUPLICATE_LINES`                         |
| Ctrl+/                       | Comment or uncomment the lines                   | `ACTION_TOGGLE_COMMENT`                          |
| Shift+Alt+F                  | Format the selected lines, or the whole text     | `ACTION_FORMAT`                                  |
| Alt+Up, Alt+Down             | Move the lines up or down                        | `ACTION_MOVE_LINES_UP`, `ACTION_MOVE_LINES_DOWN` |
| Ctrl+Backspace, Ctrl+Delete  | Delete the word before or after the caret        | `"delete-previous-word"`, `"delete-next-word"`   |

The editor has a popup menu with the main commands.

To give a command other keys, see [Change the keys](viewer.md#change-the-keys). The constants in the last
column are those of `JCodeEditor`.
