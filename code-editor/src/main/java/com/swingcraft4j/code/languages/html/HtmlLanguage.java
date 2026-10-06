package com.swingcraft4j.code.languages.html;

import com.swingcraft4j.code.languages.css.CssLanguage;
import com.swingcraft4j.code.languages.javascript.JavaScriptLanguage;
import com.swingcraft4j.code.lexer.Language;
import com.swingcraft4j.code.lexer.Lexer;

import java.util.List;

/**
 * HTML, with the JavaScript of its {@code script} elements and the CSS of its {@code style}
 * elements highlighted as such. Every script is read as JavaScript, whatever its type says, and
 * the CSS and the JavaScript in the attributes of a tag are read as the strings they stand in.
 */
public final class HtmlLanguage implements Language {

    private final Language script = new JavaScriptLanguage();
    private final Language style = new CssLanguage();

    @Override
    public String id() {
        return "html";
    }

    @Override
    public String displayName() {
        return "HTML";
    }

    @Override
    public List<String> fileExtensions() {
        return List.of("html", "htm", "xhtml");
    }

    @Override
    public String[] blockComment() {
        return new String[]{"<!--", "-->"};
    }

    @Override
    public Lexer createLexer() {
        return new HtmlLexer(script.createLexer(), style.createLexer());
    }
}
