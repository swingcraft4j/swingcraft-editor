package com.swingcraft4j.code.theme;

import java.awt.Color;
import java.awt.Font;
import java.util.List;

import static com.swingcraft4j.code.lexer.TokenType.*;

/** The themes that come with the library. Each call builds a new instance. */
public final class CodeThemes {

    private CodeThemes() {
    }

    /** Every built-in theme, the light ones first. */
    public static List<CodeTheme> all() {
        return List.of(CodeTheme.light(), gitHubLight(), solarizedLight(),
                CodeTheme.dark(), oneDark(), dracula(), monokai(), nord());
    }

    public static CodeTheme gitHubLight() {
        return base("GitHub Light", 0xFFFFFF, 0x24292F)
                .selectionBackground(new Color(0xBBDFFF))
                .searchBackground(new Color(0xFCE9A0))
                .currentLineBackground(new Color(0xF6F8FA))
                .bracketMatchBackground(new Color(0xC2EFCC))
                .gutterBackground(new Color(0xFFFFFF))
                .gutterForeground(new Color(0x8C959F))
                .gutterBorder(new Color(0xD8DEE4))
                .style(KEYWORD, TokenStyle.of(0xCF222E))
                .style(LITERAL, TokenStyle.of(0x0550AE))
                .style(TYPE, TokenStyle.of(0x953800))
                .style(FUNCTION, TokenStyle.of(0x8250DF))
                .style(CONSTANT, TokenStyle.of(0x0550AE))
                .style(STRING, TokenStyle.of(0x0A3069))
                .style(NUMBER, TokenStyle.of(0x0550AE))
                .style(COMMENT, TokenStyle.of(0x6E7781))
                .style(DOC_COMMENT, TokenStyle.of(0x6E7781))
                .style(ANNOTATION, TokenStyle.of(0x8250DF))
                .style(TAG, TokenStyle.of(0x116329))
                .style(ATTRIBUTE, TokenStyle.of(0x0550AE))
                .style(PREPROCESSOR, TokenStyle.of(0xCF222E))
                .style(VARIABLE, TokenStyle.of(0xBC4C00))
                .style(ERROR, TokenStyle.of(0x82071E))
                .build();
    }

    public static CodeTheme solarizedLight() {
        // the darker of Solarized's two body text tones, for contrast
        return base("Solarized Light", 0xFDF6E3, 0x586E75)
                .selectionBackground(new Color(0xEEE8D5))
                .searchBackground(new Color(0xF2D98C))
                .currentLineBackground(new Color(0xF8F1DE))
                .bracketMatchBackground(new Color(0xBFDED6))
                .gutterBackground(new Color(0xFDF6E3))
                .gutterForeground(new Color(0x93A1A1))
                .gutterBorder(new Color(0xEEE8D5))
                .style(KEYWORD, TokenStyle.of(0x859900))
                .style(LITERAL, TokenStyle.of(0xCB4B16))
                .style(TYPE, TokenStyle.of(0xB58900))
                .style(FUNCTION, TokenStyle.of(0x268BD2))
                .style(CONSTANT, TokenStyle.of(0xCB4B16))
                .style(STRING, TokenStyle.of(0x2AA198))
                .style(NUMBER, TokenStyle.of(0xD33682))
                .style(COMMENT, TokenStyle.italic(0x93A1A1))
                .style(DOC_COMMENT, TokenStyle.italic(0x93A1A1))
                .style(ANNOTATION, TokenStyle.of(0x6C71C4))
                .style(TAG, TokenStyle.of(0x268BD2))
                .style(ATTRIBUTE, TokenStyle.of(0x6C71C4))
                .style(PREPROCESSOR, TokenStyle.of(0xCB4B16))
                .style(VARIABLE, TokenStyle.of(0xCB4B16))
                .style(ERROR, TokenStyle.of(0xDC322F))
                .build();
    }

    public static CodeTheme oneDark() {
        return base("One Dark", 0x282C34, 0xABB2BF)
                .selectionBackground(new Color(0x3E4451))
                .searchBackground(new Color(0x42557B))
                .currentLineBackground(new Color(0x2C313C))
                .bracketMatchBackground(new Color(0x4B6A63))
                .gutterBackground(new Color(0x282C34))
                .gutterForeground(new Color(0x5C6370))
                .gutterBorder(new Color(0x3B4048))
                .style(KEYWORD, TokenStyle.of(0xC678DD))
                .style(LITERAL, TokenStyle.of(0xD19A66))
                .style(TYPE, TokenStyle.of(0xE5C07B))
                .style(FUNCTION, TokenStyle.of(0x61AFEF))
                .style(CONSTANT, TokenStyle.of(0xD19A66))
                .style(STRING, TokenStyle.of(0x98C379))
                .style(NUMBER, TokenStyle.of(0xD19A66))
                .style(COMMENT, TokenStyle.italic(0x5C6370))
                .style(DOC_COMMENT, TokenStyle.italic(0x5C6370))
                .style(ANNOTATION, TokenStyle.of(0xE5C07B))
                .style(OPERATOR, TokenStyle.of(0x56B6C2))
                .style(TAG, TokenStyle.of(0xE06C75))
                .style(ATTRIBUTE, TokenStyle.of(0xD19A66))
                .style(PREPROCESSOR, TokenStyle.of(0xC678DD))
                .style(VARIABLE, TokenStyle.of(0xE06C75))
                .style(ERROR, TokenStyle.of(0xF44747))
                .build();
    }

    public static CodeTheme dracula() {
        return base("Dracula", 0x282A36, 0xF8F8F2)
                .selectionBackground(new Color(0x44475A))
                .searchBackground(new Color(0x7A5E3A))
                .currentLineBackground(new Color(0x313341))
                .bracketMatchBackground(new Color(0x3F6E6B))
                .gutterBackground(new Color(0x282A36))
                .gutterForeground(new Color(0x6272A4))
                .gutterBorder(new Color(0x3A3D4E))
                .style(KEYWORD, TokenStyle.of(0xFF79C6))
                .style(LITERAL, TokenStyle.of(0xBD93F9))
                .style(TYPE, TokenStyle.italic(0x8BE9FD))
                .style(FUNCTION, TokenStyle.of(0x50FA7B))
                .style(CONSTANT, TokenStyle.of(0xBD93F9))
                .style(STRING, TokenStyle.of(0xF1FA8C))
                .style(NUMBER, TokenStyle.of(0xBD93F9))
                .style(COMMENT, TokenStyle.of(0x6272A4))
                .style(DOC_COMMENT, TokenStyle.of(0x6272A4))
                .style(ANNOTATION, TokenStyle.of(0x50FA7B))
                .style(OPERATOR, TokenStyle.of(0xFF79C6))
                .style(TAG, TokenStyle.of(0xFF79C6))
                .style(ATTRIBUTE, TokenStyle.italic(0x50FA7B))
                .style(PREPROCESSOR, TokenStyle.of(0xFF79C6))
                .style(VARIABLE, TokenStyle.of(0xFFB86C))
                .style(ERROR, TokenStyle.of(0xFF5555))
                .build();
    }

    public static CodeTheme monokai() {
        return base("Monokai", 0x272822, 0xF8F8F2)
                .selectionBackground(new Color(0x49483E))
                .searchBackground(new Color(0x6A6338))
                .currentLineBackground(new Color(0x32332B))
                .bracketMatchBackground(new Color(0x4E6B3A))
                .gutterBackground(new Color(0x272822))
                .gutterForeground(new Color(0x90908A))
                .gutterBorder(new Color(0x3B3A32))
                .style(KEYWORD, TokenStyle.of(0xF92672))
                .style(LITERAL, TokenStyle.of(0xAE81FF))
                .style(TYPE, TokenStyle.italic(0x66D9EF))
                .style(FUNCTION, TokenStyle.of(0xA6E22E))
                .style(CONSTANT, TokenStyle.of(0xAE81FF))
                .style(STRING, TokenStyle.of(0xE6DB74))
                .style(NUMBER, TokenStyle.of(0xAE81FF))
                .style(COMMENT, TokenStyle.of(0x75715E))
                .style(DOC_COMMENT, TokenStyle.of(0x75715E))
                .style(ANNOTATION, TokenStyle.of(0xA6E22E))
                .style(OPERATOR, TokenStyle.of(0xF92672))
                .style(TAG, TokenStyle.of(0xF92672))
                .style(ATTRIBUTE, TokenStyle.of(0xA6E22E))
                .style(PREPROCESSOR, TokenStyle.of(0xF92672))
                .style(VARIABLE, TokenStyle.of(0xFD971F))
                .style(ERROR, new TokenStyle(new Color(0xF92672), Font.BOLD))
                .build();
    }

    public static CodeTheme nord() {
        return base("Nord", 0x2E3440, 0xD8DEE9)
                .selectionBackground(new Color(0x434C5E))
                .searchBackground(new Color(0x4F6A76))
                .currentLineBackground(new Color(0x353C4A))
                .bracketMatchBackground(new Color(0x56705F))
                .gutterBackground(new Color(0x2E3440))
                .gutterForeground(new Color(0x616E88))
                .gutterBorder(new Color(0x3B4252))
                .style(KEYWORD, TokenStyle.of(0x81A1C1))
                .style(LITERAL, TokenStyle.of(0x81A1C1))
                .style(TYPE, TokenStyle.of(0x8FBCBB))
                .style(FUNCTION, TokenStyle.of(0x88C0D0))
                .style(CONSTANT, TokenStyle.of(0xB48EAD))
                .style(STRING, TokenStyle.of(0xA3BE8C))
                .style(NUMBER, TokenStyle.of(0xB48EAD))
                .style(COMMENT, TokenStyle.italic(0x616E88))
                .style(DOC_COMMENT, TokenStyle.italic(0x616E88))
                .style(ANNOTATION, TokenStyle.of(0xD08770))
                .style(OPERATOR, TokenStyle.of(0x81A1C1))
                .style(PUNCTUATION, TokenStyle.of(0xECEFF4))
                .style(TAG, TokenStyle.of(0x81A1C1))
                .style(ATTRIBUTE, TokenStyle.of(0x8FBCBB))
                .style(PREPROCESSOR, TokenStyle.of(0x5E81AC))
                .style(VARIABLE, TokenStyle.of(0xEBCB8B))
                .style(ERROR, TokenStyle.of(0xBF616A))
                .build();
    }

    private static CodeTheme.Builder base(String name, int background, int foreground) {
        return CodeTheme.builder().name(name).background(new Color(background)).foreground(new Color(foreground));
    }
}
