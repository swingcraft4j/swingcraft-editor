package com.swingcraft4j.code.languages.java;

import com.swingcraft4j.code.lexer.Language;
import com.swingcraft4j.code.lexer.Lexer;

import java.util.Collection;
import java.util.List;

public final class JavaLanguage implements Language {

    @Override
    public String id() {
        return "java";
    }

    @Override
    public String displayName() {
        return "Java";
    }

    @Override
    public List<String> fileExtensions() {
        return List.of("java");
    }

    @Override
    public Collection<String> keywords() {
        return JavaLexer.reservedWords();
    }

    @Override
    public String lineComment() {
        return "//";
    }

    @Override
    public String[] blockComment() {
        return new String[]{"/*", "*/"};
    }

    @Override
    public Lexer createLexer() {
        return new JavaLexer();
    }
}
