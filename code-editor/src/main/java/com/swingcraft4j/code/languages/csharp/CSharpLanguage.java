package com.swingcraft4j.code.languages.csharp;

import com.swingcraft4j.code.format.BraceFormatter;
import com.swingcraft4j.code.format.Formatter;
import com.swingcraft4j.code.lexer.RuleLanguage;
import com.swingcraft4j.code.lexer.TokenType;

/**
 * C#. A capitalised word is not shown as a type, as it is in most of the other languages: the
 * methods and the properties of C# are capitalised too.
 */
public final class CSharpLanguage extends RuleLanguage {

    public CSharpLanguage() {
        super(builder("csharp", "C#")
                .extensions("cs")
                .region(TokenType.DOC_COMMENT, "///", null, NO_ESCAPE, false)
                .lineComment("//")
                .blockComment("/*", "*/")
                // a verbatim string runs over lines, and a quote in it is written twice
                .region(TokenType.STRING, "@\"", "\"", NO_ESCAPE, true)
                .region(TokenType.STRING, "$\"", "\"", '\\', false)
                .string("\"")
                .string("'")
                // a directive: #region, #if
                .pattern(TokenType.PREPROCESSOR, "#[ \\t]*[a-z]+")
                .keywords("abstract", "as", "async", "await", "base", "break", "case", "catch", "checked", "class",
                        "const", "continue", "default", "delegate", "do", "else", "enum", "event", "explicit",
                        "extern", "finally", "fixed", "for", "foreach", "get", "goto", "if", "implicit", "in",
                        "init", "interface", "internal", "is", "lock", "namespace", "new", "operator", "out",
                        "override", "params", "partial", "private", "protected", "public", "readonly", "record",
                        "ref", "required", "return", "sealed", "set", "sizeof", "stackalloc", "static", "struct",
                        "switch", "this", "throw", "try", "typeof", "unchecked", "unsafe", "using", "var",
                        "virtual", "volatile", "when", "where", "while", "yield")
                .types("bool", "byte", "char", "decimal", "double", "dynamic", "float", "int", "long", "nint",
                        "nuint", "object", "sbyte", "short", "string", "uint", "ulong", "ushort", "void")
                .literals("true", "false", "null")
                .detectFunctions());
    }

    @Override
    public Formatter formatter() {
        return BraceFormatter.of(this).generics().ternary().memberArrow().build();
    }
}
