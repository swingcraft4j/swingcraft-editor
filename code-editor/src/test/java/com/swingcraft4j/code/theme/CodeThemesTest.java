package com.swingcraft4j.code.theme;

import com.swingcraft4j.code.lexer.TokenType;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CodeThemesTest {

    /** Contrast ratio between two colours as defined by WCAG, from 1 (none) to 21. */
    private static double contrast(Color a, Color b) {
        double la = luminance(a);
        double lb = luminance(b);
        return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
    }

    private static double luminance(Color color) {
        return 0.2126 * channel(color.getRed()) + 0.7152 * channel(color.getGreen()) + 0.0722 * channel(color.getBlue());
    }

    private static double channel(int value) {
        double c = value / 255.0;
        return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }

    @Test
    void listsEveryThemeOnceWithItsName() {
        List<String> names = CodeThemes.all().stream().map(CodeTheme::name).toList();
        assertEquals(List.of("Light", "GitHub Light", "Solarized Light", "Dark", "One Dark", "Dracula", "Monokai", "Nord"), names);
        assertEquals("Dracula", CodeThemes.dracula().toString(), "a theme shows its name in a picker");
    }

    @Test
    void knowsWhichThemesAreDark() {
        assertFalse(CodeTheme.light().isDark());
        assertFalse(CodeThemes.gitHubLight().isDark());
        assertFalse(CodeThemes.solarizedLight().isDark());
        for (CodeTheme theme : List.of(CodeTheme.dark(), CodeThemes.oneDark(), CodeThemes.dracula(), CodeThemes.monokai(), CodeThemes.nord())) {
            assertTrue(theme.isDark(), theme.name());
        }
    }

    @Test
    void everyTokenIsReadableOnEveryBackgroundItCanSitOn() {
        for (CodeTheme theme : CodeThemes.all()) {
            for (TokenType type : TokenType.values()) {
                Color color = theme.style(type).color();
                for (Color background : List.of(theme.background(), theme.currentLineBackground())) {
                    double ratio = contrast(color, background);
                    assertTrue(ratio >= 2.0, theme.name() + ": " + type + " on " + background + " has contrast " + ratio);
                }
                // dim tokens such as comments get fainter still when selected, but must not vanish
                double selected = contrast(color, theme.selectionBackground());
                assertTrue(selected >= 1.5, theme.name() + ": selected " + type + " has contrast " + selected);
            }
            assertTrue(contrast(theme.foreground(), theme.background()) >= 4.5, theme.name() + ": plain text");
            // the marks must be told apart from the page and from each other
            assertNotEquals(theme.background(), theme.selectionBackground(), theme.name());
            assertNotEquals(theme.background(), theme.currentLineBackground(), theme.name());
            assertNotEquals(theme.selectionBackground(), theme.searchBackground(), theme.name());
            assertNotEquals(theme.selectionBackground(), theme.bracketMatchBackground(), theme.name());
        }
    }

    @Test
    void makesTheMarkOfACollapsedFoldFromTheTextColoursUnlessSet() {
        CodeTheme theme = CodeTheme.builder().background(Color.BLACK).foreground(Color.WHITE)
                .gutterBackground(Color.BLACK).gutterBorder(Color.BLACK).build();
        assertNotEquals(theme.background(), theme.foldMarkerBackground(), "shows though the gutter does not");
        assertNotEquals(theme.foldMarkerBackground(), theme.foldMarkerBorder());
        // a copy with another background gets a mark for that background
        assertNotEquals(theme.foldMarkerBackground(), theme.toBuilder().background(Color.DARK_GRAY).build().foldMarkerBackground());
        CodeTheme set = theme.toBuilder().foldMarkerBackground(Color.RED).foldMarkerBorder(Color.BLUE).build();
        assertEquals(Color.RED, set.toBuilder().build().foldMarkerBackground());
        assertEquals(Color.BLUE, set.foldMarkerBorder());
    }

    @Test
    void aCopyKeepsTheNameUnlessGivenAnother() {
        assertEquals("Nord", CodeThemes.nord().toBuilder().background(Color.BLACK).build().name());
        assertEquals("Mine", CodeThemes.nord().toBuilder().name("Mine").build().name());
    }
}
