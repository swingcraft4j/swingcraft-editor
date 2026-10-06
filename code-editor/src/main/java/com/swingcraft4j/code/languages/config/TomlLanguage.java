package com.swingcraft4j.code.languages.config;

import com.swingcraft4j.code.lexer.RuleLanguage;
import com.swingcraft4j.code.lexer.TokenType;

/** TOML. A date is read as numbers with operators between them. */
public final class TomlLanguage extends RuleLanguage {

    public TomlLanguage() {
        super(builder("toml", "TOML")
                .extensions("toml")
                .lineComment("#")
                .multilineString("\"\"\"", "\"\"\"")
                .region(TokenType.STRING, "'''", "'''", NO_ESCAPE, true)
                .string("\"")
                .region(TokenType.STRING, "'", "'", NO_ESCAPE, false)
                // a table and an array of tables: [server], [[products]]; an array of values is no first thing on a line
                .pattern(TokenType.TAG, LineStart.ONLY + "\\[\\[?[^\\[\\]\\n]*\\]\\]?(?=[ \\t]*(?:#|$))")
                // a key, bare, dotted or quoted, where a = follows
                .pattern(TokenType.ATTRIBUTE, "[A-Za-z0-9_\\-]+(?:[ \\t]*\\.[ \\t]*[A-Za-z0-9_\\-]+)*(?=[ \\t]*=)")
                .literals("true", "false", "inf", "nan")
                .operators("=+-:")
                .punctuation("[]{},."));
    }
}
