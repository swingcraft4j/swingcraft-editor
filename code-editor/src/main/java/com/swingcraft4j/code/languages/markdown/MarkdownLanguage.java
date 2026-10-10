package com.swingcraft4j.code.languages.markdown;

import com.swingcraft4j.code.format.Formatter;
import com.swingcraft4j.code.format.MarkdownTableFormatter;
import com.swingcraft4j.code.lexer.Language;
import com.swingcraft4j.code.lexer.Lexer;

import java.util.List;

/**
 * Markdown. The code of a fenced block is highlighted in the language the fence names, where
 * that is one of the installed languages. What needs more than its own line to be known is not
 * recognised: a heading underlined on the next line, a code block made by indentation, and
 * emphasis that runs over a line break.
 */
public final class MarkdownLanguage implements Language {

    @Override
    public String id() {
        return "markdown";
    }

    @Override
    public String displayName() {
        return "Markdown";
    }

    @Override
    public List<String> fileExtensions() {
        return List.of("md", "markdown");
    }

    @Override
    public String[] blockComment() {
        return new String[]{"<!--", "-->"};
    }

    /** Formatting aligns the tables, and changes nothing else. */
    @Override
    public Formatter formatter() {
        return new MarkdownTableFormatter();
    }

    @Override
    public Lexer createLexer() {
        return new MarkdownLexer();
    }
}
