package com.swingcraft4j.code.languages.xml;

import com.swingcraft4j.code.lexer.Language;
import com.swingcraft4j.code.lexer.Lexer;

import java.util.List;

/** XML, and HTML read as markup only: the contents of script and style elements are plain text. */
public final class XmlLanguage implements Language {

    @Override
    public String id() {
        return "xml";
    }

    @Override
    public String displayName() {
        return "XML / HTML";
    }

    @Override
    public List<String> fileExtensions() {
        return List.of("xml", "xsd", "xsl", "xslt", "svg", "fxml", "html", "htm", "xhtml");
    }

    @Override
    public String[] blockComment() {
        return new String[]{"<!--", "-->"};
    }

    @Override
    public Lexer createLexer() {
        return new XmlLexer();
    }
}
