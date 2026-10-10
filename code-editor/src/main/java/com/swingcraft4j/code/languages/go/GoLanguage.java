package com.swingcraft4j.code.languages.go;

import com.swingcraft4j.code.format.BraceFormatter;
import com.swingcraft4j.code.format.Formatter;
import com.swingcraft4j.code.lexer.RuleLanguage;
import com.swingcraft4j.code.lexer.TokenType;

/**
 * Go. A capitalised word is not shown as a type, as it is in most of the other languages: in Go
 * it is any name that is exported, a function as much as a type.
 */
public final class GoLanguage extends RuleLanguage {

    public GoLanguage() {
        super(builder("go", "Go")
                .extensions("go")
                .lineComment("//")
                .blockComment("/*", "*/")
                .string("\"")
                .string("'")
                // a raw string runs over lines and has no escapes
                .region(TokenType.STRING, "`", "`", NO_ESCAPE, true)
                .keywords("break", "case", "chan", "const", "continue", "default", "defer", "else", "fallthrough",
                        "for", "func", "go", "goto", "if", "import", "interface", "map", "package", "range",
                        "return", "select", "struct", "switch", "type", "var")
                .types("any", "bool", "byte", "comparable", "complex64", "complex128", "error", "float32",
                        "float64", "int", "int8", "int16", "int32", "int64", "rune", "string", "uint", "uint8",
                        "uint16", "uint32", "uint64", "uintptr")
                .literals("true", "false", "nil", "iota")
                .detectFunctions());
    }

    @Override
    public Formatter formatter() {
        return BraceFormatter.of(this).pointers().noSemicolons().caseAtSwitchLevel().braceInitializers()
                .sliceColons().tabs().keepAlignment().maxBlankLines(1).build();
    }
}
