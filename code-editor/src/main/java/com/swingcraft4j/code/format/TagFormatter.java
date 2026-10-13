package com.swingcraft4j.code.format;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Lays out XML and HTML by their tags: each element on a line of its own, indented by the
 * depth of the elements around it. One blank line is kept where there were any between two
 * elements.
 * <p>
 * White space may be text, so it is only changed where it cannot be: between two tags with
 * nothing but white space between them. An element with text of its own is written as it was
 * found, with all that is in it, and so is an element with nothing in it. A tag, a comment and
 * what else stands between angle brackets is never changed inside.
 * <p>
 * Text whose tags do not match, or with a tag or a comment that is not closed, is returned
 * unchanged.
 */
public final class TagFormatter implements Formatter {

    /** The elements of HTML that have no end tag. */
    private static final Set<String> VOID = Set.of("area", "base", "br", "col", "embed", "hr", "img", "input", "link",
            "meta", "param", "source", "track", "wbr");
    /** The elements of HTML whose content is not markup, or is laid out by hand. */
    private static final Set<String> RAW = Set.of("script", "style", "pre", "textarea");
    /** The elements of HTML that stand in a line of text, where a line break between two of them shows as a space. */
    private static final Set<String> PHRASING = Set.of("a", "abbr", "b", "bdi", "bdo", "br", "button", "cite", "code",
            "data", "dfn", "em", "i", "img", "input", "kbd", "label", "mark", "q", "s", "samp", "select", "small", "span",
            "strong", "sub", "sup", "textarea", "time", "u", "var", "wbr");

    private enum Kind {
        OPEN, CLOSE,
        /** An element that is whole in one token: {@code <a/>}, {@code <br>} in HTML, or one whose content is not read. */
        WHOLE,
        /** A comment, a processing instruction or a declaration. */
        OTHER,
        TEXT
    }

    /** @param breaksBefore the line breaks in the white space between the token before and this one */
    private record Token(Kind kind, int start, int end, String name, int breaksBefore) {
    }

    /** An element with what is in it, or a token that stands for itself. */
    private static final class Node {
        final Token open;
        Token close;
        final List<Node> children = new ArrayList<>();

        Node(Token open) {
            this.open = open;
        }

        int end() {
            return close != null ? close.end() : open.end();
        }
    }

    private final boolean html;

    private TagFormatter(boolean html) {
        this.html = html;
    }

    /** A formatter for XML: every element has an end tag or ends with {@code />}, and names differ by case. */
    public static TagFormatter xml() {
        return new TagFormatter(false);
    }

    /**
     * A formatter for HTML. It knows the elements without an end tag, such as {@code <br>},
     * leaves {@code script}, {@code style}, {@code pre} and {@code textarea} as they are, and
     * puts no line break between elements that stand in a line of text, such as {@code <b>}.
     */
    public static TagFormatter html() {
        return new TagFormatter(true);
    }

    @Override
    public String format(String text, FormatOptions options) {
        List<Token> tokens = scan(text);
        if (tokens == null || tokens.isEmpty()) {
            return text;
        }
        Node root = tree(tokens);
        if (root == null || keptAsWritten(root)) {
            return text;
        }
        StringBuilder out = new StringBuilder(text.length() + text.length() / 4);
        write(root, 0, text, options, out);
        char last = text.charAt(text.length() - 1);
        if (last == '\n' || last == '\r') {
            out.append(options.lineSeparator());
        }
        return out.toString().equals(text) ? text : out.toString();
    }

    /** Writes the children of a node, each on a line of its own. */
    private void write(Node parent, int depth, String text, FormatOptions options, StringBuilder out) {
        for (int i = 0; i < parent.children.size(); i++) {
            Node node = parent.children.get(i);
            if (i > 0 || depth > 0) {
                out.append(options.lineSeparator());
                if (i > 0 && node.open.breaksBefore() > 1) {
                    out.append(options.lineSeparator());
                }
            }
            out.append(options.indent(depth));
            if (node.close == null || keptAsWritten(node)) {
                out.append(text, node.open.start(), node.end());
            } else {
                out.append(text, node.open.start(), node.open.end());
                write(node, depth + 1, text, options, out);
                out.append(options.lineSeparator()).append(options.indent(depth));
                out.append(text, node.close.start(), node.close.end());
            }
        }
    }

    /**
     * Whether what is in an element may not be put on lines of its own: it has nothing in it,
     * it has text, or in HTML an element that stands in a line of text.
     */
    private boolean keptAsWritten(Node node) {
        if (node.children.isEmpty()) {
            return true;
        }
        for (Node child : node.children) {
            Kind kind = child.open.kind();
            if (kind == Kind.TEXT || (html && kind != Kind.OTHER && PHRASING.contains(child.open.name()))) {
                return true;
            }
        }
        return false;
    }

    /** The tokens as a tree with a node above them all, or null where the tags do not match. */
    private static Node tree(List<Token> tokens) {
        Node root = new Node(null);
        Deque<Node> open = new ArrayDeque<>();
        open.push(root);
        for (Token token : tokens) {
            Node parent = open.peek();
            if (token.kind() == Kind.CLOSE) {
                if (parent == root || !parent.open.name().equals(token.name())) {
                    return null;
                }
                parent.close = token;
                open.pop();
                continue;
            }
            Node node = new Node(token);
            parent.children.add(node);
            if (token.kind() == Kind.OPEN) {
                open.push(node);
            }
        }
        return open.peek() == root ? root : null;
    }

    /** The tokens of a text, or null where a tag, a comment or a section is not closed. */
    private List<Token> scan(String text) {
        List<Token> tokens = new ArrayList<>();
        int length = text.length();
        int breaks = 0;
        int i = 0;
        while (i < length) {
            int start = i;
            Kind kind;
            String name = null;
            if (text.startsWith("<!--", i)) {
                i = after(text, "-->", i + 4);
                kind = Kind.OTHER;
            } else if (text.startsWith("<![CDATA[", i)) {
                i = after(text, "]]>", i + 9);
                kind = Kind.TEXT;
            } else if (text.startsWith("<?", i)) {
                i = after(text, "?>", i + 2);
                kind = Kind.OTHER;
            } else if (text.startsWith("<!", i)) {
                i = declarationEnd(text, i + 2);
                kind = Kind.OTHER;
            } else if (startsTag(text, i)) {
                boolean close = text.charAt(i + 1) == '/';
                int nameStart = i + (close ? 2 : 1);
                int nameEnd = nameStart;
                while (nameEnd < length && !Character.isWhitespace(text.charAt(nameEnd)) && "/>".indexOf(text.charAt(nameEnd)) < 0) {
                    nameEnd++;
                }
                name = text.substring(nameStart, nameEnd);
                if (html) {
                    name = name.toLowerCase(Locale.ROOT);
                }
                i = tagEnd(text, nameEnd);
                if (i < 0) {
                    return null;
                }
                if (close) {
                    kind = Kind.CLOSE;
                } else if (text.charAt(i - 2) == '/' || (html && VOID.contains(name))) {
                    kind = Kind.WHOLE;
                } else if (html && RAW.contains(name)) {
                    i = rawEnd(text, i, name);
                    kind = Kind.WHOLE;
                } else {
                    kind = Kind.OPEN;
                }
            } else {
                // text, up to what starts a tag
                boolean blank = true;
                int textBreaks = 0;
                while (i < length && !(startsTag(text, i) || text.startsWith("<!", i) || text.startsWith("<?", i))) {
                    char c = text.charAt(i);
                    if (!Character.isWhitespace(c)) {
                        blank = false;
                    } else if (c == '\n' || (c == '\r' && (i + 1 == length || text.charAt(i + 1) != '\n'))) {
                        textBreaks++;
                    }
                    i++;
                }
                if (blank) {
                    breaks += textBreaks;
                    continue;
                }
                kind = Kind.TEXT;
            }
            if (i < 0) {
                return null;
            }
            tokens.add(new Token(kind, start, i, name, breaks));
            breaks = 0;
        }
        return tokens;
    }

    /** Whether a tag starts here: {@code <} with a name or the slash of an end tag after it. */
    private static boolean startsTag(String text, int i) {
        if (i + 1 >= text.length() || text.charAt(i) != '<') {
            return false;
        }
        char c = text.charAt(i + 1);
        if (c == '/') {
            return i + 2 < text.length() && startsName(text.charAt(i + 2));
        }
        return startsName(c);
    }

    private static boolean startsName(char c) {
        return Character.isLetter(c) || c == '_' || c == ':';
    }

    /** The index just past what is searched for, or -1 if it is not there. */
    private static int after(String text, String end, int from) {
        int found = text.indexOf(end, from);
        return found < 0 ? -1 : found + end.length();
    }

    /** The index just past the {@code >} that ends a tag, which is not one inside a quoted value; -1 if there is none. */
    private static int tagEnd(String text, int from) {
        char quote = 0;
        for (int i = from; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quote != 0) {
                if (c == quote) {
                    quote = 0;
                }
            } else if (c == '"' || c == '\'') {
                quote = c;
            } else if (c == '>') {
                return i + 1;
            }
        }
        return -1;
    }

    /** The index just past a declaration such as a doctype, which may have declarations of its own in square brackets. */
    private static int declarationEnd(String text, int from) {
        char quote = 0;
        int brackets = 0;
        for (int i = from; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quote != 0) {
                if (c == quote) {
                    quote = 0;
                }
            } else if (c == '"' || c == '\'') {
                quote = c;
            } else if (c == '[') {
                brackets++;
            } else if (c == ']') {
                brackets--;
            } else if (c == '>' && brackets <= 0) {
                return i + 1;
            }
        }
        return -1;
    }

    /** The index just past the end tag of an element whose content is not read, or -1 if it has none. */
    private static int rawEnd(String text, int from, String name) {
        String end = "</" + name;
        for (int i = from; i + end.length() <= text.length(); i++) {
            if (text.regionMatches(true, i, end, 0, end.length())) {
                return tagEnd(text, i + end.length());
            }
        }
        return -1;
    }
}
