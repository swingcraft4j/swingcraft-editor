package com.swingcraft4j.code.autocomplete;

import java.util.List;

/**
 * A source of suggestions. It is called on the event dispatch thread each time the popup
 * opens or the prefix changes, so it must answer quickly.
 * <p>
 * A provider may return everything it knows: suggestions that do not start with the prefix
 * are dropped afterwards, as are duplicates. Filtering by the prefix itself is only worth
 * doing where it saves real work.
 */
@FunctionalInterface
public interface CompletionProvider {

    List<Completion> complete(CompletionRequest request);

    /**
     * Chars that open the popup as soon as they are typed, before any letter of a word: a
     * provider of members would return {@code "."}. None by default. The request then has an
     * empty prefix, and {@link CompletionRequest#charBeforePrefix()} and
     * {@link CompletionRequest#qualifier()} tell what was typed before it.
     */
    default String triggerCharacters() {
        return "";
    }

    /**
     * Whether, for this request, only the suggestions of this provider make sense: as inside
     * a placeholder, where a keyword or a word of the document would be wrong. When a
     * provider says so, the others are not asked. Never by default. It is asked on the event
     * dispatch thread, also for a provider that otherwise answers in the background.
     */
    default boolean isExclusive(CompletionRequest request) {
        return false;
    }
}
