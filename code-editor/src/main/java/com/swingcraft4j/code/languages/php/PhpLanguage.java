package com.swingcraft4j.code.languages.php;

import com.swingcraft4j.code.lexer.RuleLanguage;
import com.swingcraft4j.code.lexer.TokenType;

/** PHP. All of a file is read as PHP: the HTML around the PHP tags is not told apart from it. */
public final class PhpLanguage extends RuleLanguage {

    public PhpLanguage() {
        super(builder("php", "PHP")
                .extensions("php", "phtml")
                .lineComment("//")
                .lineComment("#")
                .blockComment("/*", "*/")
                // a string may run over lines
                .multilineString("\"", "\"")
                .region(TokenType.STRING, "'", "'", '\\', true)
                // the tags that open and close PHP
                .pattern(TokenType.PREPROCESSOR, "<\\?php\\b|<\\?=|\\?>")
                // an attribute: #[Route("/")], which would else be read as a comment
                .pattern(TokenType.ANNOTATION, "#\\[[^\\]\\n]*\\]")
                .wordChars("$")
                .keywords("abstract", "and", "as", "break", "case", "catch", "class", "clone", "const", "continue",
                        "declare", "default", "do", "echo", "else", "elseif", "empty", "enum", "extends", "final",
                        "finally", "fn", "for", "foreach", "function", "global", "if", "implements", "include",
                        "include_once", "instanceof", "interface", "isset", "list", "match", "namespace", "new",
                        "or", "print", "private", "protected", "public", "readonly", "require", "require_once",
                        "return", "static", "switch", "throw", "trait", "try", "unset", "use", "var", "while",
                        "xor", "yield", "$this", "self", "parent")
                .types("array", "bool", "callable", "float", "int", "iterable", "mixed", "never", "object",
                        "string", "void")
                .literals("true", "false", "null", "TRUE", "FALSE", "NULL")
                .detectFunctions()
                .capitalizedTypes());
    }
}
