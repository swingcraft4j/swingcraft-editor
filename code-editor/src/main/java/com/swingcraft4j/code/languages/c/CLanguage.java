package com.swingcraft4j.code.languages.c;

import com.swingcraft4j.code.format.BraceFormatter;
import com.swingcraft4j.code.format.Formatter;
import com.swingcraft4j.code.lexer.RuleLanguage;
import com.swingcraft4j.code.lexer.TokenType;

/** C. A word in capitals is shown as a constant, which is what a macro usually is. */
public final class CLanguage extends RuleLanguage {

    public CLanguage() {
        super(rules("c", "C").extensions("c", "h"));
    }

    /** The rules shared with C++. */
    static Builder rules(String id, String displayName) {
        return builder(id, displayName)
                .lineComment("//")
                .blockComment("/*", "*/")
                .string("\"")
                .string("'")
                // a directive: #include, #define, # ifdef
                .pattern(TokenType.PREPROCESSOR, "#[ \\t]*[A-Za-z_]+")
                // the header of an include: <stdio.h>
                .pattern(TokenType.STRING, "(?<=include[ \\t]{0,8})<[^>\\n]*>")
                .keywords("auto", "break", "case", "const", "continue", "default", "do", "else", "enum", "extern",
                        "for", "goto", "if", "inline", "register", "restrict", "return", "sizeof", "static",
                        "struct", "switch", "typedef", "union", "volatile", "while")
                .types("bool", "char", "double", "float", "int", "long", "short", "signed", "unsigned", "void",
                        "size_t", "ssize_t", "ptrdiff_t", "wchar_t", "int8_t", "int16_t", "int32_t", "int64_t",
                        "uint8_t", "uint16_t", "uint32_t", "uint64_t")
                .literals("true", "false", "NULL")
                .detectFunctions()
                .capitalizedTypes();
    }

    @Override
    public Formatter formatter() {
        return BraceFormatter.of(this).continuationIndent(2).ternary().pointers().memberArrow().build();
    }
}
