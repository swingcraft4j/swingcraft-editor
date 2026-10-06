package com.swingcraft4j.markdown;

import com.swingcraft4j.code.editor.JCodeEditor;
import com.swingcraft4j.code.theme.CodeTheme;

import javax.swing.JEditorPane;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.event.ChangeListener;
import javax.swing.event.HyperlinkEvent;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.StyleSheet;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.Font;
import java.awt.Point;
import java.beans.PropertyChangeListener;
import java.net.URI;
import java.net.URL;

/**
 * Shows Markdown as the page it describes: headings, lists, tables, links, images and code
 * blocks, the code highlighted as a code editor of this library would show it.
 * <pre>{@code
 * JMarkdownPreview preview = new JMarkdownPreview();
 * preview.setMarkdown("# Hello\n\nSome **bold** text.");
 * frame.add(new JScrollPane(preview));
 * }</pre>
 * To show what is typed in an editor as it is typed, let the preview {@link #follow} the editor.
 * The colours and the font are those of the Look and Feel, and follow it when it changes. All
 * the methods must be called on the event dispatch thread.
 */
public class JMarkdownPreview extends JEditorPane {

    /** How long after the last edit the preview of an editor is brought up to date. */
    private static final int FOLLOW_DELAY = 200;

    private String markdown = "";
    /** The theme that was set, or null while the one that goes with the Look and Feel is used. */
    private CodeTheme codeTheme;
    private Font codeFont;
    private boolean openLinks = true;
    /** Set once the fields are there: the Look and Feel is installed before, from the constructor of the superclass. */
    private final boolean ready;

    private JCodeEditor followed;
    private final Timer followTimer = new Timer(FOLLOW_DELAY, e -> showFollowed());
    private final ChangeListener editListener = e -> followTimer.restart();
    private final PropertyChangeListener editorListener = e -> followTimer.restart();

    public JMarkdownPreview() {
        setContentType("text/html");
        setEditable(false);
        // the font of the component is the font of the text, not the one HTML has by default
        putClientProperty(HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
        followTimer.setRepeats(false);
        addHyperlinkListener(event -> {
            if (openLinks && event.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                open(event);
            }
        });
        ready = true;
        render();
    }

    public JMarkdownPreview(String markdown) {
        this();
        setMarkdown(markdown);
    }

    public String getMarkdown() {
        return markdown;
    }

    /** Shows the Markdown. A preview in a scroll pane stays where it is scrolled to. */
    public void setMarkdown(String markdown) {
        this.markdown = markdown == null ? "" : markdown;
        render();
    }

    /** The theme of the code blocks: the one that was set, or else the one that goes with the Look and Feel. */
    public CodeTheme getCodeTheme() {
        return codeTheme != null ? codeTheme : CodeTheme.forLookAndFeel();
    }

    /** Sets the colours of the code in the code blocks; null goes back to the theme that goes with the Look and Feel. */
    public void setCodeTheme(CodeTheme codeTheme) {
        this.codeTheme = codeTheme;
        render();
    }

    /** The font of the code: the one that was set, or else the monospaced font in the size of the text. */
    public Font getCodeFont() {
        return codeFont != null ? codeFont : new Font(Font.MONOSPACED, Font.PLAIN, getFont().getSize());
    }

    /** Sets the font of the code, in the lines and in the code blocks; null goes back to the monospaced font. */
    public void setCodeFont(Font codeFont) {
        this.codeFont = codeFont;
        render();
    }

    public boolean isOpenLinks() {
        return openLinks;
    }

    /**
     * Whether a click on a link opens it in the browser of the user; on by default. Only the
     * addresses of the web and of mail are opened. To do something else with a link, switch
     * this off and add a hyperlink listener.
     */
    public void setOpenLinks(boolean openLinks) {
        this.openLinks = openLinks;
    }

    /** Sets what the images and the links with a relative address are relative to, such as the folder of the file. */
    public void setBase(URL base) {
        ((HTMLDocument) getDocument()).setBase(base);
        render();
    }

    /** The editor whose text is shown, or null. */
    public JCodeEditor getFollowed() {
        return followed;
    }

    /**
     * Shows the text of an editor, and goes on showing it while it is edited: a moment after
     * each pause in typing, with the theme and the font of the editor for the code. Null stops
     * that; the preview keeps what it shows.
     */
    public void follow(JCodeEditor editor) {
        if (followed != null) {
            followed.removeEditListener(editListener);
            followed.removePropertyChangeListener("model", editorListener);
            followed.removePropertyChangeListener("font", editorListener);
            followed.removePropertyChangeListener("theme", editorListener);
        }
        followTimer.stop();
        followed = editor;
        if (editor != null) {
            editor.addEditListener(editListener);
            // another text is shown in the editor, or it is given another font or theme
            editor.addPropertyChangeListener("model", editorListener);
            editor.addPropertyChangeListener("font", editorListener);
            editor.addPropertyChangeListener("theme", editorListener);
            showFollowed();
        }
    }

    private void showFollowed() {
        if (followed != null) {
            codeTheme = followed.getTheme();
            codeFont = followed.getFont();
            setMarkdown(followed.getText());
        }
    }

    /** Sets the font of the text of the page. The code has a font of its own; see {@link #setCodeFont}. */
    @Override
    public void setFont(Font font) {
        super.setFont(font);
        if (ready) {
            render();
        }
    }

    /** Also takes the colours and the font of the new Look and Feel. */
    @Override
    public void updateUI() {
        super.updateUI();
        if (ready) {
            render();
        }
    }

    // ---- Rendering ----

    private void render() {
        JViewport viewport = getParent() instanceof JViewport parent ? parent : null;
        Point position = viewport != null ? viewport.getViewPosition() : null;
        URL base = getDocument() instanceof HTMLDocument old ? old.getBase() : null;

        // a document of its own each time, with the style sheet for the colours as they are now
        HTMLDocument document = (HTMLDocument) ((HTMLEditorKit) getEditorKit()).createDefaultDocument();
        document.setBase(base);
        addRules(document.getStyleSheet());
        setDocument(document);
        setText("<html><body>" + MarkdownHtml.render(markdown, getCodeTheme()) + "</body></html>");
        setCaretPosition(0);
        if (position != null) {
            // once the new text is laid out; before that the view is not as tall as it will be
            SwingUtilities.invokeLater(() -> {
                int maxY = Math.max(0, getHeight() - viewport.getExtentSize().height);
                viewport.setViewPosition(new Point(position.x, Math.min(position.y, maxY)));
            });
        }
    }

    /** The look of the page, from the colours of the Look and Feel and of the code theme. */
    private void addRules(StyleSheet styles) {
        CodeTheme theme = getCodeTheme();
        Font font = getFont();
        Font code = getCodeFont();
        int size = font.getSize();
        Color background = color("EditorPane.background", getBackground());
        Color foreground = color("EditorPane.foreground", getForeground());
        Color quiet = color("Label.disabledForeground", Color.GRAY);
        Color border = color("Component.borderColor", Color.LIGHT_GRAY);
        Color link = color("Component.linkColor", new Color(0x0969DA));
        String codeFamily = "font-family: '" + code.getFamily() + "'; font-size: " + code.getSize() + "pt;";

        styles.addRule("body { font-family: '" + font.getFamily() + "'; font-size: " + size + "pt; color: "
                + MarkdownHtml.hex(foreground) + "; background-color: " + MarkdownHtml.hex(background) + "; margin: 12px; }");
        styles.addRule("h1 { font-size: " + Math.round(size * 2f) + "pt; margin-top: 6px; margin-bottom: 10px; }");
        styles.addRule("h2 { font-size: " + Math.round(size * 1.5f) + "pt; margin-top: 14px; margin-bottom: 8px; }");
        styles.addRule("h3 { font-size: " + Math.round(size * 1.25f) + "pt; margin-top: 12px; margin-bottom: 6px; }");
        styles.addRule("h4, h5, h6 { font-size: " + size + "pt; margin-top: 10px; margin-bottom: 4px; }");
        styles.addRule("p { margin-top: 0px; margin-bottom: 8px; }");
        styles.addRule("a { color: " + MarkdownHtml.hex(link) + "; }");
        styles.addRule("ul, ol { margin-top: 0px; margin-bottom: 8px; margin-left: 22px; }");
        styles.addRule("blockquote { color: " + MarkdownHtml.hex(quiet) + "; margin-left: 12px; margin-top: 0px; margin-bottom: 8px; }");
        styles.addRule("code { " + codeFamily + " color: " + MarkdownHtml.hex(theme.foreground()) + "; background-color: "
                + MarkdownHtml.hex(mix(background, foreground, 0.08f)) + "; }");
        // a code block has the background of the code theme, unless that is the one of the page: then it would not show
        Color block = isNear(theme.background(), background) ? mix(background, foreground, 0.05f) : theme.background();
        styles.addRule("pre { " + codeFamily + " color: " + MarkdownHtml.hex(theme.foreground()) + "; background-color: "
                + MarkdownHtml.hex(block) + "; padding: 8px; margin-top: 0px; margin-bottom: 10px; }");
        // the background of a table shows between its cells, as its lines
        styles.addRule("table { background-color: " + MarkdownHtml.hex(border) + "; margin-bottom: 10px; }");
        styles.addRule("td { background-color: " + MarkdownHtml.hex(background) + "; }");
        styles.addRule("th { background-color: " + MarkdownHtml.hex(mix(background, foreground, 0.06f)) + "; }");
    }

    private static Color color(String key, Color fallback) {
        Color color = UIManager.getColor(key);
        // a colour of the Look and Feel is not kept: the next one brings its own
        return color != null ? new Color(color.getRGB()) : fallback != null ? fallback : Color.GRAY;
    }

    /** Whether two colours are so alike that one does not show on the other. */
    private static boolean isNear(Color a, Color b) {
        return Math.abs(a.getRed() - b.getRed()) + Math.abs(a.getGreen() - b.getGreen()) + Math.abs(a.getBlue() - b.getBlue()) < 24;
    }

    private static Color mix(Color from, Color to, float part) {
        return new Color(
                Math.round(from.getRed() + (to.getRed() - from.getRed()) * part),
                Math.round(from.getGreen() + (to.getGreen() - from.getGreen()) * part),
                Math.round(from.getBlue() + (to.getBlue() - from.getBlue()) * part));
    }

    /** Opens a link of the web or of mail in the application the user has for it; any other is left alone. */
    private static void open(HyperlinkEvent event) {
        String address = event.getURL() != null ? event.getURL().toString() : event.getDescription();
        if (address == null || !(address.startsWith("http://") || address.startsWith("https://") || address.startsWith("mailto:"))) {
            return;
        }
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(address));
            }
        } catch (Exception e) {
            // there is no browser to open it with, or the address is not one: the click does nothing
        }
    }
}
