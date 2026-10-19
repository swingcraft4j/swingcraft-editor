package com.swingcraft4j.code.config;

import com.swingcraft4j.code.editor.JCodeEditor;
import com.swingcraft4j.code.lexer.TokenType;
import com.swingcraft4j.code.marker.Severity;
import com.swingcraft4j.code.theme.CodeTheme;
import com.swingcraft4j.code.theme.CodeThemes;
import com.swingcraft4j.code.theme.TokenStyle;
import com.swingcraft4j.code.viewer.JCodeViewer;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.awt.Color;
import java.awt.Font;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.ObjIntConsumer;

/**
 * A theme and the settings of a viewer or an editor, read from an XML file. The settings are
 * named after the setters they call: {@code <lineNumbersVisible>} calls
 * {@link JCodeViewer#setLineNumbersVisible}, {@code <tabSize>} calls {@link JCodeViewer#setTabSize}.
 * A file says only what it changes; the rest stays as the component has it.
 *
 * <pre>{@code
 * <editor>
 *     <theme base="One Dark" name="My Dark">
 *         <background>#1E1E1E</background>
 *         <style token="keyword" color="#C586C0" font="bold"/>
 *         <markerColor severity="warning">#CCA700</markerColor>
 *     </theme>
 *     <settings>
 *         <font family="JetBrains Mono" size="14"/>
 *         <lineNumbersVisible>true</lineNumbersVisible>
 *         <roundedSelection>false</roundedSelection>
 *         <tabSize>4</tabSize>
 *     </settings>
 * </editor>
 * }</pre>
 *
 * A file whose root is {@code <theme>} holds a theme alone. The parser reads no DTD and no
 * external entity, so a file from anywhere can be read safely.
 */
public final class EditorConfig {

    /** A setting: what kind of value it takes, and the setter it calls. */
    private record Setting(boolean flag, int min, Class<? extends JCodeViewer> owner, BiConsumer<JCodeViewer, Object> setter) {
    }

    private static final Map<String, Setting> SETTINGS = new LinkedHashMap<>();
    private static final Map<String, BiConsumer<CodeTheme.Builder, Color>> COLORS = new LinkedHashMap<>();

    static {
        number("tabSize", 1, JCodeViewer::setTabSize);
        flag("lineNumbersVisible", JCodeViewer::setLineNumbersVisible);
        flag("lineWrap", JCodeViewer::setLineWrap);
        flag("wrapStyleWord", JCodeViewer::setWrapStyleWord);
        flag("roundedSelection", JCodeViewer::setRoundedSelection);
        flag("roundedHighlights", JCodeViewer::setRoundedHighlights);
        number("highlightRadius", 0, JCodeViewer::setHighlightRadius);
        flag("bracketMatching", JCodeViewer::setBracketMatching);
        flag("indentGuides", JCodeViewer::setIndentGuides);
        flag("foldingEnabled", JCodeViewer::setFoldingEnabled);
        flag("markerDots", JCodeViewer::setMarkerDots);
        flag("markerStripe", JCodeViewer::setMarkerStripe);
        number("markerStripeWidth", 6, JCodeViewer::setMarkerStripeWidth);
        number("topPadding", 0, JCodeViewer::setTopPadding);
        number("bottomPadding", 0, JCodeViewer::setBottomPadding);
        number("leftPadding", 0, JCodeViewer::setLeftPadding);
        number("rightPadding", 0, JCodeViewer::setRightPadding);
        editorFlag("editable", JCodeEditor::setEditable);
        editorFlag("tabsToSpaces", JCodeEditor::setTabsToSpaces);
        editorFlag("autoClosePairs", JCodeEditor::setAutoClosePairs);
        editorFlag("highlightCurrentLine", JCodeEditor::setHighlightCurrentLine);

        COLORS.put("background", CodeTheme.Builder::background);
        COLORS.put("foreground", CodeTheme.Builder::foreground);
        COLORS.put("selectionBackground", CodeTheme.Builder::selectionBackground);
        COLORS.put("searchBackground", CodeTheme.Builder::searchBackground);
        COLORS.put("currentLineBackground", CodeTheme.Builder::currentLineBackground);
        COLORS.put("bracketMatchBackground", CodeTheme.Builder::bracketMatchBackground);
        COLORS.put("gutterBackground", CodeTheme.Builder::gutterBackground);
        COLORS.put("gutterForeground", CodeTheme.Builder::gutterForeground);
        COLORS.put("gutterBorder", CodeTheme.Builder::gutterBorder);
        COLORS.put("foldMarkerBackground", CodeTheme.Builder::foldMarkerBackground);
        COLORS.put("foldMarkerBorder", CodeTheme.Builder::foldMarkerBorder);
        COLORS.put("indentGuide", CodeTheme.Builder::indentGuide);
        COLORS.put("activeIndentGuide", CodeTheme.Builder::activeIndentGuide);
        COLORS.put("insertedLineBackground", CodeTheme.Builder::insertedLineBackground);
        COLORS.put("insertedTextBackground", CodeTheme.Builder::insertedTextBackground);
        COLORS.put("deletedLineBackground", CodeTheme.Builder::deletedLineBackground);
        COLORS.put("deletedTextBackground", CodeTheme.Builder::deletedTextBackground);
    }

    private static void flag(String name, BiConsumer<JCodeViewer, Boolean> setter) {
        SETTINGS.put(name, new Setting(true, 0, JCodeViewer.class, (viewer, value) -> setter.accept(viewer, (Boolean) value)));
    }

    private static void editorFlag(String name, BiConsumer<JCodeEditor, Boolean> setter) {
        SETTINGS.put(name, new Setting(true, 0, JCodeEditor.class, (viewer, value) -> setter.accept((JCodeEditor) viewer, (Boolean) value)));
    }

    private static void number(String name, int min, ObjIntConsumer<JCodeViewer> setter) {
        SETTINGS.put(name, new Setting(false, min, JCodeViewer.class, (viewer, value) -> setter.accept(viewer, (Integer) value)));
    }

    private CodeTheme theme;
    private String fontFamily;
    private Integer fontSize;
    private Integer fontStyle;
    private final Map<String, Object> settings = new HashMap<>();

    private EditorConfig() {
    }

    /** Reads a config file; its encoding is the one its XML declaration gives, UTF-8 without one. */
    public static EditorConfig read(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            return read(in);
        }
    }

    /** Reads a config from a stream, such as a resource of the application; the stream is not closed. */
    public static EditorConfig read(InputStream in) throws IOException {
        try {
            return readRoot(factory().createXMLStreamReader(in));
        } catch (XMLStreamException e) {
            throw malformed(e);
        }
    }

    /** Reads a config from a reader; the reader is not closed. */
    public static EditorConfig read(Reader reader) throws IOException {
        try {
            return readRoot(factory().createXMLStreamReader(reader));
        } catch (XMLStreamException e) {
            throw malformed(e);
        }
    }

    /** Reads a config from the XML in a string. */
    public static EditorConfig parse(String xml) throws EditorConfigException {
        try {
            return read(new StringReader(xml));
        } catch (EditorConfigException e) {
            throw e;
        } catch (IOException e) {
            throw new AssertionError("a string cannot fail to be read", e);
        }
    }

    /** The theme of the file, if it has one. */
    public Optional<CodeTheme> theme() {
        return Optional.ofNullable(theme);
    }

    /**
     * The settings the file gives, by name, each a {@link Boolean} or an {@link Integer}, in the
     * order they are applied. The font is not among them.
     */
    public Map<String, Object> settings() {
        Map<String, Object> ordered = new LinkedHashMap<>();
        for (String name : SETTINGS.keySet()) {
            if (settings.containsKey(name)) {
                ordered.put(name, settings.get(name));
            }
        }
        return Collections.unmodifiableMap(ordered);
    }

    /**
     * Sets the theme, the font and the settings the file gives on a viewer or an editor. The
     * settings only an editor has, such as {@code tabsToSpaces}, are left out on a viewer, so
     * that one file serves both.
     */
    public void applyTo(JCodeViewer viewer) {
        if (theme != null) {
            viewer.setTheme(theme);
        }
        if (fontFamily != null || fontSize != null || fontStyle != null) {
            Font current = viewer.getFont();
            int style = fontStyle != null ? fontStyle : current.getStyle();
            int size = fontSize != null ? fontSize : current.getSize();
            viewer.setFont(fontFamily != null ? new Font(fontFamily, style, size) : current.deriveFont(style, (float) size));
        }
        SETTINGS.forEach((name, setting) -> {
            Object value = settings.get(name);
            if (value != null && setting.owner().isInstance(viewer)) {
                setting.setter().accept(viewer, value);
            }
        });
    }

    // ---- Reading ----

    private static XMLInputFactory factory() {
        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        return factory;
    }

    private static EditorConfig readRoot(XMLStreamReader xml) throws EditorConfigException {
        try {
            EditorConfig config = new EditorConfig();
            xml.nextTag();
            switch (xml.getLocalName()) {
                case "editor" -> config.readEditor(xml);
                case "theme" -> config.theme = readTheme(xml);
                default -> throw error(xml, "the root must be <editor> or <theme>, not <" + xml.getLocalName() + ">");
            }
            xml.close();
            return config;
        } catch (XMLStreamException e) {
            throw malformed(e);
        }
    }

    private void readEditor(XMLStreamReader xml) throws XMLStreamException, EditorConfigException {
        attributes(xml, Set.of());
        boolean themeRead = false;
        boolean settingsRead = false;
        while (nextChild(xml)) {
            switch (xml.getLocalName()) {
                case "theme" -> {
                    if (themeRead) {
                        throw error(xml, "<theme> is given twice");
                    }
                    themeRead = true;
                    theme = readTheme(xml);
                }
                case "settings" -> {
                    if (settingsRead) {
                        throw error(xml, "<settings> is given twice");
                    }
                    settingsRead = true;
                    readSettings(xml);
                }
                default -> throw error(xml, "unknown element <" + xml.getLocalName() + ">; <editor> holds <theme> and <settings>");
            }
        }
    }

    private static CodeTheme readTheme(XMLStreamReader xml) throws XMLStreamException, EditorConfigException {
        Map<String, String> attributes = attributes(xml, Set.of("base", "name"));
        String base = attributes.get("base");
        CodeTheme.Builder builder = base != null ? baseTheme(xml, base).toBuilder() : CodeTheme.builder();
        if (attributes.containsKey("name")) {
            builder.name(attributes.get("name"));
        }
        while (nextChild(xml)) {
            String element = xml.getLocalName();
            BiConsumer<CodeTheme.Builder, Color> color = COLORS.get(element);
            if (color != null) {
                attributes(xml, Set.of());
                color.accept(builder, color(xml, xml.getElementText()));
            } else if (element.equals("style")) {
                Map<String, String> style = attributes(xml, Set.of("token", "color", "font"));
                TokenType type = constant(xml, TokenType.class, required(xml, style, "token"));
                Color tokenColor = color(xml, required(xml, style, "color"));
                int fontStyle = style.containsKey("font") ? fontStyle(xml, style.get("font")) : Font.PLAIN;
                empty(xml);
                builder.style(type, new TokenStyle(tokenColor, fontStyle));
            } else if (element.equals("markerColor")) {
                Map<String, String> marker = attributes(xml, Set.of("severity"));
                Severity severity = constant(xml, Severity.class, required(xml, marker, "severity"));
                builder.markerColor(severity, color(xml, xml.getElementText()));
            } else {
                throw error(xml, "unknown element <" + element + "> in <theme>; the colours are " + String.join(", ", COLORS.keySet())
                        + ", and there are <style> and <markerColor>");
            }
        }
        return builder.build();
    }

    private static CodeTheme baseTheme(XMLStreamReader xml, String base) throws EditorConfigException {
        for (CodeTheme theme : CodeThemes.all()) {
            if (key(theme.name()).equals(key(base))) {
                return theme;
            }
        }
        throw error(xml, "no theme is called \"" + base + "\"; the themes are "
                + String.join(", ", CodeThemes.all().stream().map(CodeTheme::name).toList()));
    }

    /** A name without case, spaces or dashes, so that "One Dark", "oneDark" and "one-dark" are the same. */
    private static String key(String name) {
        return name.replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
    }

    private void readSettings(XMLStreamReader xml) throws XMLStreamException, EditorConfigException {
        attributes(xml, Set.of());
        boolean fontRead = false;
        while (nextChild(xml)) {
            String name = xml.getLocalName();
            if (name.equals("font")) {
                if (fontRead) {
                    throw error(xml, "<font> is given twice");
                }
                fontRead = true;
                readFont(xml);
                continue;
            }
            Setting setting = SETTINGS.get(name);
            if (setting == null) {
                throw error(xml, "unknown setting <" + name + ">; the settings are font, " + String.join(", ", SETTINGS.keySet()));
            }
            if (settings.containsKey(name)) {
                throw error(xml, "<" + name + "> is given twice");
            }
            attributes(xml, Set.of());
            String text = xml.getElementText().trim();
            settings.put(name, setting.flag() ? bool(xml, name, text) : number(xml, name, text, setting.min()));
        }
    }

    private void readFont(XMLStreamReader xml) throws XMLStreamException, EditorConfigException {
        Map<String, String> font = attributes(xml, Set.of("family", "size", "style"));
        if (font.isEmpty()) {
            throw error(xml, "<font> needs a family, a size or a style");
        }
        fontFamily = font.get("family");
        fontSize = font.containsKey("size") ? number(xml, "the font size", font.get("size"), 1) : null;
        fontStyle = font.containsKey("style") ? fontStyle(xml, font.get("style")) : null;
        empty(xml);
    }

    // ---- Values ----

    /** A colour as CSS writes it: {@code #RGB}, {@code #RRGGBB}, or {@code #RRGGBBAA} with alpha. */
    private static Color color(XMLStreamReader xml, String text) throws EditorConfigException {
        String value = text.trim();
        String hex = value.startsWith("#") ? value.substring(1) : "";
        if (hex.matches("[0-9A-Fa-f]{3}|[0-9A-Fa-f]{6}|[0-9A-Fa-f]{8}")) {
            if (hex.length() == 3) {
                hex = "" + hex.charAt(0) + hex.charAt(0) + hex.charAt(1) + hex.charAt(1) + hex.charAt(2) + hex.charAt(2);
            }
            int rgb = Integer.parseInt(hex.substring(0, 6), 16);
            int alpha = hex.length() == 8 ? Integer.parseInt(hex.substring(6), 16) : 0xFF;
            return new Color(rgb >> 16, (rgb >> 8) & 0xFF, rgb & 0xFF, alpha);
        }
        throw error(xml, "not a colour: \"" + value + "\"; write it as #RGB, #RRGGBB or #RRGGBBAA");
    }

    /** {@code plain}, {@code bold}, {@code italic}, or {@code bold italic}. */
    private static int fontStyle(XMLStreamReader xml, String text) throws EditorConfigException {
        int style = Font.PLAIN;
        for (String word : text.trim().toLowerCase(Locale.ROOT).split("\\s+")) {
            switch (word) {
                case "plain" -> {
                }
                case "bold" -> style |= Font.BOLD;
                case "italic" -> style |= Font.ITALIC;
                default -> throw error(xml, "not a font style: \"" + text.trim() + "\"; it is plain, bold, italic or bold italic");
            }
        }
        return style;
    }

    private static Boolean bool(XMLStreamReader xml, String name, String text) throws EditorConfigException {
        return switch (text) {
            case "true" -> true;
            case "false" -> false;
            default -> throw error(xml, name + " is true or false, not \"" + text + "\"");
        };
    }

    private static Integer number(XMLStreamReader xml, String name, String text, int min) throws EditorConfigException {
        int value;
        try {
            value = Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            throw error(xml, name + " is a whole number, not \"" + text.trim() + "\"");
        }
        if (value < min) {
            throw error(xml, name + " must be at least " + min + ": " + value);
        }
        return value;
    }

    /** An enum constant by its name, without case, so that {@code doc_comment} is {@link TokenType#DOC_COMMENT}. */
    private static <E extends Enum<E>> E constant(XMLStreamReader xml, Class<E> type, String text) throws EditorConfigException {
        for (E constant : type.getEnumConstants()) {
            if (constant.name().equalsIgnoreCase(text.trim())) {
                return constant;
            }
        }
        throw error(xml, "no " + type.getSimpleName() + " is called \"" + text.trim() + "\"");
    }

    // ---- XML ----

    /**
     * Moves to the next child element of the element the reader is in, and tells whether there
     * is one; at the end of the element it stays on its end tag.
     */
    private static boolean nextChild(XMLStreamReader xml) throws XMLStreamException, EditorConfigException {
        while (true) {
            int event = xml.next();
            switch (event) {
                case XMLStreamConstants.START_ELEMENT:
                    return true;
                case XMLStreamConstants.END_ELEMENT:
                    return false;
                case XMLStreamConstants.CHARACTERS:
                case XMLStreamConstants.CDATA:
                    if (!xml.isWhiteSpace()) {
                        throw error(xml, "text where only elements belong: \"" + xml.getText().trim() + "\"");
                    }
                    break;
                default:
                    break; // comments, processing instructions and white space
            }
        }
    }

    /** The attributes of the element the reader is on, by name; one the element does not take is an error. */
    private static Map<String, String> attributes(XMLStreamReader xml, Set<String> allowed) throws EditorConfigException {
        Map<String, String> attributes = new HashMap<>();
        for (int i = 0; i < xml.getAttributeCount(); i++) {
            String name = xml.getAttributeLocalName(i);
            if (!allowed.contains(name)) {
                String namespace = xml.getAttributeNamespace(i);
                if (namespace != null && !namespace.isEmpty()) {
                    continue; // such as xsi:schemaLocation
                }
                throw error(xml, "<" + xml.getLocalName() + "> has no attribute " + name);
            }
            attributes.put(name, xml.getAttributeValue(i));
        }
        return attributes;
    }

    private static String required(XMLStreamReader xml, Map<String, String> attributes, String name) throws EditorConfigException {
        String value = attributes.get(name);
        if (value == null) {
            throw error(xml, "<" + xml.getLocalName() + "> needs the attribute " + name);
        }
        return value;
    }

    /** Reads to the end of an element that holds nothing but white space. */
    private static void empty(XMLStreamReader xml) throws XMLStreamException, EditorConfigException {
        String element = xml.getLocalName();
        if (nextChild(xml)) {
            throw error(xml, "<" + element + "> takes attributes only, not <" + xml.getLocalName() + ">");
        }
    }

    private static EditorConfigException error(XMLStreamReader xml, String message) {
        return new EditorConfigException(message, xml.getLocation().getLineNumber());
    }

    private static EditorConfigException malformed(XMLStreamException e) {
        // the message of the parser starts with "ParseError at [row,col]:[1,2]", which the line number says better
        String message = String.valueOf(e.getMessage());
        int at = message.indexOf("Message: ");
        int line = e.getLocation() != null ? e.getLocation().getLineNumber() : -1;
        EditorConfigException error = new EditorConfigException(at >= 0 ? message.substring(at + 9) : message, line);
        error.initCause(e);
        return error;
    }
}
