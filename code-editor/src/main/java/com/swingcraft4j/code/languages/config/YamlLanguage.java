package com.swingcraft4j.code.languages.config;

import com.swingcraft4j.code.lexer.RuleLanguage;
import com.swingcraft4j.code.lexer.TokenType;

/**
 * YAML. The lines of a block scalar, the text that follows a {@code |} or a {@code >}, are read
 * as any other lines: what is in them is highlighted as if it were YAML.
 */
public final class YamlLanguage extends RuleLanguage {

    public YamlLanguage() {
        super(builder("yaml", "YAML")
                .extensions("yaml", "yml")
                // a # starts a comment where a blank comes before it, not inside a value
                .pattern(TokenType.COMMENT, "(?<![^\\s])#.*")
                // the start and the end of a document
                .pattern(TokenType.PREPROCESSOR, LineStart.ONLY + "(?:---|\\.\\.\\.)(?=\\s|$)")
                // a key, plain or quoted: what a colon and a blank, or the end of the line, follow
                .pattern(TokenType.ATTRIBUTE, "(?:\"[^\"\\n]*\"|'[^'\\n]*'|[A-Za-z0-9_$./<~][^:#\\n,\\[\\]{}]*?)(?=[ \\t]*:(?:[ \\t]|$))")
                // an anchor and the alias that uses it: &defaults, *defaults
                .pattern(TokenType.ANNOTATION, "(?<![^\\s\\[{,])[&*][A-Za-z0-9_\\-]+")
                // a tag: !!str, !Ref
                .pattern(TokenType.TYPE, "(?<![^\\s\\[{,])!{1,2}[A-Za-z0-9_/.\\-]*")
                // a value that is nothing but one of these words; inside a text they are words like any other
                .pattern(TokenType.LITERAL, "(?<=[:\\-,\\[][ \\t]{0,20})(?i:true|false|null|yes|no|on|off|~)(?=[ \\t]*(?:#|$|[,\\]}]))")
                // a date, with the time it may have: 2024-03-01, 2024-03-01T12:30:00Z
                .pattern(TokenType.NUMBER, "\\d{4}-\\d{2}-\\d{2}(?:[Tt ]\\d{2}:\\d{2}[\\d:.]*(?:Z|[+-]\\d{2}:?\\d{2})?)?(?![\\w-])")
                // the colon after a key and the dash of a list item, which a blank follows; elsewhere they are part of a word
                .pattern(TokenType.OPERATOR, ":(?=[ \\t]|$)")
                .pattern(TokenType.OPERATOR, "(?<![^\\s])-(?=[ \\t]|$)")
                .lineComment("#")
                .string("\"")
                .region(TokenType.STRING, "'", "'", NO_ESCAPE, false)
                // a plain value is one word with what is usual in one: a path, a URL, a version
                .wordChars("-./:@#")
                .operators(":-|>?")
                .punctuation("[]{},"));
    }
}
