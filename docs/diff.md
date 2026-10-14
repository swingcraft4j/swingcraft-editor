# Diff

Two versions of a text are compared line by line, as `git diff` compares them, and the difference is
written as a unified diff that a viewer highlights.

- [Show the difference of two texts](#show-the-difference-of-two-texts)
- [The changes as data](#the-changes-as-data)
- [Highlighting a diff](#highlighting-a-diff)

## Show the difference of two texts

```java
String patch = UnifiedDiff.of(original, revised)
        .names("a/Main.java", "b/Main.java")
        .context(3)
        .format();

JCodeViewer output = new JCodeViewer();
output.setLanguage(Languages.byId("diff").orElse(null));
output.setText(patch);
```

What comes out is what `git diff` prints:

```diff
--- a/Main.java
+++ b/Main.java
@@ -1,4 +1,4 @@
 class Main {
-    int count;
+    long count;
 
     void run() {
```

| Method                       | What it sets                                                              |
|------------------------------|---------------------------------------------------------------------------|
| `names(String, String)`      | The names after `---` and `+++`; `a` and `b` by default                   |
| `context(int)`               | How many unchanged lines are shown around each change; 3 by default       |

Changes whose lines of context would meet go in one hunk. A text that does not end with a line break gets
`\ No newline at end of file` after its last line. A line break of `\r\n` is a part of its line, so two
texts that differ only in their line breaks differ in every line, as they do to git. Two equal texts give
an empty string.

To compare two editors while they are typed in, compare again a moment after each pause:

```java
Timer timer = new Timer(300, e -> output.setText(UnifiedDiff.of(
        left.getModel().getText(0, left.getModel().length()),
        right.getModel().getText(0, right.getModel().length())).format()));
timer.setRepeats(false);
left.addEditListener(e -> timer.restart());
right.addEditListener(e -> timer.restart());
```

The demo `DiffDemoApp` does this.

## The changes as data

`Diff.compare` gives the changes themselves, for an application that shows them in its own way:

```java
List<Change> changes = Diff.compare(originalLines, revisedLines);
for (Change change : changes) {
    // lines [originalStart, originalEnd()) of the original became
    // lines [revisedStart, revisedEnd()) of the revised version
}
```

A `Change` with `isInsert()` takes no lines away, one with `isDelete()` puts none in. Lines are compared
whole, with `equals`. The changes are the fewest lines deleted and inserted that turn one text into the
other, found with the algorithm of Myers in the form whose memory grows with the length of the texts only.
A line that is in one of the texts only is changed whatever else is, so it is left out of the search,
which keeps two large texts that have little in common from taking long.

## Highlighting a diff

The language `diff` highlights a diff or a patch, also a file `.diff` or `.patch` that `git diff` wrote:

| What                                          | Token type |
|-----------------------------------------------|------------|
| A line put in, `+`                            | `INSERTED` |
| A line taken away, `-`                        | `DELETED`  |
| The header of a hunk, `@@ -1,4 +1,4 @@`       | `TYPE`     |
| The header of a file, `diff`, `---`, `+++`    | `KEYWORD`  |
| `\ No newline at end of file`                 | `COMMENT`  |

Inside a hunk the lines its header gives are counted, so a line taken away that starts with `--`, as a
comment of SQL does, is not mistaken for the header of the next file.

Every [theme](themes.md) shows `INSERTED` in green and `DELETED` in red. A theme of your own that sets no
style for them gets a green and a red that suit its background.
