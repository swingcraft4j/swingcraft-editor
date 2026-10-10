package com.swingcraft4j.code.lexer;

import com.swingcraft4j.code.format.Formatter;

import java.util.Collection;
import java.util.List;

/**
 * A language that can be highlighted. Implement this and either pass an instance straight to
 * a viewer, or list the class in {@code META-INF/services/com.swingcraft4j.code.lexer.Language}
 * so that {@link Languages} finds it.
 */
public interface Language {

    /** Stable lower-case identifier, such as {@code "java"}. */
    String id();

    String displayName();

    /** Lower-case file extensions without the dot. */
    List<String> fileExtensions();

    /** The reserved words of the language, as offered by code completion; none by default. */
    default Collection<String> keywords() {
        return List.of();
    }

    /** What starts a comment that runs to the end of the line, such as {@code //}, or null if there is none. */
    default String lineComment() {
        return null;
    }

    /**
     * The pair that opens and closes a comment, such as {@code <!--} and {@code -->}, or null
     * if there is none. An editor uses it to comment lines out where there is no line comment.
     */
    default String[] blockComment() {
        return null;
    }

    /** What lays the text of the language out afresh when an editor is asked to format it, or null if nothing does. */
    default Formatter formatter() {
        return null;
    }

    /** Creates a new lexer; each caller gets its own instance. */
    Lexer createLexer();
}
