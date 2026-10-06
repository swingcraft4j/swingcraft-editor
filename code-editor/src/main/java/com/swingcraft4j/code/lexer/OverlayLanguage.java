package com.swingcraft4j.code.lexer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;

/**
 * A language with extra syntax laid over another one: wherever a pattern matches, its token
 * replaces whatever the base language made of that text, even in the middle of a string or
 * a comment. This is how placeholders such as {@code {{name}}} are shown in documents that
 * are otherwise JSON, SQL or anything else:
 * <pre>{@code
 * Language json = Languages.byId("json").orElseThrow();
 * Language template = OverlayLanguage.over(json)
 *         .pattern(TokenType.VARIABLE, "\\{\\{[^{}]*\\}\\}")
 *         .build();
 * }</pre>
 * A match lies within one line. Where two matches overlap, the one starting first is kept,
 * and of two starting together the one whose pattern was added first.
 */
public final class OverlayLanguage implements Language {

    record Overlay(TokenType type, Pattern pattern) {
    }

    private final Language base;
    private final String id;
    private final String displayName;
    private final List<String> fileExtensions;
    private final List<Overlay> overlays;

    private OverlayLanguage(Builder builder) {
        base = builder.base;
        id = builder.id;
        displayName = builder.displayName;
        fileExtensions = List.copyOf(builder.fileExtensions);
        overlays = List.copyOf(builder.overlays);
    }

    /** Starts a language that is the base one plus the patterns added to the builder. */
    public static Builder over(Language base) {
        return new Builder(base);
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String displayName() {
        return displayName;
    }

    @Override
    public List<String> fileExtensions() {
        return fileExtensions;
    }

    @Override
    public Collection<String> keywords() {
        return base.keywords();
    }

    @Override
    public String lineComment() {
        return base.lineComment();
    }

    @Override
    public String[] blockComment() {
        return base.blockComment();
    }

    @Override
    public Lexer createLexer() {
        return new OverlayLexer(base.createLexer(), overlays);
    }

    public static final class Builder {

        private final Language base;
        private String id;
        private String displayName;
        private List<String> fileExtensions;
        private final List<Overlay> overlays = new ArrayList<>();

        private Builder(Language base) {
            this.base = base;
            this.id = base.id();
            this.displayName = base.displayName();
            this.fileExtensions = base.fileExtensions();
        }

        /** Sets the id; that of the base language by default. */
        public Builder id(String id) {
            this.id = id;
            return this;
        }

        /** Sets the name shown to users; that of the base language by default. */
        public Builder displayName(String displayName) {
            this.displayName = displayName;
            return this;
        }

        /** Sets the file extensions; those of the base language by default. */
        public Builder extensions(String... extensions) {
            fileExtensions = List.of(extensions);
            return this;
        }

        /** Shows every match of a regular expression as a token of the given type. */
        public Builder pattern(TokenType type, String regex) {
            overlays.add(new Overlay(type, Pattern.compile(regex)));
            return this;
        }

        public OverlayLanguage build() {
            return new OverlayLanguage(this);
        }
    }
}
