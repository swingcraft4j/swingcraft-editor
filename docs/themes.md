# Themes

A `CodeTheme` holds the colours of a viewer or an editor: the background, the selection, the gutter, and a
style for each [token type](languages.md#token-types).

- [Set a theme](#set-a-theme)
- [The themes](#the-themes)
- [Adjust a theme](#adjust-a-theme)
- [A theme of your own](#a-theme-of-your-own)
- [Follow the look and feel](#follow-the-look-and-feel)

## Set a theme

```java
viewer.setTheme(CodeThemes.oneDark());
```

Without one, the viewer takes the light or the dark theme, by the look and feel at the time it is created.

## The themes

| Theme            | Method                        | Kind  |
|------------------|-------------------------------|-------|
| Light            | `CodeTheme.light()`           | Light |
| Dark             | `CodeTheme.dark()`            | Dark  |
| GitHub Light     | `CodeThemes.gitHubLight()`    | Light |
| Solarized Light  | `CodeThemes.solarizedLight()` | Light |
| One Dark         | `CodeThemes.oneDark()`        | Dark  |
| Dracula          | `CodeThemes.dracula()`        | Dark  |
| Monokai          | `CodeThemes.monokai()`        | Dark  |
| Nord             | `CodeThemes.nord()`           | Dark  |

`CodeThemes.all()` lists all eight, as for a picker. A theme has a `name()` to show and tells whether it
`isDark()`.

```java
JComboBox<String> picker = new JComboBox<>();
for (CodeTheme theme : CodeThemes.all()) {
    picker.addItem(theme.name());
}
picker.addActionListener(e -> viewer.setTheme(CodeThemes.all().get(picker.getSelectedIndex())));
```

## Adjust a theme

`toBuilder()` starts from a theme that exists, so only what differs has to be set.

```java
CodeTheme theme = CodeTheme.light().toBuilder()
        .name("Light with orange variables")
        .style(TokenType.VARIABLE, new TokenStyle(new Color(0xE8590C), Font.BOLD))
        .selectionBackground(new Color(0xCCE4FF))
        .build();
```

A `TokenStyle` is a colour and a font style:

```java
new TokenStyle(new Color(0x0033B3), Font.BOLD)
TokenStyle.of(0x067D17)     // plain, from an RGB value
TokenStyle.italic(0x8C8C8C) // italic
```

## A theme of your own

```java
CodeTheme theme = CodeTheme.builder()
        .name("Mine")
        .background(Color.WHITE)
        .foreground(Color.BLACK)
        .style(TokenType.KEYWORD, new TokenStyle(new Color(0x0033B3), Font.BOLD))
        .style(TokenType.STRING, TokenStyle.of(0x067D17))
        .style(TokenType.COMMENT, TokenStyle.italic(0x8C8C8C))
        .build();
```

| Builder method                         | What it colours                                         |
|----------------------------------------|---------------------------------------------------------|
| `background(Color)`                    | The background of the text                              |
| `foreground(Color)`                    | Text with no style of its own                           |
| `selectionBackground(Color)`           | The selection                                           |
| `searchBackground(Color)`              | The matches of a search                                 |
| `currentLineBackground(Color)`         | The line of the caret, in an editor                     |
| `bracketMatchBackground(Color)`        | The bracket at the caret and its partner                |
| `gutterBackground(Color)`              | The gutter                                              |
| `gutterForeground(Color)`              | The line numbers, the fold arrows and the dots of a fold mark |
| `gutterBorder(Color)`                  | The line between the gutter and the text                |
| `foldMarkerBackground(Color)`          | The `...` mark that stands for a collapsed fold         |
| `foldMarkerBorder(Color)`              | The line around that mark                               |
| `indentGuide(Color)`                   | The [indent guides](viewer.md#options)                  |
| `style(TokenType, TokenStyle)`         | The tokens of one type                                  |
| `markerColor(Severity, Color)`         | The [markers](markers.md) of one severity               |

A theme that does not set the two colours of the fold mark gets them made from its background and its
foreground, so the mark shows also where the gutter has the colour of the text background. The colour of the
indent guides is made the same way where it is not set.

## Follow the look and feel

`CodeTheme.forLookAndFeel()` gives the dark theme when the look and feel has a dark text background, else
the light one. Call it again after the look and feel changes:

```java
FlatDarkLaf.setup();
FlatLaf.updateUI();
viewer.setTheme(CodeTheme.forLookAndFeel());
```

The other way round, a dark code theme calls for a dark look and feel around it:

```java
if (theme.isDark() != FlatLaf.isLafDark()) {
    if (theme.isDark()) {
        FlatDarkLaf.setup();
    } else {
        FlatLightLaf.setup();
    }
    FlatLaf.updateUI();
}
viewer.setTheme(theme);
```

The library does not need FlatLaf; these examples are for an application that uses it.
