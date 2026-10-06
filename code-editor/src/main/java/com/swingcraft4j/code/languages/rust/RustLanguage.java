package com.swingcraft4j.code.languages.rust;

import com.swingcraft4j.code.lexer.RuleLanguage;
import com.swingcraft4j.code.lexer.TokenType;

/** Rust. Raw string literals are read as ordinary strings. */
public final class RustLanguage extends RuleLanguage {

    public RustLanguage() {
        super(builder("rust", "Rust")
                .extensions("rs")
                .region(TokenType.DOC_COMMENT, "///", null, NO_ESCAPE, false)
                .region(TokenType.DOC_COMMENT, "//!", null, NO_ESCAPE, false)
                .lineComment("//")
                .blockComment("/*", "*/")
                // a string may run over lines
                .multilineString("\"", "\"")
                // a char, also one written with an escape: told from a lifetime by its closing quote
                .pattern(TokenType.STRING, "'(?:[^'\\\\\\n]|\\\\[^\\n]{1,10}?)'")
                // a lifetime: 'a, 'static
                .pattern(TokenType.ANNOTATION, "'[A-Za-z_]\\w*")
                // an attribute: #[derive(Debug)], #![allow(unused)]
                .pattern(TokenType.ANNOTATION, "#!?\\[[^\\]\\n]*\\]")
                // a macro that is called: println!, vec!
                .pattern(TokenType.FUNCTION, "[A-Za-z_]\\w*!(?=[ \\t]*[(\\[{])")
                .keywords("as", "async", "await", "break", "const", "continue", "crate", "dyn", "else", "enum",
                        "extern", "fn", "for", "if", "impl", "in", "let", "loop", "match", "mod", "move", "mut",
                        "pub", "ref", "return", "self", "static", "struct", "super", "trait", "type", "union",
                        "unsafe", "use", "where", "while")
                .types("bool", "char", "f32", "f64", "i8", "i16", "i32", "i64", "i128", "isize", "str", "u8",
                        "u16", "u32", "u64", "u128", "usize")
                .literals("true", "false")
                .detectFunctions()
                .capitalizedTypes());
    }
}
