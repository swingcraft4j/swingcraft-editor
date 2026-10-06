package com.swingcraft4j.code.autocomplete;

import java.util.Objects;

/**
 * One suggestion offered by code completion. Start from a constructor and add to it:
 * <pre>{@code
 * new Completion("add", CompletionKind.METHOD)
 *         .withDetail("boolean")
 *         .withTemplate("add(${item})")
 *         .withDocumentation("Appends the item to the end of the list.");
 * }</pre>
 *
 * @param text          what is matched against what was typed and shown in the list; also
 *                      what is inserted, unless there is a template
 * @param kind          what it is, which decides its icon
 * @param detail        a short note shown at the right of its row, such as a type; when empty
 *                      the name of the kind is shown
 * @param template      a snippet to insert instead of the text, or null; see
 *                      {@link SnippetSession} for how one is written
 * @param documentation a longer description shown beside the list while it is selected; may
 *                      be empty
 */
public record Completion(String text, CompletionKind kind, String detail, String template, String documentation) {

    public Completion {
        Objects.requireNonNull(text);
        Objects.requireNonNull(kind);
        detail = detail == null ? "" : detail;
        documentation = documentation == null ? "" : documentation;
    }

    /** A plain word. */
    public Completion(String text) {
        this(text, CompletionKind.WORD);
    }

    public Completion(String text, CompletionKind kind) {
        this(text, kind, "", null, "");
    }

    /** A suggestion that inserts a snippet when its name is chosen. */
    public static Completion snippet(String name, String template) {
        return new Completion(name, CompletionKind.SNIPPET, "", template, "");
    }

    public Completion withDetail(String detail) {
        return new Completion(text, kind, detail, template, documentation);
    }

    public Completion withTemplate(String template) {
        return new Completion(text, kind, detail, template, documentation);
    }

    public Completion withDocumentation(String documentation) {
        return new Completion(text, kind, detail, template, documentation);
    }

    /** Whether a template is inserted rather than the text itself. */
    public boolean hasTemplate() {
        return template != null;
    }
}
