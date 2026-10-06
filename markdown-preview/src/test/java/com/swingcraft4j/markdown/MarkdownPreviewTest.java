package com.swingcraft4j.markdown;

import com.swingcraft4j.code.editor.JCodeEditor;
import com.swingcraft4j.code.theme.CodeTheme;
import com.swingcraft4j.code.theme.CodeThemes;
import org.junit.jupiter.api.Test;

import javax.swing.JScrollPane;
import javax.swing.event.HyperlinkEvent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarkdownPreviewTest {

    private static String html(String markdown) {
        return MarkdownHtml.render(markdown, CodeTheme.light());
    }

    @Test
    void rendersTheTextOfAPage() {
        assertEquals("<h1 id=\"title\">Title</h1>\n<p>Some <strong>bold</strong> and <em>it</em> and <code>code</code>.</p>\n",
                html("# Title\n\nSome **bold** and *it* and `code`."));
        assertTrue(html("[guide](docs/guide.md) and https://example.com").contains("<a href=\"https://example.com\">"),
                "a bare address is a link");
        assertEquals("<p><strike>gone</strike></p>\n", html("~~gone~~"), "struck out the way Swing knows it");
    }

    @Test
    void showsOnlyTheHtmlThatIsSafeAsHtml() {
        assertEquals("<p>a <b>bold</b> and <kbd>Ctrl</kbd><br> and <span style=\"color:red\">red</span></p>\n",
                html("a <b>bold</b> and <kbd>Ctrl</kbd><br> and <span style=\"color:red\">red</span>"));
        // what Swing would make a component or an object of is shown as the text it is
        String form = html("<form action=\"https://example.com\"><input type=\"submit\"></form>\n\n"
                + "x <object classid=\"javax.swing.JButton\"></object> <script>alert(1)</script>");
        assertFalse(form.contains("<form") || form.contains("<input") || form.contains("<object") || form.contains("<script"), form);
        assertTrue(form.contains("&lt;form action=\"https://example.com\"&gt;"), form);
        assertTrue(form.contains("&lt;object classid"), form);
        assertEquals("a &lt;IFRAME src=x&gt; b <B>c</B> &lt;input", MarkdownHtml.safe("a <IFRAME src=x> b <B>c</B> <input"));
    }

    @Test
    void scrollsToAHeadingByItsId() {
        String filler = "text\n\n".repeat(60);
        JMarkdownPreview preview = new JMarkdownPreview("# One\n\n" + filler + "## Getting started\n\n" + filler
                + "## Getting started\n\n### What's `new` in 2.0?");
        JScrollPane scrollPane = new JScrollPane(preview);
        scrollPane.setSize(300, 200);
        scrollPane.doLayout();
        scrollPane.getViewport().doLayout();
        preview.setSize(280, preview.getPreferredSize().height);

        assertTrue(preview.scrollToHeading("getting-started"));
        int first = scrollPane.getViewport().getViewPosition().y;
        assertTrue(first > 0, "the heading is at the top of the view");
        assertFalse(preview.scrollToHeading("nothing-like-it"));
        assertEquals(first, scrollPane.getViewport().getViewPosition().y);

        // a click on a link to a heading does the same, also when the other links are not opened
        preview.setOpenLinks(false);
        preview.fireHyperlinkUpdate(new HyperlinkEvent(preview, HyperlinkEvent.EventType.ACTIVATED, null, "#getting-started-1"));
        assertTrue(scrollPane.getViewport().getViewPosition().y > first, "the second heading of that text");
        assertTrue(preview.scrollToHeading("whats-new-in-20"), "an id is without the punctuation of its heading");
    }

    @Test
    void rendersATaskListWithBoxesThatAreText() {
        String html = html("- [x] done\n- [ ] open");
        assertTrue(html.contains("&#9745; done"), html);
        assertTrue(html.contains("&#9744; open"), html);
        assertFalse(html.contains("<input"), html);
    }

    @Test
    void givesATableItsLines() {
        String html = html("| a | b |\n|---|---|\n| 1 | 2 |");
        assertTrue(html.contains("<table border=\"0\" cellspacing=\"1\" cellpadding=\"5\">"), html);
        assertTrue(html.contains("<td>1</td>"), html);
    }

    @Test
    void highlightsACodeBlockInTheLanguageOfItsFence() {
        CodeTheme theme = CodeTheme.light();
        String keyword = MarkdownHtml.hex(theme.style(com.swingcraft4j.code.lexer.TokenType.KEYWORD).color());
        String html = html("```js\nconst a = 1 < 2;\n```");
        assertTrue(html.startsWith("<pre><font color=\"" + keyword + "\">const</font> "), html);
        assertTrue(html.contains("&lt;"), "what is code is not read as HTML: " + html);
        assertTrue(html.endsWith("</pre>\n"), html);
        // a block of a language that is not known, and one made by indentation, are shown as they are
        assertEquals("<pre>a &lt;b&gt; &amp; c</pre>\n", html("```nothing\na <b> & c\n```"));
        assertEquals("<pre>x = 1</pre>\n", html("    x = 1"));
    }

    @Test
    void coloursTheCodeOverItsLines() {
        // the comment is open at the end of the first line and is carried on to the second
        String html = MarkdownHtml.highlight("/* a\nb */ x", new com.swingcraft4j.code.languages.javascript.JavaScriptLanguage(),
                CodeTheme.light());
        assertEquals(2, html.split("\n").length);
        assertTrue(html.split("\n")[1].startsWith("<font color="), html);
    }

    @Test
    void showsWhatAnEditorHolds() {
        JMarkdownPreview preview = new JMarkdownPreview("# One");
        assertEquals("# One", preview.getMarkdown());
        assertTrue(preview.getText().contains("One"));
        assertFalse(preview.isEditable());

        JCodeEditor editor = new JCodeEditor("## Two");
        editor.setTheme(CodeThemes.dracula());
        preview.follow(editor);
        assertEquals("## Two", preview.getMarkdown(), "the text of the editor is shown at once");
        assertEquals("Dracula", preview.getCodeTheme().name(), "with the theme of the editor for the code");
        preview.follow(null);
        assertEquals("## Two", preview.getMarkdown(), "and stays when the editor is let go");
    }
}