package com.swingcraft4j.code.languages.python;

import com.swingcraft4j.code.lexer.RuleLanguage;
import com.swingcraft4j.code.lexer.TokenType;

public final class PythonLanguage extends RuleLanguage {

    public PythonLanguage() {
        super(builder("python", "Python")
                .extensions("py", "pyw", "pyi")
                .lineComment("#")
                .multilineString("\"\"\"", "\"\"\"")
                .multilineString("'''", "'''")
                .string("\"")
                .string("'")
                .pattern(TokenType.ANNOTATION, "@[A-Za-z_][\\w.]*")
                .keywords("and", "as", "assert", "async", "await", "break", "case", "class", "continue", "def",
                        "del", "elif", "else", "except", "finally", "for", "from", "global", "if", "import", "in",
                        "is", "lambda", "match", "nonlocal", "not", "or", "pass", "raise", "return", "try",
                        "while", "with", "yield")
                .literals("True", "False", "None")
                .types("bool", "bytes", "complex", "dict", "float", "frozenset", "int", "list", "object", "set",
                        "str", "tuple")
                .words(TokenType.CONSTANT, "self", "cls")
                .operators("+-*/%=<>!&|^~@:")
                .detectFunctions()
                .capitalizedTypes());
    }
}
