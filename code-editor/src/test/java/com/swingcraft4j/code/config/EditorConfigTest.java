package com.swingcraft4j.code.config;

import com.swingcraft4j.code.editor.JCodeEditor;
import com.swingcraft4j.code.lexer.TokenType;
import com.swingcraft4j.code.marker.Severity;
import com.swingcraft4j.code.theme.CodeTheme;
import com.swingcraft4j.code.theme.CodeThemes;
import com.swingcraft4j.code.viewer.JCodeViewer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.Color;
import java.awt.Font;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EditorConfigTest {

    private static EditorConfigException failure(String xml) {
        return assertThrows(EditorConfigException.class, () -> EditorConfig.parse(xml));
    }

    @Test
    void adjustsABuiltInTheme() throws Exception {
        EditorConfig config = EditorConfig.parse("""
                <editor>
                    <theme base="one-dark" name="My Dark">
                        <background>#1E1E1E</background>
                        <selectionBackground>#26F</selectionBackground>
                        <style token="keyword" color="#C586C0" font="bold italic"/>
                        <style token="doc_comment" color="#6A9955"/>
                        <markerColor severity="warning">#CCA70080</markerColor>
                    </theme>
                </editor>
                """);
        CodeTheme theme = config.theme().orElseThrow();
        CodeTheme base = CodeThemes.oneDark();
        assertEquals("My Dark", theme.name());
        assertEquals(new Color(0x1E1E1E), theme.background());
        assertEquals(new Color(0x2266FF), theme.selectionBackground(), "#RGB doubles each digit");
        assertEquals(new Color(0xC586C0), theme.style(TokenType.KEYWORD).color());
        assertEquals(Font.BOLD | Font.ITALIC, theme.style(TokenType.KEYWORD).fontStyle());
        assertEquals(Font.PLAIN, theme.style(TokenType.DOC_COMMENT).fontStyle());
        assertEquals(new Color(0xCC, 0xA7, 0x00, 0x80), theme.markerColor(Severity.WARNING), "the last two digits are alpha");
        // what the file does not say is as the base has it
        assertEquals(base.foreground(), theme.foreground());
        assertEquals(base.style(TokenType.STRING), theme.style(TokenType.STRING));
        assertEquals(base.markerColor(Severity.ERROR), theme.markerColor(Severity.ERROR));
        assertTrue(config.settings().isEmpty());
    }

    @Test
    void namesTheBaseThemeAnyWay() throws Exception {
        for (String name : List.of("One Dark", "oneDark", "ONE_DARK")) {
            CodeTheme theme = EditorConfig.parse("<theme base=\"" + name + "\"/>").theme().orElseThrow();
            assertEquals("One Dark", theme.name(), "a theme without a name keeps the name of its base");
        }
        assertEquals("Custom", EditorConfig.parse("<theme/>").theme().orElseThrow().name(), "without a base the theme starts empty");
    }

    @Test
    void appliesTheSettingsToAViewer() throws Exception {
        EditorConfig config = EditorConfig.parse("""
                <?xml version="1.0" encoding="UTF-8"?>
                <!-- the settings of the viewer -->
                <editor>
                    <theme base="Dracula"/>
                    <settings>
                        <lineNumbersVisible>false</lineNumbersVisible>
                        <roundedSelection> false </roundedSelection>
                        <tabSize>2</tabSize>
                        <lineWrap>true</lineWrap>
                        <markerStripeWidth>12</markerStripeWidth>
                        <leftPadding>0</leftPadding>
                        <tabsToSpaces>false</tabsToSpaces>
                    </settings>
                </editor>
                """);
        JCodeViewer viewer = new JCodeViewer("text");
        config.applyTo(viewer);
        assertEquals("Dracula", viewer.getTheme().name());
        assertFalse(viewer.isLineNumbersVisible());
        assertFalse(viewer.isRoundedSelection());
        assertEquals(2, viewer.getTabSize());
        assertTrue(viewer.isLineWrap());
        assertEquals(12, viewer.getMarkerStripeWidth());
        assertEquals(0, viewer.getLeftPadding());
        assertTrue(viewer.isBracketMatching(), "what the file does not say stays as it was");
        assertEquals(List.of("tabSize", "lineNumbersVisible", "lineWrap", "roundedSelection", "markerStripeWidth", "leftPadding", "tabsToSpaces"),
                List.copyOf(config.settings().keySet()), "the settings come in the order they are applied");
    }

    @Test
    void appliesTheSettingsOfAnEditorOnlyToAnEditor() throws Exception {
        EditorConfig config = EditorConfig.parse("""
                <editor><settings>
                    <tabsToSpaces>false</tabsToSpaces>
                    <autoClosePairs>false</autoClosePairs>
                    <highlightCurrentLine>false</highlightCurrentLine>
                    <editable>false</editable>
                    <indentGuides>false</indentGuides>
                </settings></editor>
                """);
        JCodeEditor editor = new JCodeEditor();
        config.applyTo(editor);
        assertFalse(editor.isTabsToSpaces());
        assertFalse(editor.isAutoClosePairs());
        assertFalse(editor.isHighlightCurrentLine());
        assertFalse(editor.isEditable());
        assertFalse(editor.isIndentGuides());
        assertEquals(Map.of("editable", false, "tabsToSpaces", false, "autoClosePairs", false,
                "highlightCurrentLine", false, "indentGuides", false), config.settings());
    }

    @Test
    void setsTheFontOrOnlyItsSize() throws Exception {
        JCodeViewer viewer = new JCodeViewer("text");
        EditorConfig.parse("<editor><settings><font family=\"Monospaced\" size=\"17\" style=\"bold\"/></settings></editor>").applyTo(viewer);
        assertEquals("Monospaced", viewer.getFont().getName());
        assertEquals(17, viewer.getFont().getSize());
        assertTrue(viewer.getFont().isBold());

        EditorConfig.parse("<editor><settings><font size=\"20\"/></settings></editor>").applyTo(viewer);
        assertEquals("Monospaced", viewer.getFont().getName(), "the family stays");
        assertEquals(20, viewer.getFont().getSize());
        assertTrue(viewer.getFont().isBold(), "the style stays");
    }

    @Test
    void readsAFileAndAStream(@TempDir Path dir) throws Exception {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<theme base=\"Nord\" name=\"Nörd\"><foreground>#EEE</foreground></theme>";
        Path file = dir.resolve("theme.xml");
        Files.writeString(file, xml);
        assertEquals("Nörd", EditorConfig.read(file).theme().orElseThrow().name());
        EditorConfig fromStream = EditorConfig.read(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        assertEquals(new Color(0xEEEEEE), fromStream.theme().orElseThrow().foreground());
    }

    @Test
    void ignoresTheAttributesOfASchema() throws Exception {
        EditorConfig config = EditorConfig.parse("""
                <editor xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:noNamespaceSchemaLocation="editor.xsd">
                    <settings><tabSize>8</tabSize></settings>
                </editor>
                """);
        assertEquals(8, config.settings().get("tabSize"));
    }

    @Test
    void saysWhatIsWrongAndOnWhichLine() {
        EditorConfigException unknown = failure("<editor>\n<settings>\n<lineNumbers>true</lineNumbers>\n</settings>\n</editor>");
        assertEquals(3, unknown.getLineNumber());
        assertTrue(unknown.getMessage().startsWith("line 3: unknown setting <lineNumbers>"), unknown.getMessage());
        assertTrue(unknown.getMessage().contains("lineNumbersVisible"), "the message lists the settings there are");

        assertTrue(failure("<editor><settings><tabSize>0</tabSize></settings></editor>").getMessage().contains("tabSize must be at least 1"));
        assertTrue(failure("<editor><settings><tabSize>four</tabSize></settings></editor>").getMessage().contains("whole number"));
        assertTrue(failure("<editor><settings><lineWrap>yes</lineWrap></settings></editor>").getMessage().contains("true or false"));
        assertTrue(failure("<editor><settings><tabSize>2</tabSize><tabSize>4</tabSize></settings></editor>").getMessage().contains("twice"));
        assertTrue(failure("<theme base=\"Solarized Dark\"/>").getMessage().contains("the themes are Light, GitHub Light"));
        assertTrue(failure("<theme><background>red</background></theme>").getMessage().contains("not a colour"));
        assertTrue(failure("<theme><style token=\"keyword\"/></theme>").getMessage().contains("needs the attribute color"));
        assertTrue(failure("<theme><style token=\"keywords\" color=\"#000\"/></theme>").getMessage().contains("no TokenType"));
        assertTrue(failure("<theme><style token=\"keyword\" color=\"#000\" font=\"heavy\"/></theme>").getMessage().contains("not a font style"));
        assertTrue(failure("<theme colour=\"#000\"/>").getMessage().contains("no attribute colour"));
        assertTrue(failure("<settings/>").getMessage().contains("the root must be"));
        assertTrue(failure("<editor>text</editor>").getMessage().contains("text where only elements belong"));

        EditorConfigException malformed = failure("<editor>\n<settings>\n</editor>");
        assertEquals(3, malformed.getLineNumber());
        assertFalse(malformed.getMessage().contains("ParseError"), malformed.getMessage());
    }

    @Test
    void readsNoExternalEntity(@TempDir Path dir) throws Exception {
        Path secret = dir.resolve("secret.txt");
        Files.writeString(secret, "#000000");
        String xml = "<!DOCTYPE theme [<!ENTITY secret SYSTEM \"" + secret.toUri() + "\">]>\n"
                + "<theme><background>&secret;</background></theme>";
        assertThrows(EditorConfigException.class, () -> EditorConfig.parse(xml));
    }
}
