package com.swingcraft4j.code.languages.javascript;

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
}
