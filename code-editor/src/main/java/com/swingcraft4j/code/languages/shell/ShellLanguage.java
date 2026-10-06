package com.swingcraft4j.code.languages.shell;

import com.swingcraft4j.code.lexer.RuleLanguage;
import com.swingcraft4j.code.lexer.TokenType;

/**
 * Shell scripts, as of sh and bash. The text of a here-document is read as any other lines, and
 * a variable inside a string in double quotes has the colour of the string.
 */
public final class ShellLanguage extends RuleLanguage {

    public ShellLanguage() {
        super(builder("shell", "Shell")
                .extensions("sh", "bash", "zsh", "ksh")
                // the line that names the interpreter: #!/bin/sh
                .pattern(TokenType.PREPROCESSOR, "(?<![^\\s][ \\t]{0,200})#!.*")
                // a # starts a comment at the start of a word, not inside one, and not in $#
                .pattern(TokenType.COMMENT, "(?<![^\\s;&|(])#.*")
                // a variable: $HOME, ${HOME:-/root}, $1, $@, $?
                .pattern(TokenType.VARIABLE, "\\$(?:\\{[^}\\n]*\\}|[A-Za-z_]\\w*|[0-9@*#?$!-])")
                // an option of a command, which a blank comes before: -f, --no-cache
                .pattern(TokenType.ATTRIBUTE, "(?<![^\\s])--?[A-Za-z][\\w-]*")
                // the name of a variable that is set: NAME=value
                .pattern(TokenType.ATTRIBUTE, "(?<![\\w./-])[A-Za-z_]\\w*(?=\\+?=)")
                .lineComment("#")
                // a string may run over lines
                .multilineString("\"", "\"")
                .region(TokenType.STRING, "'", "'", NO_ESCAPE, true)
                .multilineString("`", "`")
                .keywords("if", "then", "else", "elif", "fi", "for", "while", "until", "do", "done", "case", "esac",
                        "in", "function", "select", "time", "return", "break", "continue", "exit", "local", "export",
                        "readonly", "declare", "typeset", "unset", "shift", "source", "alias", "set", "eval", "exec",
                        "trap")
                .words(TokenType.FUNCTION, "echo", "printf", "read", "cd", "pwd", "test", "true", "false")
                // a word is a command or a path as much as a name, and a # inside one is a part of it
                .wordChars("-./#")
                .operators("=|&;<>!")
                .punctuation("(){}[]")
                .detectFunctions());
    }
}
