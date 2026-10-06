package com.swingcraft4j.markdown;

import com.swingcraft4j.code.editor.JCodeEditor;
import com.swingcraft4j.code.theme.CodeTheme;
import com.swingcraft4j.code.theme.CodeThemes;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarkdownPreviewTest {

    private static String html(String markdown) {
        return MarkdownHtml.render(markdown, CodeTheme.light());
    }

    @Test
    void rendersTheTextOfAPage() {
        assertEquals("<h1>Title</h1>\n<p>Some <strong>bold</strong> and <em>it</em> and <code>code</code>.</p>\n",
                html("# Title\n\nSome **bold** and *it* and `code`."));
        assertTrue(html("[guide](docs/guide.md) and https://example.com").contains("<a href=\"https://example.com\">"),
                "a bare address is a link");
        assertEquals("<p><strike>gone</strike></p>\n", html("~~gone~~"), "struck out the way Swing knows it");
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