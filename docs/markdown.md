# Markdown preview

`JMarkdownPreview` shows Markdown as the page it describes. It is a module of its own, `markdown-preview`,
which uses [commonmark-java](https://github.com/commonmark/commonmark-java) to read the Markdown and the code
editor to highlight the code blocks.

- [Installation](#installation)
- [Show Markdown](#show-markdown)
- [Follow an editor](#follow-an-editor)
- [What is shown](#what-is-shown)
- [Code blocks](#code-blocks)
- [Links and images](#links-and-images)
- [Colours and font](#colours-and-font)

All the methods must be called on the event dispatch thread.

## Installation

```xml
<dependency>
    <groupId>com.swingcraft4j</groupId>
    <artifactId>markdown-preview</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

The code editor and commonmark-java come in with it. For the snapshot repository, see the
[README](../README.md#installation).

## Show Markdown

```java
JMarkdownPreview preview = new JMarkdownPreview();
preview.setMarkdown("# Hello\n\nSome **bold** text.");
frame.add(new JScrollPane(preview));
```

Put the preview in a `JScrollPane`. `setMarkdown` can be called again at any time; a preview that is scrolled
stays where it is.

## Follow an editor

To show what is typed in a [`JCodeEditor`](editor.md) as it is typed:

```java
JCodeEditor editor = new JCodeEditor();
editor.setLanguage(Languages.byId("markdown").orElse(null));

JMarkdownPreview preview = new JMarkdownPreview();
preview.follow(editor);

frame.add(new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new JScrollPane(editor), new JScrollPane(preview)));
```

The preview is brought up to date a moment after each pause in typing, and when another text is set in the
editor. The code in it has the [theme](themes.md) and the font of the editor, and takes another one when the
editor does. `follow(null)` lets the editor go; the preview keeps what it shows.

## What is shown

Headings, paragraphs, emphasis, lists, quotes, rules, links, images, code in a line and code blocks, and of
what GitHub has more than plain Markdown: tables, task lists, text that is struck out, and addresses that are
links without being written as one.

The preview is a `JEditorPane`, and shows the page with the HTML that Swing knows. That sets its limits:

- the box of a task is a char, not a check box, and cannot be clicked
- a quote is set in and greyed, without a line at its side
- HTML written in the Markdown is shown as far as Swing knows the tags

## Code blocks

The code of a fenced block is highlighted in the language its fence names, as the code editor would show it:

````
```java
record Item(String name, int quantity) {}
```
````

The name is looked up with `Languages.forName`: the id of a [language](languages.md), one of its file
extensions, or a name it commonly goes by, such as `js` or `c++`. A block of a language that is not known, or
without one, is shown plain.

```java
preview.setCodeTheme(CodeThemes.dracula());                   // the colours of the code
preview.setCodeFont(new Font(Font.MONOSPACED, Font.PLAIN, 13)); // the font of the code
```

Without a theme, the code has the light or the dark theme, by the look and feel. A preview that follows an
editor takes both from the editor.

## Links and images

A click on a link opens it in the browser of the user, if it is an address of the web or of mail. To do
something else with a link, switch that off and add a listener:

```java
preview.setOpenLinks(false);
preview.addHyperlinkListener(e -> {
    if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
        open(e.getDescription());
    }
});
```

An image or a link with a relative address is relative to the base. Set it to the folder of the file that is
shown:

```java
preview.setBase(file.getParent().toUri().toURL());
```

## Colours and font

The text has the font and the colours of the look and feel, and takes the new ones when the look and feel
changes. `setFont` sets another font for the text, and `setCodeFont` one for the code. A preview that follows
an editor takes only the font of its code from the editor; to give all of it the font of the editor:

```java
editor.addPropertyChangeListener("font", e -> preview.setFont(editor.getFont()));
```
