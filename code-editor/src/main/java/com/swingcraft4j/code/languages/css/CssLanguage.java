package com.swingcraft4j.code.languages.css;

import com.swingcraft4j.code.lexer.RuleLanguage;
import com.swingcraft4j.code.lexer.TokenType;

/**
 * CSS. A property is told from a selector by the colon and the value that follow it on the
 * same line, so a declaration whose value starts on the next line is read as a selector.
 */
public final class CssLanguage extends RuleLanguage {

    public CssLanguage() {
        super(builder("css", "CSS")
                .extensions("css")
                .blockComment("/*", "*/")
                .string("\"")
                .string("'")
                .wordChars("-")
                // an at-rule: @media, @import
                .pattern(TokenType.KEYWORD, "@[\\w-]+")
                .pattern(TokenType.KEYWORD, "!important\\b")
                // a colour: #fff, #1e1f22
                .pattern(TokenType.NUMBER, "#[0-9a-fA-F]{3,8}\\b")
                // a custom property, where it is declared and where it is used
                .pattern(TokenType.VARIABLE, "--[\\w-]+")
                // a name followed by a colon and a value that ends on the line; not a part of a selector, as
                // the item of ".item:hover," is, which has a dot before it and a comma at its end
                .pattern(TokenType.ATTRIBUTE, "(?<![.#:\\w-])[\\w-]+(?=[ \\t]*:[^{;}]*(?:[;}]|(?<=[^,\\s{])[ \\t]*$))")
                .literals("inherit", "initial", "unset", "revert", "none", "auto")
                .detectFunctions()
                .operators(":>+~*=")
                .punctuation("{}()[];,."));
    }
}
