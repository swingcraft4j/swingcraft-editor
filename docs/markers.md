# Markers

A `Marker` is a note on a range of a document, such as an error found by a compiler. A viewer or an editor
underlines the range with a wavy line, puts a dot after the number of its line in the gutter, and shows the
message in a tooltip, over the text and over the gutter. A stripe at the right shows where the markers are
in the whole text, and F2 goes from one to the next.

- [Set markers](#set-markers)
- [Find markers as the text changes](#find-markers-as-the-text-changes)
- [A slow analysis](#a-slow-analysis)
- [The dots of the gutter](#the-dots-of-the-gutter)
- [The stripe at the right](#the-stripe-at-the-right)
- [Go to a marker](#go-to-a-marker)
- [Listen to the markers](#listen-to-the-markers)
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

## The dots of the gutter

A line with a marker has a dot after its number, in the colour of the most serious marker on the line. The
dot stands in space that is there already, so the gutter is as wide with markers as without. A click on it
goes to the marker, and the gutter gives the messages of the line as a tooltip.

```java
editor.setMarkerDots(false);   // no dots
```

## The stripe at the right

The stripe lies between the text and the vertical scroll bar. It has a mark for each marker, as far down
as the marker is in the document, and as long as the lines of its range. So it shows where the markers are
in the whole text, also those that are not in view; where several fall on one place, the most serious one
shows. While the whole text is in view, each mark is beside its line.

- A mark gives the messages of its markers as a tooltip, and a click on it goes to the marker.
- A square at the top has the colour of the most serious marker of the text, and is green when there are
  none. Its tooltip counts them, as `2 errors, 1 warning`, and a click on it goes to the next marker. The
  square is there once the viewer has had markers or a marker provider.

The stripe is there with markers or without, and whether or not there is a scroll bar, so the text does not
move when the first marker comes. It takes 10 pixels at the right, or as many as you set.

```java
editor.setMarkerStripeWidth(14);
editor.setMarkerStripe(false);   // no stripe
```

The dots and the stripe are set apart from each other: both, which is the default, either one, or neither,
which leaves the wavy lines and their tooltips.

## Go to a marker

F2 moves the caret to the next marker after it, and Shift+F2 to the one before; after the last comes the
first again. The view scrolls to the marker, and a fold that hides it opens.

```java
editor.goToNextMarker();
editor.goToPreviousMarker();
editor.goToMarker(marker);
```

The actions are `ACTION_NEXT_MARKER` and `ACTION_PREVIOUS_MARKER`; to give them other keys, see
[Change the keys](viewer.md#change-the-keys).

## Listen to the markers

A marker listener is told when there are other markers than there were: set by the application, found by
the marker provider, or moved or removed by an edit. It keeps a list of problems or a count in a status bar
in step.

```java
editor.addMarkerListener(e -> problems.setText(editor.getMarkers().size() + " problems"));
```

## Colours

The colours come from the [theme](themes.md), one for each severity.

```java
CodeTheme theme = CodeTheme.light().toBuilder()
        .markerColor(Severity.WARNING, new Color(0xB58900))
        .build();
editor.setTheme(theme);
```
