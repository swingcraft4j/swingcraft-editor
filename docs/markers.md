# Markers

A `Marker` is a note on a range of a document, such as an error found by a compiler. A viewer or an editor
underlines the range with a wavy line, puts a dot on its line in the gutter, and shows the message in a
tooltip, over the text and over the dot.

- [Set markers](#set-markers)
- [Find markers as the text changes](#find-markers-as-the-text-changes)
- [A slow analysis](#a-slow-analysis)
- [Colours](#colours)

## Set markers

```java
editor.setMarkers(List.of(
        Marker.error(120, 127, "Cannot resolve symbol 'missing'"),
        Marker.warning(40, 52, "Unused variable"),
        Marker.info(8, 8, "Something to know about this place")));
```

The range runs from the offset of its first char to the offset just past its last. A marker whose start and
end are the same marks a position. Offsets past the end of the document are cut off.

| Method                      | Severity  |
|-----------------------------|-----------|
| `Marker.error(...)`         | `ERROR`   |
| `Marker.warning(...)`       | `WARNING` |
| `Marker.info(...)`          | `INFO`    |

The dot in the gutter has the colour of the most serious marker on the line.

`setMarkers(List.of())` removes them, and `getMarkers()` gives those that are set.

## Find markers as the text changes

A `MarkerProvider` is asked for the markers when a document is shown, and again a moment after each pause
in editing.

```java
editor.setMarkerProvider((model, language) -> {
    List<Marker> markers = new ArrayList<>();
    for (int line = 0; line < model.lineCount(); line++) {
        if (model.lineLength(line) > 120) {
            markers.add(Marker.warning(model.lineStart(line) + 120, model.lineEnd(line), "Line too long"));
        }
    }
    return markers;
});
```

It is called on the event dispatch thread, so it must be quick. `setMarkerProvider(null)` stops it and
removes the markers.

## A slow analysis

An analysis that takes longer, such as a compiler, belongs on a thread of your own. Start it from an edit
listener and hand the markers to the editor when it is done:

```java
editor.addEditListener(e -> {
    String text = editor.getText();
    executor.submit(() -> {
        List<Marker> markers = analyze(text);
        SwingUtilities.invokeLater(() -> {
            if (text.equals(editor.getText())) { // the text has not changed since
                editor.setMarkers(markers);
            }
        });
    });
});
```

## Colours

The colours come from the [theme](themes.md), one for each severity.

```java
CodeTheme theme = CodeTheme.light().toBuilder()
        .markerColor(Severity.WARNING, new Color(0xB58900))
        .build();
editor.setTheme(theme);
```
