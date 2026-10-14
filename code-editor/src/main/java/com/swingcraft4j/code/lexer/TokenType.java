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
    ERROR,
    /** Text that one version has and the version before it has not, such as a line of a diff that starts with {@code +}. */
    INSERTED,
    /** Text that the version before had and this one has not, such as a line of a diff that starts with {@code -}. */
    DELETED
}
