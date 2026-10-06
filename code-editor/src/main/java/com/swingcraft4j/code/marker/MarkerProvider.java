package com.swingcraft4j.code.marker;

import com.swingcraft4j.code.lexer.Language;
import com.swingcraft4j.code.text.TextModel;

import java.util.List;

/**
 * Finds the problems in a document: a parser, a linter or a spell checker. A viewer given one
 * calls it when a document is shown and again a moment after each pause in editing.
 * <p>
 * It is called on the event dispatch thread and so must be quick. An analysis that takes
 * longer belongs on a thread of the application's own, which hands the markers to the viewer
 * when it is done.
 */
@FunctionalInterface
public interface MarkerProvider {

    /**
     * @param language the language of the document, or null for plain text
     * @return the markers for the document as it is now; offsets past its end are cut off
     */
    List<Marker> markers(TextModel model, Language language);
}
