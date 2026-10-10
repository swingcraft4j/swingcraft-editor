package com.swingcraft4j.code.languages.javascript;

import com.swingcraft4j.code.format.BraceFormatter;
import com.swingcraft4j.code.format.Formatter;
import com.swingcraft4j.code.lexer.RuleLanguage;
import com.swingcraft4j.code.lexer.TokenType;

public final class TypeScriptLanguage extends RuleLanguage {

    public TypeScriptLanguage() {
        super(JavaScriptLanguage.rules("typescript", "TypeScript")
                .extensions("ts", "tsx", "mts", "cts")
                .pattern(TokenType.ANNOTATION, "@[A-Za-z_$][\\w$.]*")
                .keywords("abstract", "as", "declare", "enum", "implements", "infer", "interface", "is", "keyof",
                        "namespace", "override", "private", "protected", "public", "readonly", "satisfies", "type")
                .types("any", "bigint", "boolean", "never", "number", "object", "string", "symbol", "unknown"));
    }

    @Override
    public Formatter formatter() {
        return BraceFormatter.of(this).generics().ternary().regexLiterals().jsx().build();
    }
}
