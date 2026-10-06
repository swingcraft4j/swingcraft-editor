package com.swingcraft4j.code.languages.xml;

import com.swingcraft4j.code.lexer.Language;
import com.swingcraft4j.code.lexer.Lexer;

import java.util.List;

/** XML. For HTML, with what is inside its script and style elements, there is {@code HtmlLanguage}. */
public final class XmlLanguage implements Language {

    @Override
    public String id() {
        return "xml";
    }

    @Override
    public String displayName() {
        return "XML";
    }

    @Override
    public List<String> fileExtensions() {
        return List.of("xml", "xsd", "xsl", "xslt", "svg", "fxml");
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
