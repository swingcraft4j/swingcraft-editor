package com.swingcraft4j.code.lexer;

/** Token categories a {@link Lexer} can report. A theme maps each one to a style. */
public enum TokenType {
    TEXT,
    KEYWORD,
    LITERAL,
    TYPE,
    FUNCTION,
    CONSTANT,
    IDENTIFIER,
    STRING,
    NUMBER,
    COMMENT,
    DOC_COMMENT,
    ANNOTATION,
    OPERATOR,
    PUNCTUATION,
    TAG,
    ATTRIBUTE,
    PREPROCESSOR,
    /** A placeholder to be filled in later, such as {@code {{name}}} in a template. */
    VARIABLE,
    ERROR
}
