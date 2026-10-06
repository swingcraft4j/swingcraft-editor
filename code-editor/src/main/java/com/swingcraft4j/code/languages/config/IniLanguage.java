package com.swingcraft4j.code.languages.config;

import com.swingcraft4j.code.lexer.RuleLanguage;
import com.swingcraft4j.code.lexer.TokenType;

/** INI files: sections in brackets, and keys with their values. */
public final class IniLanguage extends RuleLanguage {

    public IniLanguage() {
        super(builder("ini", "INI")
                .extensions("ini", "cfg")
                .lineComment(";")
                .lineComment("#")
                .string("\"")
                .string("'")
                // a section: [database]
                .pattern(TokenType.TAG, LineStart.ONLY + "\\[[^\\]\\n]*\\]")
                // a key is the first thing on its line and ends at =
                .pattern(TokenType.ATTRIBUTE, LineStart.ONLY + "[^\\s=\\[;#][^=\\n]*?(?=[ \\t]*=)")
                .literals("true", "false", "yes", "no", "on", "off")
                .ignoreCase()
                .wordChars(".-")
                .operators("=")
                .punctuation(","));
    }
}
