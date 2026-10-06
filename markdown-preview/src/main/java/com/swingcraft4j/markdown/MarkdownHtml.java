package com.swingcraft4j.markdown;

import com.swingcraft4j.code.lexer.Language;
import com.swingcraft4j.code.lexer.Languages;
import com.swingcraft4j.code.lexer.Lexer;
import com.swingcraft4j.code.theme.CodeTheme;
import com.swingcraft4j.code.theme.TokenStyle;
import org.commonmark.Extension;
import org.commonmark.ext.autolink.AutolinkExtension;
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.commonmark.ext.gfm.tables.TableBlock;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.ext.heading.anchor.HeadingAnchorExtension;
import org.commonmark.ext.task.list.items.TaskListItemMarker;
import org.commonmark.ext.task.list.items.TaskListItemsExtension;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.HtmlBlock;
import org.commonmark.node.HtmlInline;
import org.commonmark.node.IndentedCodeBlock;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.NodeRenderer;
import org.commonmark.renderer.html.HtmlRenderer;
import org.commonmark.renderer.html.HtmlWriter;

import java.awt.Color;
import java.awt.Font;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns Markdown into the HTML that a Swing text component can show: the HTML of its time, in
 * which a struck-out text is {@code strike} and the colour of a word is a {@code font} tag.
 */
final class MarkdownHtml {

    // what GitHub has more than plain Markdown, and an id for each heading, which a link can lead to
    private static final List<Extension> EXTENSIONS = List.of(TablesExtension.create(), TaskListItemsExtension.create(),
            StrikethroughExtension.create(), AutolinkExtension.create(), HeadingAnchorExtension.create());

    /**
     * The tags of HTML written in the Markdown that are shown as HTML. Any other is shown as the
     * text it is: Swing makes a real component of a form field, sends a form that is submitted,
     * and creates an object of the class an {@code object} tag names.
     */
    private static final Set<String> SAFE_TAGS = Set.of("a", "b", "big", "blockquote", "br", "center", "code", "dd", "del",
            "details", "div", "dl", "dt", "em", "font", "h1", "h2", "h3", "h4", "h5", "h6", "hr", "i", "img", "kbd", "li", "ol",
            "p", "pre", "s", "samp", "small", "span", "strike", "strong", "sub", "summary", "sup", "table", "tbody", "td", "th",
            "thead", "tr", "tt", "u", "ul", "var");
    private static final Pattern TAG = Pattern.compile("</?([A-Za-z][A-Za-z0-9]*)[^<>]*>?");
    private static final Parser PARSER = Parser.builder().extensions(EXTENSIONS).build();

    private MarkdownHtml() {
    }

    /**
     * @param theme the colours of the code in the fenced blocks
     * @return the body of an HTML document, without the tags around it
     */
    static String render(String markdown, CodeTheme theme) {
        // the renderers of this class are added first: of two for one kind of node the first one is taken
        HtmlRenderer renderer = HtmlRenderer.builder()
                .nodeRendererFactory(context -> new CodeBlockRenderer(context.getWriter(), theme))
                .nodeRendererFactory(context -> new TaskMarkerRenderer(context.getWriter()))
                .nodeRendererFactory(context -> new HtmlTagRenderer(context.getWriter()))
                .attributeProviderFactory(context -> (node, tagName, attributes) -> {
                    if (node instanceof TableBlock) {
                        // The lines of a table are the gaps between its cells, through which its own
                        // background shows: a border of Swing takes no colour from a style sheet.
                        attributes.put("border", "0");
                        attributes.put("cellspacing", "1");
                        attributes.put("cellpadding", "5");
                    }
                })
                .extensions(EXTENSIONS)
                .build();
        return renderer.render(PARSER.parse(markdown)).replace("<del>", "<strike>").replace("</del>", "</strike>");
    }

    /** A code block with its code coloured, where the language its fence names is known. */
    private static final class CodeBlockRenderer implements NodeRenderer {

        private final HtmlWriter html;
        private final CodeTheme theme;

        CodeBlockRenderer(HtmlWriter html, CodeTheme theme) {
            this.html = html;
            this.theme = theme;
        }

        @Override
        public Set<Class<? extends Node>> getNodeTypes() {
            return Set.of(FencedCodeBlock.class, IndentedCodeBlock.class);
        }

        @Override
        public void render(Node node) {
            String code;
            Language language = null;
            if (node instanceof FencedCodeBlock fenced) {
                code = fenced.getLiteral();
                String info = fenced.getInfo() == null ? "" : fenced.getInfo().trim();
                // the language is the first word after the fence
                String name = info.split("[\\s{]", 2)[0];
                language = name.isEmpty() ? null : Languages.forName(name).orElse(null);
            } else {
                code = ((IndentedCodeBlock) node).getLiteral();
            }
            if (code.endsWith("\n")) {
                code = code.substring(0, code.length() - 1);
            }
            html.line();
            html.raw("<pre>");
            html.raw(language == null ? escape(code) : highlight(code, language, theme));
            html.raw("</pre>");
            html.line();
        }
    }

    /**
     * The box of a task as a char, in a span by which the preview finds it and draws a box in its
     * place: Swing would put a real check box there, which can be clicked and does nothing.
     */
    private static final class TaskMarkerRenderer implements NodeRenderer {

        private final HtmlWriter html;

        TaskMarkerRenderer(HtmlWriter html) {
            this.html = html;
        }

        @Override
        public Set<Class<? extends Node>> getNodeTypes() {
            return Set.of(TaskListItemMarker.class);
        }

        @Override
        public void render(Node node) {
            boolean done = ((TaskListItemMarker) node).isChecked();
            html.raw("<span class=\"" + (done ? MarkdownViews.TASK_DONE : MarkdownViews.TASK_OPEN) + "\">"
                    + (done ? "&#9745;" : "&#9744;") + "</span> ");
        }
    }

    /** HTML written in the Markdown, with only the tags that are safe to show left as tags. */
    private static final class HtmlTagRenderer implements NodeRenderer {

        private final HtmlWriter html;

        HtmlTagRenderer(HtmlWriter html) {
            this.html = html;
        }

        @Override
        public Set<Class<? extends Node>> getNodeTypes() {
            return Set.of(HtmlBlock.class, HtmlInline.class);
        }

        @Override
        public void render(Node node) {
            boolean block = node instanceof HtmlBlock;
            String literal = block ? ((HtmlBlock) node).getLiteral() : ((HtmlInline) node).getLiteral();
            if (block) {
                html.line();
            }
            html.raw(safe(literal));
            if (block) {
                html.line();
            }
        }
    }

    /** The HTML with every tag that is not one of the safe ones turned into the text it is written as. */
    static String safe(String literal) {
        Matcher matcher = TAG.matcher(literal);
        StringBuilder result = new StringBuilder(literal.length());
        while (matcher.find()) {
            boolean keep = SAFE_TAGS.contains(matcher.group(1).toLowerCase(Locale.ROOT));
            matcher.appendReplacement(result, Matcher.quoteReplacement(keep ? matcher.group() : escape(matcher.group())));
        }
        return matcher.appendTail(result).toString();
    }

    /** The code as HTML, each token in the colour and the style the theme gives its type. */
    static String highlight(String code, Language language, CodeTheme theme) {
        Lexer lexer = language.createLexer();
        StringBuilder html = new StringBuilder(code.length() * 2);
        int state = Lexer.INITIAL_STATE;
        for (String line : code.split("\n", -1)) {
            char[] text = line.toCharArray();
            int[] done = {0};
            state = lexer.tokenize(text, 0, text.length, state, (start, length, type) -> {
                // what lies between two tokens is plain text
                html.append(escape(new String(text, done[0], start - done[0])));
                TokenStyle style = theme.style(type);
                boolean bold = (style.fontStyle() & Font.BOLD) != 0;
                boolean italic = (style.fontStyle() & Font.ITALIC) != 0;
                html.append("<font color=\"").append(hex(style.color())).append("\">")
                        .append(bold ? "<b>" : "").append(italic ? "<i>" : "")
                        .append(escape(new String(text, start, length)))
                        .append(italic ? "</i>" : "").append(bold ? "</b>" : "").append("</font>");
                done[0] = start + length;
            });
            html.append(escape(new String(text, done[0], text.length - done[0]))).append('\n');
        }
        html.setLength(html.length() - 1);
        return html.toString();
    }

    static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** The colour as it is written in HTML: #rrggbb. */
    static String hex(Color color) {
        return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
    }
}
