package com.swingcraft4j.code.languages.config;

import com.swingcraft4j.code.lexer.RuleLanguage;
import com.swingcraft4j.code.lexer.TokenType;

/**
 * The properties files of Java. All that follows the separator of a key is its value, also a
 * {@code #}, which starts a comment only at the start of a line.
 */
public final class PropertiesLanguage extends RuleLanguage {

    public PropertiesLanguage() {
        super(builder("properties", "Properties")
                .extensions("properties")
                .pattern(TokenType.COMMENT, LineStart.ONLY + "[#!].*")
                // a key is the first thing on its line and ends at = or :
                .pattern(TokenType.ATTRIBUTE, LineStart.ONLY + "[^\\s=:#!][^\\s=:]*(?=[ \\t]*[=:])")
                // the value: the rest of the line after the separator
                .pattern(TokenType.STRING, "(?<=[=:][ \\t]{0,200})[^\\s].*")
                .lineComment("#")
                .wordChars(".-")
                .operators("=:")
                .punctuation(""));
    }
}
