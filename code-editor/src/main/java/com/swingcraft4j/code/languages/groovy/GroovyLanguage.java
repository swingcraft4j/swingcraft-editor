package com.swingcraft4j.code.languages.groovy;

import com.swingcraft4j.code.format.BraceFormatter;
import com.swingcraft4j.code.format.Formatter;
import com.swingcraft4j.code.lexer.RuleLanguage;
import com.swingcraft4j.code.lexer.TokenType;

/** Groovy, and with it the build files of Gradle. Slashy strings are read as operators and words. */
public final class GroovyLanguage extends RuleLanguage {

    public GroovyLanguage() {
        super(builder("groovy", "Groovy")
                .extensions("groovy", "gradle", "gvy")
                .lineComment("//")
                .blockComment("/*", "*/")
                .multilineString("\"\"\"", "\"\"\"")
                .multilineString("'''", "'''")
                .string("\"")
                .string("'")
                .pattern(TokenType.ANNOTATION, "@[A-Za-z_][\\w.]*")
                .wordChars("$")
                .keywords("abstract", "as", "assert", "break", "case", "catch", "class", "const", "continue", "def",
                        "default", "do", "else", "enum", "extends", "final", "finally", "for", "goto", "if",
                        "implements", "import", "in", "instanceof", "interface", "native", "new", "package",
                        "private", "protected", "public", "return", "static", "strictfp", "super", "switch",
                        "synchronized", "this", "throw", "throws", "trait", "transient", "try", "var", "volatile",
                        "while")
                .types("boolean", "byte", "char", "double", "float", "int", "long", "short", "void")
                .literals("true", "false", "null")
                .detectFunctions()
                .capitalizedTypes());
    }

    @Override
    public Formatter formatter() {
        return BraceFormatter.of(this).continuationIndent(2).generics().ternary().elvis().regexLiterals().noSemicolons()
                .nestedContinuation().build();
    }
}
