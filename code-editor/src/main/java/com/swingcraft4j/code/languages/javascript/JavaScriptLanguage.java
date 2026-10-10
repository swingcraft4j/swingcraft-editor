package com.swingcraft4j.code.languages.javascript;

import com.swingcraft4j.code.format.BraceFormatter;
import com.swingcraft4j.code.format.Formatter;
import com.swingcraft4j.code.lexer.RuleLanguage;

/** JavaScript. Regular expression literals are not recognised and are read as operators and words. */
public final class JavaScriptLanguage extends RuleLanguage {

    public JavaScriptLanguage() {
        super(rules("javascript", "JavaScript").extensions("js", "mjs", "cjs", "jsx"));
    }

    /** The rules shared with TypeScript. */
    static Builder rules(String id, String displayName) {
        return builder(id, displayName)
                .lineComment("//")
                .blockComment("/*", "*/")
                .string("\"")
                .string("'")
                .multilineString("`", "`")
                .wordChars("$")
                .keywords("async", "await", "break", "case", "catch", "class", "const", "continue", "debugger",
                        "default", "delete", "do", "else", "export", "extends", "finally", "for", "from",
                        "function", "get", "if", "import", "in", "instanceof", "let", "new", "of", "return",
                        "set", "static", "super", "switch", "this", "throw", "try", "typeof", "var", "void",
                        "while", "with", "yield")
                .literals("true", "false", "null", "undefined", "NaN", "Infinity")
                .detectFunctions()
                .capitalizedTypes();
    }

    @Override
    public Formatter formatter() {
        return BraceFormatter.of(this).ternary().regexLiterals().jsx().build();
    }
}
