package com.swingcraft4j.code.languages.kotlin;

import com.swingcraft4j.code.format.BraceFormatter;
import com.swingcraft4j.code.format.Formatter;
import com.swingcraft4j.code.lexer.RuleLanguage;
import com.swingcraft4j.code.lexer.TokenType;

/** Kotlin. A string template with a string inside it, such as <code>"${"a"}"</code>, ends at the inner quote. */
public final class KotlinLanguage extends RuleLanguage {

    public KotlinLanguage() {
        super(builder("kotlin", "Kotlin")
                .extensions("kt", "kts")
                .lineComment("//")
                .blockComment("/*", "*/")
                .multilineString("\"\"\"", "\"\"\"")
                .string("\"")
                .string("'")
                // an annotation, with the target it may name: @JvmStatic, @field:Inject
                .pattern(TokenType.ANNOTATION, "@(?:[a-z]+:)?[A-Za-z_][\\w.]*")
                .keywords("abstract", "actual", "annotation", "as", "break", "by", "catch", "class", "companion",
                        "const", "constructor", "continue", "crossinline", "data", "do", "else", "enum", "expect",
                        "external", "final", "finally", "for", "fun", "get", "if", "import", "in", "infix", "init",
                        "inline", "inner", "interface", "internal", "is", "lateinit", "noinline", "object", "open",
                        "operator", "out", "override", "package", "private", "protected", "public", "reified",
                        "return", "sealed", "set", "super", "suspend", "tailrec", "this", "throw", "try",
                        "typealias", "val", "var", "vararg", "when", "where", "while")
                .literals("true", "false", "null")
                .detectFunctions()
                .capitalizedTypes());
    }

    @Override
    public Formatter formatter() {
        return BraceFormatter.of(this).generics().elvis().noSemicolons().build();
    }
}
