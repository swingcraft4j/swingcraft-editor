package com.swingcraft4j.code.languages.json;

import com.swingcraft4j.code.format.Formatter;
import com.swingcraft4j.code.format.JsonFormatter;
import com.swingcraft4j.code.lexer.RuleLanguage;
import com.swingcraft4j.code.lexer.TokenType;

public final class JsonLanguage extends RuleLanguage {

    public JsonLanguage() {
        super(builder("json", "JSON")
                .extensions("json", "jsonc", "json5")
                // a string followed by a colon is a member name; written without a choice inside
                // a repetition, which overflows the stack on a string of a few thousand chars
                .pattern(TokenType.ATTRIBUTE, "\"[^\"\\\\]*+(?:\\\\.[^\"\\\\]*+)*+\"(?=\\s*:)")
                .string("\"")
                .lineComment("//")
                .blockComment("/*", "*/")
                .literals("true", "false", "null")
                .operators(":")
                .punctuation("{}[],"));
    }

    @Override
    public Formatter formatter() {
        return new JsonFormatter();
    }
}
