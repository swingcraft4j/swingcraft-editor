# Field

`JCodeField` is a text field for one line of code: a URL with variables in it, an expression, a query. It
looks like the text fields of the look and feel, and edits its text as a [`JCodeEditor`](editor.md) does, with
highlighting, code completion and markers.

- [Show a field](#show-a-field)
- [The editor of the field](#the-editor-of-the-field)
- [Options](#options)
- [Enter and Tab](#enter-and-tab)
- [A component at each end](#a-component-at-each-end)
- [The look of the field](#the-look-of-the-field)
- [What differs from an editor](#what-differs-from-an-editor)

All the methods must be called on the event dispatch thread.

`FieldDemoApp` in the `demo` module shows the fields of this page.

## Show a field

```java
JCodeField field = new JCodeField();
field.setLanguage(Languages.byId("json").orElse(null));
field.setPlaceholder("Enter a value");
field.addActionListener(e -> send(field.getText()));
panel.add(field);
```

The field is not put in a scroll pane. A text that is wider than the field is scrolled to where the caret is.

A text of one line is often none of the languages there are. An `OverlayLanguage` over plain text highlights
what matters in it and nothing else; see [Languages](languages.md#syntax-laid-over-a-language):

```java
Language url = OverlayLanguage.over(new PlainTextLanguage())
        .pattern(TokenType.VARIABLE, "\\{\\{([^{}]*)\\}\\}")
        .build();
field.setLanguage(url);
```

## The editor of the field

The editing is done by an editor of the field's own, `getEditor()`. All that is said of the
[editor](editor.md) and the [viewer](viewer.md) is done through it:

```java
JCodeEditor editor = field.getEditor();

editor.setTheme(CodeTheme.dark());                       // see Themes
editor.addEditListener(e -> changed(field.getText()));   // see Listen to edits
editor.setMarkerProvider(provider);                      // see Markers
new AutoCompletion(editor).addProvider(provider);        // see Code completion
editor.addFocusListener(listener);                       // the editor is what has the focus
```

Change the text through the field or its editor. A line break put straight into the model of the editor is
not kept out.

## Options

| Method                           | Default | What it does                                                        |
|----------------------------------|---------|---------------------------------------------------------------------|
| `setText(CharSequence)`          |         | Sets the text; its line breaks are left out                         |
| `setLanguage(Language)`          | none    | The language used for highlighting                                  |
| `setPlaceholder(String)`         | none    | The text shown, greyed, while the field is empty                    |
| `setColumns(int)`                | `0`     | How many chars wide the field would like to be; with zero, as wide as its text |
| `setEditable(boolean)`           | `true`  | Whether the text can be changed                                     |
| `setEnabled(boolean)`            | `true`  | Whether the field can be used at all                                |
| `setFont(Font)`                  |         | The font of the text; see [Font](viewer.md#font)                    |
| `setLeadingComponent(JComponent)`  | none  | A component inside the border, in front of the text                 |
| `setTrailingComponent(JComponent)` | none  | A component inside the border, after the text                       |

Of a text that is set, pasted or entered by code completion, the line breaks at its ends are left out and each
of the others becomes a blank.

## Enter and Tab

Enter tells the action listeners of the field; the command of the event is the text. Tab and Shift+Tab move
the focus to the next and the previous component, as in any text field.

While the popup of code completion is open, Enter and Tab take the suggestion instead.

## A component at each end

```java
JComboBox<String> method = new JComboBox<>(new String[]{"GET", "POST"});
field.setLeadingComponent(method);
field.setTrailingComponent(sendButton);
```

The component stands against the border and is as high as the field is inside it. Where the border has round
corners, make the component not opaque, so that it does not paint over them.

## The look of the field

The border, the background and the mark of the focus are painted by a text field of the look and feel that
lies under the editor, so the field looks like the text fields beside it in any look and feel.

A client property whose name starts with `JComponent.` or `FlatLaf.` is passed on to that text field. With
FlatLaf, that is how the field gets an outline or a style:

```java
field.putClientProperty("JComponent.outline", "error");
field.putClientProperty("FlatLaf.style", "arc: 8; margin: 4,6,4,6");
```

The colours of the text are those of the [theme](themes.md) of the editor. The background of the theme is not
used: the field has the background of a text field.

## What differs from an editor

The editor of a field is set up for one line:

- no line numbers, no folding, no background for the line of the caret, no matching of brackets;
  `getEditor().setBracketMatching(true)` turns the last on again
- Enter breaks no line and Tab types none
- no keys for what is done to lines: go to a line, the folds, duplicate, move, comment and format
- Up and Down move the caret to the start and to the end of the text
