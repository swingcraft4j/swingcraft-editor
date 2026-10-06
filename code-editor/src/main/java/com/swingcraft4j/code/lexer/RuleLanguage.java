package com.swingcraft4j.code.lexer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * A {@link Language} described by rules instead of a hand-written lexer: word lists, comment
 * and string delimiters, and regular expressions.
 * <pre>{@code
 * Language ini = RuleLanguage.builder("ini", "INI")
 *         .extensions("ini", "cfg")
 *         .lineComment(";")
 *         .pattern(TokenType.TAG, "\\[[^\\]]*\\]")
 *         .pattern(TokenType.ATTRIBUTE, "[\\w.]+(?=\\s*=)")
 *         .string("\"")
 *         .literals("true", "false")
 *         .build();
 * }</pre>
 * At each position the lexer tries, in this order: patterns (in the order they were added),
 * delimited regions (longest opening delimiter first), numbers, words, then single operator
 * and punctuation chars. To register a language for discovery, subclass this with a public
 * no-argument constructor and list the subclass as a {@link Language} service.
 */
public class RuleLanguage implements Language {

    /** Passed as the escape char of a region that has none. */
    public static final char NO_ESCAPE = '\0';

    record Region(TokenType type, String open, String close, char escape, boolean multiline) {
    }

    record PatternRule(TokenType type, Pattern pattern) {
    }

    private final String id;
    private final String displayName;
    private final List<String> fileExtensions;
    final List<PatternRule> patterns;
    final List<Region> regions;
    final Map<String, TokenType> words;
    final int maxWordLength;
    final boolean ignoreCase;
    final String operatorChars;
    final String punctuationChars;
    final String extraWordChars;
    final boolean detectFunctions;
    final boolean capitalizedTypes;
    private final String lineComment;
    private final String[] blockComment;

    protected RuleLanguage(Builder builder) {
        id = builder.id;
        displayName = builder.displayName;
        fileExtensions = List.copyOf(builder.fileExtensions);
        patterns = List.copyOf(builder.patterns);
        List<Region> sorted = new ArrayList<>(builder.regions);
        sorted.sort(Comparator.comparingInt((Region region) -> region.open().length()).reversed());
        regions = List.copyOf(sorted);
        words = Map.copyOf(builder.words);
        maxWordLength = words.keySet().stream().mapToInt(String::length).max().orElse(0);
        ignoreCase = builder.ignoreCase;
        operatorChars = builder.operatorChars;
        punctuationChars = builder.punctuationChars;
        extraWordChars = builder.extraWordChars;
        detectFunctions = builder.detectFunctions;
        capitalizedTypes = builder.capitalizedTypes;
        lineComment = builder.lineComment;
        blockComment = builder.blockComment;
    }

    public static Builder builder(String id, String displayName) {
        return new Builder(id, displayName);
    }

    /**
     * Starts a language that is this one plus what is added to the builder, as for the words
     * of an API that an application adds to a language:
     * <pre>{@code
     * Language script = new JavaScriptLanguage().toBuilder()
     *         .keywords("app")
     *         .words(TokenType.ATTRIBUTE, "request", "response")
     *         .build();
     * }</pre>
     * The id and the name are those of this language until the builder is given others.
     */
    public Builder toBuilder() {
        Builder builder = new Builder(id, displayName);
        builder.fileExtensions.addAll(fileExtensions);
        builder.patterns.addAll(patterns);
        builder.regions.addAll(regions);
        builder.words.putAll(words);
        builder.ignoreCase = ignoreCase;
        builder.operatorChars = operatorChars;
        builder.punctuationChars = punctuationChars;
        builder.extraWordChars = extraWordChars;
        builder.detectFunctions = detectFunctions;
        builder.capitalizedTypes = capitalizedTypes;
        builder.lineComment = lineComment;
        builder.blockComment = blockComment;
        return builder;
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

    /** Every word from the word lists: keywords, literals, types and the rest. */
    @Override
    public Collection<String> keywords() {
        return words.keySet();
    }

    @Override
    public String lineComment() {
        return lineComment;
    }

    @Override
    public String[] blockComment() {
        return blockComment == null ? null : blockComment.clone();
    }

    @Override
    public Lexer createLexer() {
        return new RuleLexer(this);
    }

    public static final class Builder {

        private String id;
        private String displayName;
        private final List<String> fileExtensions = new ArrayList<>();
        private final List<PatternRule> patterns = new ArrayList<>();
        private final List<Region> regions = new ArrayList<>();
        private final Map<String, TokenType> words = new HashMap<>();
        private boolean ignoreCase;
        private String operatorChars = "+-*/%=<>!&|^~?:";
        private String punctuationChars = "(){}[];,.";
        private String extraWordChars = "";
        private boolean detectFunctions;
        private boolean capitalizedTypes;
        private String lineComment;
        private String[] blockComment;

        private Builder(String id, String displayName) {
            this.id = id;
            this.displayName = displayName;
        }

        /** Sets another id, as for a language started with {@link RuleLanguage#toBuilder()}. */
        public Builder id(String id) {
            this.id = id;
            return this;
        }

        /** Sets another name to show to users. */
        public Builder displayName(String displayName) {
            this.displayName = displayName;
            return this;
        }

        /** Lower-case file extensions without the dot. */
        public Builder extensions(String... extensions) {
            fileExtensions.addAll(List.of(extensions));
            return this;
        }

        public Builder keywords(String... words) {
            return words(TokenType.KEYWORD, words);
        }

        /** Words such as {@code true} and {@code null}. */
        public Builder literals(String... words) {
            return words(TokenType.LITERAL, words);
        }

        public Builder types(String... words) {
            return words(TokenType.TYPE, words);
        }

        public Builder words(TokenType type, String... words) {
            for (String word : words) {
                this.words.put(word, type);
            }
            return this;
        }

        /** Matches the word lists without regard to case; list the words in lower case. */
        public Builder ignoreCase() {
            ignoreCase = true;
            return this;
        }

        /** A comment running from the prefix to the end of the line. */
        public Builder lineComment(String prefix) {
            if (lineComment == null) {
                lineComment = prefix; // the first one given is the one an editor comments lines out with
            }
            regions.add(new Region(TokenType.COMMENT, prefix, null, NO_ESCAPE, false));
            return this;
        }

        /** A comment between two delimiters that may span lines. */
        public Builder blockComment(String open, String close) {
            if (blockComment == null) {
                blockComment = new String[]{open, close};
            }
            return region(TokenType.COMMENT, open, close, NO_ESCAPE, true);
        }

        /** A string on one line, opened and closed by the same quote, with backslash escapes. */
        public Builder string(String quote) {
            return region(TokenType.STRING, quote, quote, '\\', false);
        }

        /** A string that may span lines, with backslash escapes. */
        public Builder multilineString(String open, String close) {
            return region(TokenType.STRING, open, close, '\\', true);
        }

        /**
         * Text between two delimiters, shown as one token.
         *
         * @param escape    a char that makes the next char literal, or {@link #NO_ESCAPE}
         * @param multiline whether the region continues on the next line when it is not
         *                  closed; otherwise it ends with the line
         */
        public Builder region(TokenType type, String open, String close, char escape, boolean multiline) {
            regions.add(new Region(type, open, close, escape, multiline));
            return this;
        }

        /** Text matching a regular expression at the current position. */
        public Builder pattern(TokenType type, String regex) {
            patterns.add(new PatternRule(type, Pattern.compile(regex)));
            return this;
        }

        /** Replaces the chars shown as operators. */
        public Builder operators(String chars) {
            operatorChars = chars;
            return this;
        }

        /** Replaces the chars shown as punctuation. */
        public Builder punctuation(String chars) {
            punctuationChars = chars;
            return this;
        }

        /** Chars that may appear in a word besides letters, digits and the underscore. */
        public Builder wordChars(String chars) {
            extraWordChars = chars;
            return this;
        }

        /** Shows a word followed by {@code (} as a function. */
        public Builder detectFunctions() {
            detectFunctions = true;
            return this;
        }

        /** Shows a capitalised word as a type and an all-caps word as a constant. */
        public Builder capitalizedTypes() {
            capitalizedTypes = true;
            return this;
        }

        public RuleLanguage build() {
            return new RuleLanguage(this);
        }
    }

    String normalize(String word) {
        return ignoreCase ? word.toLowerCase(Locale.ROOT) : word;
    }
}
