package com.swingcraft4j.code.languages.config;

import com.swingcraft4j.code.lexer.RuleLanguage;
import com.swingcraft4j.code.lexer.TokenType;

/**
 * The {@code .env} files that hold the settings of an application as variables of the
 * environment. A {@code #} starts a comment where a blank comes before it, not inside a value.
 */
public final class EnvLanguage extends RuleLanguage {

    public EnvLanguage() {
        super(builder("env", "Env")
                .extensions("env")
                .pattern(TokenType.COMMENT, "(?<![^\\s])#.*")
                // the name of a variable, where a = follows
                .pattern(TokenType.ATTRIBUTE, "[A-Za-z_][A-Za-z0-9_.]*(?=[ \\t]*=)")
                // another variable used in a value: $HOME, ${HOME}
                .pattern(TokenType.VARIABLE, "\\$\\{[^}\\n]*\\}|\\$[A-Za-z_]\\w*")
                .lineComment("#")
                .string("\"")
                .region(TokenType.STRING, "'", "'", NO_ESCAPE, false)
                .keywords("export")
                .literals("true", "false")
                // a # inside a value is a part of it
                .wordChars("#.-/:@")
                .operators("=")
                .punctuation(""));
    }
}
