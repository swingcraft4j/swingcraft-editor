package com.swingcraft4j.code.languages.docker;

import com.swingcraft4j.code.lexer.RuleLanguage;
import com.swingcraft4j.code.lexer.TokenType;

/**
 * The files Docker builds an image from. An instruction is one only as the first word of its
 * line, so the {@code add} of a command that is run is not taken for one.
 */
public final class DockerfileLanguage extends RuleLanguage {

    public DockerfileLanguage() {
        super(builder("dockerfile", "Dockerfile")
                .extensions("dockerfile")
                .lineComment("#")
                .string("\"")
                .string("'")
                .pattern(TokenType.KEYWORD, "(?<![^\\s][ \\t]{0,200})(?i:FROM|RUN|CMD|LABEL|MAINTAINER|EXPOSE|ENV|ADD|COPY"
                        + "|ENTRYPOINT|VOLUME|USER|WORKDIR|ARG|ONBUILD|STOPSIGNAL|HEALTHCHECK|SHELL)\\b")
                // a variable: $HOME, ${HOME:-/root}
                .pattern(TokenType.VARIABLE, "\\$\\{[^}\\n]*\\}|\\$[A-Za-z_]\\w*")
                // an option of an instruction or of a command: --from=build, --no-cache
                .pattern(TokenType.ATTRIBUTE, "(?<![\\w-])--?[A-Za-z][\\w-]*")
                .keywords("AS", "as")
                .wordChars("-./:@")
                .operators("=&|;<>\\")
                .punctuation("[],(){}"));
    }
}
