package com.swingcraft4j.code.theme;

import com.swingcraft4j.code.lexer.TokenType;
import com.swingcraft4j.code.marker.Severity;

import javax.swing.UIManager;
import java.awt.Color;
import java.util.EnumMap;
import java.util.Map;

import static com.swingcraft4j.code.lexer.TokenType.*;

/** Immutable set of colours and token styles. Start from a built-in theme and adjust it with {@link #toBuilder()}. */
public final class CodeTheme {

    private final String name;
    private final Color background;
    private final Color foreground;
    private final Color selectionBackground;
    private final Color searchBackground;
    private final Color currentLineBackground;
    private final Color bracketMatchBackground;
    private final Color gutterBackground;
    private final Color gutterForeground;
    private final Color gutterBorder;
    private final Color foldMarkerBackground;
    private final Color foldMarkerBorder;
    private final Color explicitFoldMarkerBackground;
    private final Color explicitFoldMarkerBorder;
    private final Color indentGuide;
    private final Color explicitIndentGuide;
    /** The colour for each {@link Severity}, by ordinal. */
    private final Color[] markerColors;
    private final Color[] explicitMarkerColors;
    private final Map<TokenType, TokenStyle> explicitStyles;
    private final TokenStyle[] styles;

    private CodeTheme(Builder builder) {
        name = builder.name;
        background = builder.background;
        foreground = builder.foreground;
        selectionBackground = builder.selectionBackground;
        searchBackground = builder.searchBackground;
        currentLineBackground = builder.currentLineBackground;
        bracketMatchBackground = builder.bracketMatchBackground;
        gutterBackground = builder.gutterBackground;
        gutterForeground = builder.gutterForeground;
        gutterBorder = builder.gutterBorder;
        // The mark of a collapsed fold, unless a theme sets it, is made from the colours of the
        // text, so that it shows whatever the colours of the gutter are.
        explicitFoldMarkerBackground = builder.foldMarkerBackground;
        explicitFoldMarkerBorder = builder.foldMarkerBorder;
        foldMarkerBackground = explicitFoldMarkerBackground != null ? explicitFoldMarkerBackground : mix(background, foreground, 0.12f);
        foldMarkerBorder = explicitFoldMarkerBorder != null ? explicitFoldMarkerBorder : mix(background, foreground, 0.25f);
        // The indent guides are faint: they are there to follow with the eye, not to be read.
        explicitIndentGuide = builder.indentGuide;
        indentGuide = explicitIndentGuide != null ? explicitIndentGuide : mix(background, foreground, 0.14f);
        // Marker colours a theme does not set are chosen to stand out on its background.
        boolean dark = isDark();
        markerColors = new Color[]{
                builder.errorColor != null ? builder.errorColor : new Color(dark ? 0xF75464 : 0xD1242F),
                builder.warningColor != null ? builder.warningColor : new Color(dark ? 0xD6A635 : 0xB07D00),
                builder.infoColor != null ? builder.infoColor : new Color(dark ? 0x56A8F5 : 0x0969DA)};
        explicitMarkerColors = new Color[]{builder.errorColor, builder.warningColor, builder.infoColor};
        explicitStyles = new EnumMap<>(builder.styles);
        // Types without a style of their own share one instance, which lets painters merge them.
        TokenStyle plain = TokenStyle.of(foreground);
        TokenType[] types = TokenType.values();
        styles = new TokenStyle[types.length];
        for (TokenType type : types) {
            styles[type.ordinal()] = builder.styles.getOrDefault(type, plain);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static CodeTheme light() {
        return new Builder()
                .name("Light")
                .background(new Color(0xFFFFFF))
                .foreground(new Color(0x1F2328))
                .selectionBackground(new Color(0xADD6FF))
                .searchBackground(new Color(0xFCE8A6))
                .currentLineBackground(new Color(0xF5F8FE))
                .bracketMatchBackground(new Color(0x93D9D9))
                .gutterBackground(new Color(0xF7F8FA))
                .gutterForeground(new Color(0x8C959F))
                .gutterBorder(new Color(0xE5E7EB))
                .style(KEYWORD, TokenStyle.of(0x0033B3))
                .style(LITERAL, TokenStyle.of(0x0033B3))
                .style(TYPE, TokenStyle.of(0x00627A))
                .style(FUNCTION, TokenStyle.of(0x7A3E9D))
                .style(CONSTANT, TokenStyle.of(0x871094))
                .style(STRING, TokenStyle.of(0x067D17))
                .style(NUMBER, TokenStyle.of(0x1750EB))
                .style(COMMENT, TokenStyle.italic(0x8C8C8C))
                .style(DOC_COMMENT, TokenStyle.italic(0x5F826B))
                .style(ANNOTATION, TokenStyle.of(0x9E880D))
                .style(TAG, TokenStyle.of(0x0033B3))
                .style(ATTRIBUTE, TokenStyle.of(0x174AD4))
                .style(PREPROCESSOR, TokenStyle.of(0x9E880D))
                .style(VARIABLE, TokenStyle.of(0xC4570F))
                .style(ERROR, TokenStyle.of(0xD1242F))
                .build();
    }

    public static CodeTheme dark() {
        return new Builder()
                .name("Dark")
                .background(new Color(0x1E1F22))
                .foreground(new Color(0xBCBEC4))
                .selectionBackground(new Color(0x214283))
                .searchBackground(new Color(0x5C4A14))
                .currentLineBackground(new Color(0x26282E))
                .bracketMatchBackground(new Color(0x3B5F5B))
                .gutterBackground(new Color(0x1E1F22))
                .gutterForeground(new Color(0x6F737A))
                .gutterBorder(new Color(0x313438))
                .style(KEYWORD, TokenStyle.of(0xCF8E6D))
                .style(LITERAL, TokenStyle.of(0xCF8E6D))
                .style(TYPE, TokenStyle.of(0x6FAFBD))
                .style(FUNCTION, TokenStyle.of(0x56A8F5))
                .style(CONSTANT, TokenStyle.of(0xC77DBB))
                .style(STRING, TokenStyle.of(0x6AAB73))
                .style(NUMBER, TokenStyle.of(0x2AACB8))
                .style(COMMENT, TokenStyle.italic(0x7A7E85))
                .style(DOC_COMMENT, TokenStyle.italic(0x5F826B))
                .style(ANNOTATION, TokenStyle.of(0xB3AE60))
                .style(TAG, TokenStyle.of(0xD5B778))
                .style(ATTRIBUTE, TokenStyle.of(0xC77DBB))
                .style(PREPROCESSOR, TokenStyle.of(0xB3AE60))
                .style(VARIABLE, TokenStyle.of(0xE8A46A))
                .style(ERROR, TokenStyle.of(0xF75464))
                .build();
    }

    /** The dark theme when the current Look and Feel has a dark text background, else the light one. */
    public static CodeTheme forLookAndFeel() {
        Color color = UIManager.getColor("TextArea.background");
        if (color == null) {
            return light();
        }
        double luminance = (0.299 * color.getRed() + 0.587 * color.getGreen() + 0.114 * color.getBlue()) / 255;
        return luminance < 0.5 ? dark() : light();
    }

    /** The name shown to users, as in a theme picker. */
    public String name() {
        return name;
    }

    /** Whether the background is dark, which tells an application how to style what surrounds the text. */
    public boolean isDark() {
        return 0.299 * background.getRed() + 0.587 * background.getGreen() + 0.114 * background.getBlue() < 128;
    }

    @Override
    public String toString() {
        return name;
    }

    public Color background() {
        return background;
    }

    public Color foreground() {
        return foreground;
    }

    public Color selectionBackground() {
        return selectionBackground;
    }

    /** Background of the matches of the current search. */
    public Color searchBackground() {
        return searchBackground;
    }

    /** Background of the line holding the caret in an editor. */
    public Color currentLineBackground() {
        return currentLineBackground;
    }

    /** Background of a bracket next to the caret and of its partner. */
    public Color bracketMatchBackground() {
        return bracketMatchBackground;
    }

    public Color gutterBackground() {
        return gutterBackground;
    }

    public Color gutterForeground() {
        return gutterForeground;
    }

    public Color gutterBorder() {
        return gutterBorder;
    }

    /** Background of the mark that stands for the hidden lines of a collapsed fold. */
    public Color foldMarkerBackground() {
        return foldMarkerBackground;
    }

    /** The line around the mark of a collapsed fold. */
    public Color foldMarkerBorder() {
        return foldMarkerBorder;
    }

    /** The lines that run down from a line to where the lines indented under it end. */
    public Color indentGuide() {
        return indentGuide;
    }

    /** The colour that lies the given part of the way from one colour to another. */
    private static Color mix(Color from, Color to, float part) {
        return new Color(
                Math.round(from.getRed() + (to.getRed() - from.getRed()) * part),
                Math.round(from.getGreen() + (to.getGreen() - from.getGreen()) * part),
                Math.round(from.getBlue() + (to.getBlue() - from.getBlue()) * part));
    }

    public TokenStyle style(TokenType type) {
        return styles[type.ordinal()];
    }

    /** The colour of the wavy line and the gutter mark of a marker of the given severity. */
    public Color markerColor(Severity severity) {
        return markerColors[severity.ordinal()];
    }

    public Builder toBuilder() {
        Builder builder = new Builder()
                .name(name)
                .background(background)
                .foreground(foreground)
                .selectionBackground(selectionBackground)
                .searchBackground(searchBackground)
                .currentLineBackground(currentLineBackground)
                .bracketMatchBackground(bracketMatchBackground)
                .gutterBackground(gutterBackground)
                .gutterForeground(gutterForeground)
                .gutterBorder(gutterBorder)
                .foldMarkerBackground(explicitFoldMarkerBackground)
                .foldMarkerBorder(explicitFoldMarkerBorder)
                .indentGuide(explicitIndentGuide)
                .markerColor(Severity.ERROR, explicitMarkerColors[0])
                .markerColor(Severity.WARNING, explicitMarkerColors[1])
                .markerColor(Severity.INFO, explicitMarkerColors[2]);
        builder.styles.putAll(explicitStyles);
        return builder;
    }

    public static final class Builder {

        private String name = "Custom";
        private Color background = Color.WHITE;
        private Color foreground = Color.BLACK;
        private Color selectionBackground = new Color(0xADD6FF);
        private Color searchBackground = new Color(0xFCE8A6);
        private Color currentLineBackground = new Color(0xF5F8FE);
        private Color bracketMatchBackground = new Color(0x93D9D9);
        private Color gutterBackground = Color.WHITE;
        private Color gutterForeground = Color.GRAY;
        private Color gutterBorder = Color.LIGHT_GRAY;
        private Color foldMarkerBackground;
        private Color foldMarkerBorder;
        private Color indentGuide;
        private Color errorColor;
        private Color warningColor;
        private Color infoColor;
        private final Map<TokenType, TokenStyle> styles = new EnumMap<>(TokenType.class);

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder background(Color color) {
            background = color;
            return this;
        }

        /** Also the colour of every token type that has no style of its own. */
        public Builder foreground(Color color) {
            foreground = color;
            return this;
        }

        public Builder selectionBackground(Color color) {
            selectionBackground = color;
            return this;
        }

        public Builder searchBackground(Color color) {
            searchBackground = color;
            return this;
        }

        public Builder currentLineBackground(Color color) {
            currentLineBackground = color;
            return this;
        }

        public Builder bracketMatchBackground(Color color) {
            bracketMatchBackground = color;
            return this;
        }

        public Builder gutterBackground(Color color) {
            gutterBackground = color;
            return this;
        }

        public Builder gutterForeground(Color color) {
            gutterForeground = color;
            return this;
        }

        public Builder gutterBorder(Color color) {
            gutterBorder = color;
            return this;
        }

        /**
         * Sets the background of the mark of a collapsed fold; null goes back to the one made
         * from the background and the foreground.
         */
        public Builder foldMarkerBackground(Color color) {
            foldMarkerBackground = color;
            return this;
        }

        /** Sets the line around the mark of a collapsed fold; null goes back to the one made from the background and the foreground. */
        public Builder foldMarkerBorder(Color color) {
            foldMarkerBorder = color;
            return this;
        }

        /** Sets the colour of the indent guides; null goes back to the one made from the background and the foreground. */
        public Builder indentGuide(Color color) {
            indentGuide = color;
            return this;
        }

        public Builder style(TokenType type, TokenStyle style) {
            styles.put(type, style);
            return this;
        }

        /** Sets the colour of markers of one severity; null goes back to the one chosen for the background. */
        public Builder markerColor(Severity severity, Color color) {
            switch (severity) {
                case ERROR -> errorColor = color;
                case WARNING -> warningColor = color;
                case INFO -> infoColor = color;
            }
            return this;
        }

        public CodeTheme build() {
            return new CodeTheme(this);
        }
    }
}
