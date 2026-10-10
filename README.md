# SwingCraft4j Editor

A source code viewer and editor for Java Swing desktop applications, with syntax highlighting and code
completion, and a preview of Markdown. The code editor has no dependencies.

| Component      | What it gives                                                                     | Docs                        |
|----------------|-----------------------------------------------------------------------------------|-----------------------------|
| `JCodeViewer`  | A read-only view of source code: cheap to show, also for very large files         | [Viewer](docs/viewer.md)    |
| `JCodeEditor`  | The viewer with editing: undo, indentation, comment toggle, input methods         | [Editor](docs/editor.md)    |
| `JCodeFindBar` | A find bar for either of them                                                     | [Viewer](docs/viewer.md#find) |
| `JMarkdownPreview` | Markdown shown as the page it describes, also while it is typed in an editor  | [Markdown preview](docs/markdown.md) |

| Topic          | What it covers                                                                    | Docs                                    |
|----------------|-----------------------------------------------------------------------------------|-----------------------------------------|
| Languages      | The languages that come with the library, and how to add your own                 | [Languages](docs/languages.md)          |
| Themes         | The colour themes, and how to make or adjust one                                  | [Themes](docs/themes.md)                |
| Code completion | Suggestions, snippets, documentation and parameter hints                         | [Code completion](docs/autocomplete.md) |
| Markers        | Errors and warnings underlined in the text and marked in the gutter               | [Markers](docs/markers.md)              |
| Format         | Laying the text out afresh: indentation and spaces of code, JSON, tables of Markdown | [Format](docs/format.md)             |

Website: https://www.swingcraft4j.com

## Features

- Syntax highlighting for 25 languages, and for your own: C, C++, C#, CSS, Dockerfile, Env, Go, Groovy, HTML,
  INI, Java, JavaScript, TypeScript, JSON, Kotlin, Markdown, PHP, Properties, Python, Rust, Shell, SQL, TOML,
  XML and YAML
- CSS and JavaScript highlighted inside HTML, and the code of a fenced block inside Markdown
- Line wrap, at any char or at words
- Line numbers, code folding and matching brackets
- Find, with match case, whole word and regular expression, and go to line
- Eight colour themes, light and dark
- Code completion with snippets, documentation and parameter hints, in a popup the user can resize
- Error and warning markers
- Format, for Java and ten other languages with braces, for JSON and for the tables of Markdown
- A preview of Markdown, with tables, task lists and highlighted code blocks, in a module of its own
- Large documents: only the visible rows are measured, tokenized and painted

## Requirements

- Java 17 or later

## Installation

The library is in development, before its first release. It is published as a snapshot; add the snapshot
repository to use it:

```xml
<repositories>
    <repository>
        <id>central-snapshots</id>
        <url>https://central.sonatype.com/repository/maven-snapshots/</url>
        <releases>
            <enabled>false</enabled>
        </releases>
        <snapshots>
            <enabled>true</enabled>
        </snapshots>
    </repository>
</repositories>

<dependency>
    <groupId>com.swingcraft4j</groupId>
    <artifactId>code-editor</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

The code editor has no dependencies, so nothing else comes in with it.

The preview of Markdown is a module of its own, `markdown-preview`, which brings the code editor and
[commonmark-java](https://github.com/commonmark/commonmark-java) with it, and
[JSVG](https://github.com/weisJ/jsvg) for the images that are SVG; see
[Markdown preview](docs/markdown.md).

## Quick start

A viewer, to show code:

```java
JCodeViewer viewer = new JCodeViewer();
viewer.setText(source);
viewer.setLanguage(Languages.byId("java").orElse(null));
frame.add(new JScrollPane(viewer));
```

An editor, to change it:

```java
JCodeEditor editor = new JCodeEditor();
editor.setText(source);
editor.setLanguage(Languages.forFileName("Main.java").orElse(null));
AutoCompletion.install(editor);
frame.add(new JScrollPane(editor));
```

Put the component in a `JScrollPane`: the line numbers install themselves as the row header of the scroll
pane. All the methods must be called on the event dispatch thread.

## Demo

The `demo` module has four demos. It is not published, and it is the only module that uses
[FlatLaf](https://github.com/JFormDesigner/FlatLaf).

| Class              | What it shows                                                        |
|--------------------|----------------------------------------------------------------------|
| `DemoApp`          | The viewer, with the languages, the themes and a file of a million lines |
| `EditorDemoApp`    | The editor, with code completion, parameter hints and markers        |
| `VariablesDemoApp` | Syntax of your own laid over a language: `{{variables}}` in JSON     |
| `MarkdownDemoApp`  | Markdown edited at the left and previewed at the right               |

```
mvn install
mvn -pl demo exec:java
```

That runs `DemoApp`. Run another with `-Dexec.mainClass=com.swingcraft4j.code.demo.EditorDemoApp`, or run the
classes from your IDE.

## Build

```
mvn install
```

The build needs JDK 17 or later.

## License

[MIT](LICENSE)
