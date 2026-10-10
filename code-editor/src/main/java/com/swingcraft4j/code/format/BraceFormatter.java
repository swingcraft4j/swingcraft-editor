package com.swingcraft4j.code.format;

import com.swingcraft4j.code.layout.Cells;
import com.swingcraft4j.code.lexer.Language;
import com.swingcraft4j.code.lexer.Lexer;
import com.swingcraft4j.code.lexer.TokenType;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Formats the languages that group their code with braces: indents each line by the brackets
 * around it, puts the spaces around operators, commas and keywords right, and reduces runs of
 * blank lines. It keeps every line break: no line is wrapped and none are joined.
 * <p>
 * It works on the tokens of the lexer of the language and knows no grammar, so it formats code
 * that is half typed as well as code that compiles. Where the tokens do not tell what a char
 * means, as with a {@code *} in C that may multiply or point, the space around it is left as it
 * was written. Strings and comments are never changed, except that the lines of a block comment
 * move with its first line.
 * <p>
 * What differs from language to language is set on the builder:
 * <pre>{@code
 * Formatter formatter = BraceFormatter.of(language).continuationIndent(2).generics().ternary().build();
 * }</pre>
 */
public final class BraceFormatter implements Formatter {

    /** Operators with a space on each side wherever they are. */
    private static final Set<String> BINARY = Set.of("=", "==", "===", "!=", "!==", "<=", ">=", "&&", "||", "+=", "-=",
            "*=", "/=", "%=", "&=", "|=", "^=", "<<=", ">>=", ">>>=", "=>", ":=", "??", "<=>", "&&=", "||=", "??=");
    /** The other operators of more than one char, which are kept whole. */
    private static final Set<String> OTHER_OPERATORS = Set.of("->", "?:", "?.", "::", "..", "...", "..=", "++", "--", "<<", ">>",
            ">>>", "**", "!!", "<-", "=~", "==~", "*.", "&^");
    /** What at the end of a line tells that the statement goes on in the next one. */
    private static final Set<String> ENDS_UNFINISHED = Set.of("=", "+", "-", "*", "/", "%", "&&", "||", "|", "&", "^", "?",
            "==", "!=", "===", "!==", "<=", ">=", "+=", "-=", "*=", "/=", "%=", "&=", "|=", "^=", "->", "=>", ":=", "?:",
            "??", "<<");
    /** What at the start of a line tells that it goes on with the statement of the line before. */
    private static final Set<String> STARTS_CONTINUED = Set.of("?.", "&&", "||", ":", "==", "!=", "===", "!==", "<=", ">=",
            "=", "+=", "-=", "*=", "/=", "->", "=>", "?:", "??", "|", "^", "<<", ">>");
    /** The same, but these may also start a statement of their own. */
    private static final Set<String> MAY_START_CONTINUED = Set.of("+", "-", "*", "/", "%", "&", "<", ">");
    /** Keywords with a space between them and the parenthesis that follows. */
    private static final Set<String> CONTROL = Set.of("if", "for", "foreach", "while", "switch", "catch", "synchronized",
            "when", "using", "lock", "fixed", "match", "return", "in", "throw", "case", "with", "try", "async");
    private static final Set<String> BLOCK_KEYWORDS = Set.of("else", "try", "finally", "do", "for", "loop", "switch",
            "select", "catch", "unsafe");
    /** Keywords at the start of a line whose brace at the end opens a block and not a value. */
    private static final Set<String> HEAD_KEYWORDS = Set.of("if", "for", "while", "switch", "select", "func", "else", "type",
            "class", "struct", "namespace", "enum", "union", "do", "try", "template", "typedef", "extern");
    private static final Set<String> HEADERS = Set.of("if", "for", "foreach", "while");
    private static final Set<String> VALUE_KEYWORDS = Set.of("this", "super", "self", "base", "it");
    private static final Set<String> CLAUSES = Set.of("throws", "extends", "implements", "permits");
    private static final Set<String> ACCESS = Set.of("public", "private", "protected");

    private static final Set<TokenType> VALUES = EnumSet.of(TokenType.IDENTIFIER, TokenType.TYPE, TokenType.CONSTANT,
            TokenType.NUMBER, TokenType.STRING, TokenType.LITERAL, TokenType.VARIABLE, TokenType.FUNCTION, TokenType.ATTRIBUTE);
    private static final Set<TokenType> NAMES = EnumSet.of(TokenType.IDENTIFIER, TokenType.TYPE, TokenType.CONSTANT,
            TokenType.FUNCTION);
    private static final Set<TokenType> WORDS = EnumSet.of(TokenType.IDENTIFIER, TokenType.TYPE, TokenType.CONSTANT,
            TokenType.FUNCTION, TokenType.KEYWORD, TokenType.LITERAL, TokenType.VARIABLE, TokenType.NUMBER);

    private final Supplier<Lexer> lexers;
    private final int continuation;
    private final int maxBlankLines;
    private final boolean generics;
    private final boolean ternary;
    private final boolean elvis;
    private final boolean memberArrow;
    private final boolean pointers;
    private final boolean closureBars;
    private final boolean regexLiterals;
    private final boolean jsx;
    private final boolean semicolons;
    private final boolean caseAtSwitchLevel;
    private final boolean accessLabels;
    private final boolean braceInitializers;
    private final boolean sliceColons;
    private final boolean arrayInitializers;
    private final boolean nestedContinuation;
    private final boolean tabs;
    private final boolean keepAlignment;
    private final boolean plain;

    private BraceFormatter(Builder builder) {
        lexers = builder.lexers;
        continuation = builder.continuation;
        maxBlankLines = builder.maxBlankLines;
        generics = builder.generics;
        ternary = builder.ternary;
        elvis = builder.elvis;
        memberArrow = builder.memberArrow;
        pointers = builder.pointers;
        closureBars = builder.closureBars;
        regexLiterals = builder.regexLiterals;
        jsx = builder.jsx;
        semicolons = builder.semicolons;
        caseAtSwitchLevel = builder.caseAtSwitchLevel;
        accessLabels = builder.accessLabels;
        braceInitializers = builder.braceInitializers;
        sliceColons = builder.sliceColons;
        arrayInitializers = builder.arrayInitializers;
        nestedContinuation = builder.nestedContinuation;
        tabs = builder.tabs;
        keepAlignment = builder.keepAlignment;
        plain = builder.plain;
    }

    /** Starts a formatter that reads the code with the lexer of a language. */
    public static Builder of(Language language) {
        return new Builder(language::createLexer);
    }

    @Override
    public String format(String text, FormatOptions options) {
        FormatOptions used = tabs && !options.useTabs()
                ? new FormatOptions(options.tabSize(), true, options.lineSeparator())
                : options;
        String formatted = new Pass(used).run(text);
        // The chars that are not white space must come out as they went in. Should a rule ever
        // break that, the text is better left alone than changed.
        return formatted == null || formatted.equals(text) || !sameChars(text, formatted) ? text : formatted;
    }

    private static boolean sameChars(String before, String after) {
        int i = 0;
        int j = 0;
        while (true) {
            while (i < before.length() && Character.isWhitespace(before.charAt(i))) {
                i++;
            }
            while (j < after.length() && Character.isWhitespace(after.charAt(j))) {
                j++;
            }
            if (i == before.length() || j == after.length()) {
                return i == before.length() && j == after.length();
            }
            if (before.charAt(i++) != after.charAt(j++)) {
                return false;
            }
        }
    }

    public static final class Builder {

        private final Supplier<Lexer> lexers;
        private int continuation = 1;
        private int maxBlankLines = 2;
        private boolean generics;
        private boolean ternary;
        private boolean elvis;
        private boolean memberArrow;
        private boolean pointers;
        private boolean closureBars;
        private boolean regexLiterals;
        private boolean jsx;
        private boolean semicolons = true;
        private boolean caseAtSwitchLevel;
        private boolean accessLabels;
        private boolean braceInitializers;
        private boolean sliceColons;
        private boolean arrayInitializers;
        private boolean nestedContinuation;
        private boolean tabs;
        private boolean keepAlignment;
        private boolean plain;

        private Builder(Supplier<Lexer> lexers) {
            this.lexers = lexers;
        }

        /**
         * The levels by which a line is indented that goes on with the statement of the line
         * before, or lies inside parentheses or square brackets; one by default, two for Java.
         */
        public Builder continuationIndent(int levels) {
            continuation = levels;
            return this;
        }

        /** The blank lines that are kept where there are more; two by default. */
        public Builder maxBlankLines(int lines) {
            maxBlankLines = lines;
            return this;
        }

        /** Type arguments are written in angle brackets, which are told from less and greater. */
        public Builder generics() {
            generics = true;
            return this;
        }

        /** {@code a ? b : c} is an expression, so a question mark and its colon get spaces around them. */
        public Builder ternary() {
            ternary = true;
            return this;
        }

        /** {@code ?:} is an operator, with a space on each side. */
        public Builder elvis() {
            elvis = true;
            return this;
        }

        /** {@code ->} names a member, as in C, and gets no spaces; elsewhere it gets one on each side. */
        public Builder memberArrow() {
            memberArrow = true;
            return this;
        }

        /**
         * {@code *} and {@code &} may point and refer as well as multiply and combine, so the
         * space around them is left as written, and so is the space after a cast.
         */
        public Builder pointers() {
            pointers = true;
            return this;
        }

        /** {@code |} may enclose the parameters of a closure, as in Rust, so the space around it is left as written. */
        public Builder closureBars() {
            closureBars = true;
            return this;
        }

        /**
         * A regular expression may be written between slashes. The lexers do not know these, so
         * the formatter finds them itself and leaves them alone.
         */
        public Builder regexLiterals() {
            regexLiterals = true;
            return this;
        }

        /** The code may have JSX in it, which cannot be formatted: a text with a tag in it is returned unchanged. */
        public Builder jsx() {
            jsx = true;
            return this;
        }

        /**
         * Statements end with the line and not with a semicolon, so a line that starts with
         * {@code +} or {@code -} is a statement of its own.
         */
        public Builder noSemicolons() {
            semicolons = false;
            return this;
        }

        /** The labels of a switch stand at the level of the switch, as in Go, and not one level in. */
        public Builder caseAtSwitchLevel() {
            caseAtSwitchLevel = true;
            return this;
        }

        /** {@code public:} and its like stand at the level of the class, as in C++. */
        public Builder accessLabels() {
            accessLabels = true;
            return this;
        }

        /**
         * A brace after a name may start a value, as in {@code Point{1, 2}}, so no space is put
         * before it, unless it ends a line that starts with a keyword such as {@code if}.
         */
        public Builder braceInitializers() {
            braceInitializers = true;
            return this;
        }

        /** A colon in square brackets divides a slice, as in {@code a[1:2]}, and gets no space after it. */
        public Builder sliceColons() {
            sliceColons = true;
            return this;
        }

        /** A brace after {@code =} or {@code ]} holds the elements of an array, which are indented as a line that goes on is. */
        public Builder arrayInitializers() {
            arrayInitializers = true;
            return this;
        }

        /**
         * The indents of brackets opened on one line add up, so that after {@code a(b(} the
         * arguments of {@code b} are indented twice; and so does that of an argument that goes on.
         */
        public Builder nestedContinuation() {
            nestedContinuation = true;
            return this;
        }

        /** The code is indented with tabs whatever the options say, as Go is. */
        public Builder tabs() {
            tabs = true;
            return this;
        }

        /** Several spaces between two tokens are kept, where the code is lined up in columns as gofmt does it. */
        public Builder keepAlignment() {
            keepAlignment = true;
            return this;
        }

        /**
         * White space carries meaning, as in the selectors of CSS, so it is only changed at
         * commas, semicolons, opening braces and the colon of a declaration.
         */
        public Builder plainSpacing() {
            plain = true;
            return this;
        }

        public BraceFormatter build() {
            return new BraceFormatter(this);
        }
    }

    private static final class Token {
        final int start;
        int end;
        TokenType type;
        String text;
        /** {@code <} or {@code >} where the token brackets type arguments, else 0. */
        char angle;
        /** For a {@code >} of type arguments that came after a dot: the name follows it at once. */
        boolean tight;
        boolean spaceBefore;
        boolean spaceAfter;
        /** The gap before the token is kept exactly as written. */
        boolean exact;
        /** For a colon: it belongs to a question mark. */
        boolean ternary;
        /** For an opening bracket: the cells of its line up to and with it, as written and as formatted. */
        int oldEnd;
        int newEnd;

        Token(int start, int end, TokenType type) {
            this.start = start;
            this.end = end;
            this.type = type;
        }

        boolean comment() {
            return type == TokenType.COMMENT || type == TokenType.DOC_COMMENT;
        }

        /** Whether this is the given punctuation or operator, and not a string or comment that reads the same. */
        boolean is(String symbol) {
            return angle == 0 && type != TokenType.STRING && !comment() && text.equals(symbol);
        }

        boolean operator() {
            return type == TokenType.OPERATOR && angle == 0;
        }

        boolean open() {
            return is("{") || is("(") || is("[");
        }

        boolean close() {
            return is("}") || is(")") || is("]");
        }
    }

    private static final class Line {
        final String text;
        final String terminator;
        /** The state of the lexer at the start: not the initial one inside a comment or a string. */
        final int startState;
        List<Token> tokens = new ArrayList<>();
        /** A preprocessor line, which is copied as it is. */
        boolean verbatim;
        boolean prepared;

        Line(String text, String terminator, int startState) {
            this.text = text;
            this.terminator = terminator;
            this.startState = startState;
        }
    }

    /** A bracket that is open. */
    private static final class Open {
        final char kind;
        /** The levels of a line that starts with the bracket that closes this one. */
        final int indent;
        /** The levels of the lines inside. */
        final int inner;
        final int line;
        /** A brace around statements, and not one around the elements of an array. */
        boolean block;
        /** A parenthesis around the arguments of a call. */
        boolean call;
        /** A line inside starts something of its own, as the second argument of a call does, and does not go on with the first. */
        boolean wrapped;
        /** For the brace of a switch: a label was seen, so its statements are a level further in. */
        boolean caseBody;
        /**
         * The cells up to what follows the bracket on its line, as written and as formatted, or
         * -1 if nothing follows it. A line inside that was lined up with that stays lined up.
         */
        int alignOld = -1;
        int alignNew = -1;

        Open(char kind, int indent, int inner, int line) {
            this.kind = kind;
            this.indent = indent;
            this.inner = inner;
            this.line = line;
        }
    }

    /** One run of the formatter over a text. */
    private final class Pass {

        private final FormatOptions options;
        private final Deque<Open> stack = new ArrayDeque<>();
        private List<Line> lines;
        private boolean jsxSeen;
        /** The last line with code on it, its last token that is not a comment, and what follows from them. */
        private int previousLine = -1;
        private Token previousLast;
        /** The extra levels of the line before if it is an {@code if} or a loop without a brace, else -1. */
        private int previousDangling = -1;
        /** The levels the current line has beyond those of its block as the body of such a line. */
        private int extra;
        /** Whether the current line starts a statement, and does not go on with one. */
        private boolean startsStatement;
        /** The bracket around the statement the current line is part of, and the levels of its first line. */
        private Open statementTop;
        private int statementLevels;
        /** Whether the current line starts with a keyword whose brace opens a block, and where its last token of code is. */
        private boolean blockHead;
        private int lastCode;
        /** The cells by which the current line is indented where it is lined up under a bracket, else -1. */
        private int alignColumn = -1;

        Pass(FormatOptions options) {
            this.options = options;
        }

        String run(String text) {
            lines = read(text);
            if (jsxSeen) {
                return null;
            }
            StringBuilder out = new StringBuilder(text.length() + text.length() / 8);
            int blanks = 0;
            int shift = 0; // the cells by which the open comment has moved
            for (int index = 0; index < lines.size(); index++) {
                Line line = lines.get(index);
                if (line.verbatim) {
                    out.append(line.text).append(line.terminator);
                    blanks = 0;
                    continue;
                }
                boolean inside = line.startState != Lexer.INITIAL_STATE;
                if (line.tokens.isEmpty()) {
                    if (inside) {
                        out.append(line.text).append(line.terminator);
                    } else if (++blanks <= maxBlankLines) {
                        out.append(line.terminator);
                    }
                    continue;
                }
                blanks = 0;
                Open top = stack.peek();
                int from = inside ? 1 : 0; // the first token that is code of this line
                prepare(line, from, top, index);
                List<Token> tokens = line.tokens;
                StringBuilder row = new StringBuilder();
                int levels;
                extra = 0;
                if (inside) {
                    // The line starts inside a string, which is not touched, or a comment, which
                    // moves as its first line did.
                    Token head = tokens.get(0);
                    row.append(head.comment() ? shifted(head.text, shift) : head.text);
                    levels = top == null ? 0 : top.inner;
                    startsStatement = false;
                } else {
                    levels = levels(index, top);
                    row.append(alignColumn >= 0 ? white(alignColumn) : options.indent(levels));
                }
                int head = from;
                while (head < tokens.size() - 1 && tokens.get(head).is("}")) {
                    head++;
                }
                blockHead = head < tokens.size() && tokens.get(head).type == TokenType.KEYWORD
                        && HEAD_KEYWORDS.contains(tokens.get(head).text);
                lastCode = tokens.size() - 1;
                while (lastCode > from && tokens.get(lastCode).comment()) {
                    lastCode--;
                }
                int exactFrom = Integer.MAX_VALUE;
                int lastStart = row.length();
                for (int i = from; i < tokens.size(); i++) {
                    Token token = tokens.get(i);
                    if (i > 0) {
                        String written = line.text.substring(tokens.get(i - 1).end, token.start);
                        row.append(i > exactFrom ? written : gap(tokens, i, written));
                    }
                    lastStart = row.length();
                    row.append(token.text);
                    if (token.open()) {
                        token.oldEnd = width(line.text, token.end);
                        token.newEnd = width(row, row.length());
                    }
                    if (token.type == TokenType.STRING && unfinishedTemplate(token.text)) {
                        // "${map["key"]}": the lexer ends the string at the inner quote, and what it
                        // takes for code after it is still string
                        exactFrom = Math.min(exactFrom, i);
                    }
                }
                Token last = tokens.get(tokens.size() - 1);
                if (last.comment() && tokens.size() > from) {
                    shift = width(row, lastStart) - width(line.text, last.start);
                }
                if (from < tokens.size()) {
                    after(tokens, from, index, levels);
                }
                out.append(row).append(line.terminator);
            }
            return out.toString();
        }

        // ---- Reading ----

        private List<Line> read(String text) {
            List<String> texts = new ArrayList<>();
            List<String> terminators = new ArrayList<>();
            TextLines.split(text, texts, terminators);
            Lexer lexer = lexers.get();
            List<Line> read = new ArrayList<>(texts.size());
            int state = Lexer.INITIAL_STATE;
            boolean directive = false; // the line before is a preprocessor line that ends with a backslash
            for (int i = 0; i < texts.size(); i++) {
                Line line = new Line(texts.get(i), terminators.get(i), state);
                state = lex(lexer, line.text.toCharArray(), state, line.tokens);
                fill(line);
                boolean starts = line.startState == Lexer.INITIAL_STATE && !line.tokens.isEmpty()
                        && line.tokens.get(0).type == TokenType.PREPROCESSOR;
                line.verbatim = directive || starts;
                directive = line.verbatim && line.text.endsWith("\\");
                read.add(line);
            }
            return read;
        }

        /** Tokenizes a line, taking each regular expression between slashes as one token. */
        private int lex(Lexer lexer, char[] chars, int state, List<Token> tokens) {
            int from = 0;
            int checked = 0;
            while (true) {
                int end = lexer.tokenize(chars, from, chars.length, state,
                        (start, length, type) -> tokens.add(new Token(start, start + length, type)));
                int slash = regexLiterals || jsx ? regexStart(chars, tokens, checked) : -1;
                if (slash < 0) {
                    return end;
                }
                // What the lexer made of the expression is void, and so is all after it on the
                // line: a quote in the expression would have been read as the start of a string.
                int start = tokens.get(slash).start;
                int close = regexEnd(chars, start + 1);
                tokens.subList(slash, tokens.size()).clear();
                tokens.add(new Token(start, close, TokenType.STRING));
                checked = tokens.size();
                from = close;
                state = Lexer.INITIAL_STATE;
            }
        }

        /** The index of the first token from {@code from} on that is the slash opening a regular expression, or -1. */
        private int regexStart(char[] chars, List<Token> tokens, int from) {
            for (int i = from; i < tokens.size(); i++) {
                Token token = tokens.get(i);
                if (token.type != TokenType.OPERATOR || token.end - token.start != 1) {
                    continue;
                }
                char c = chars[token.start];
                char next = token.end < chars.length ? chars[token.end] : '\0';
                char second = token.end + 1 < chars.length ? chars[token.end + 1] : '\0';
                if (jsx && ((c == '<' && next == '/' && (Character.isLetter(second) || second == '>')) || (c == '/' && next == '>'))) {
                    jsxSeen = true;
                }
                // after a value a slash divides; anywhere else it opens an expression
                if (regexLiterals && c == '/' && !valueBefore(chars, i > 0 ? tokens.get(i - 1) : null)
                        && regexEnd(chars, token.end) > 0) {
                    return i;
                }
            }
            return -1;
        }

        private boolean valueBefore(char[] chars, Token previous) {
            if (previous == null) {
                return false;
            }
            if (VALUES.contains(previous.type)) {
                return true;
            }
            char c = chars[previous.start];
            if (previous.end - previous.start != 1) {
                return previous.type == TokenType.KEYWORD
                        && VALUE_KEYWORDS.contains(new String(chars, previous.start, previous.end - previous.start));
            }
            // i++ / 2
            return c == ')' || c == ']' || ((c == '+' || c == '-') && previous.start > 0 && chars[previous.start - 1] == c);
        }

        /** The index just past the regular expression whose first char is at {@code from}, or -1 if it does not end on the line. */
        private int regexEnd(char[] chars, int from) {
            boolean inClass = false;
            for (int i = from; i < chars.length; i++) {
                char c = chars[i];
                if (c == '\\') {
                    i++;
                } else if (c == '[') {
                    inClass = true;
                } else if (c == ']') {
                    inClass = false;
                } else if (c == '/' && !inClass) {
                    if (i == from) {
                        return -1;
                    }
                    int end = i + 1;
                    while (end < chars.length && Character.isLetter(chars[end])) {
                        end++; // the flags
                    }
                    return end;
                }
            }
            return -1;
        }

        /** Gives each token its text, and makes tokens of the chars the lexer passed over, so that none is lost. */
        private void fill(Line line) {
            List<Token> all = new ArrayList<>(line.tokens.size() + 2);
            int at = 0;
            for (int i = 0; i <= line.tokens.size(); i++) {
                Token token = i < line.tokens.size() ? line.tokens.get(i) : null;
                int limit = token != null ? token.start : line.text.length();
                while (at < limit) {
                    if (Character.isWhitespace(line.text.charAt(at))) {
                        at++;
                        continue;
                    }
                    int end = at;
                    while (end < limit && !Character.isWhitespace(line.text.charAt(end))) {
                        end++;
                    }
                    all.add(new Token(at, end, TokenType.TEXT));
                    at = end;
                }
                if (token != null) {
                    all.add(token);
                    at = token.end;
                }
            }
            for (Token token : all) {
                token.text = line.text.substring(token.start, token.end);
            }
            line.tokens = all;
        }

        // ---- Indentation ----

        /** The levels by which a line of code is indented, from the brackets open at its start and the line before. */
        private int levels(int index, Open top) {
            Line line = lines.get(index);
            List<Token> tokens = line.tokens;
            Token first = tokens.get(0);
            alignColumn = -1;
            if (tokens.stream().allMatch(Token::comment)) {
                // A comment is indented as the code it stands above, unless that closes the block.
                int next = nextCode(index);
                if (next >= 0) {
                    prepare(lines.get(next), 0, top, next);
                    if (!lines.get(next).tokens.get(0).close()) {
                        int levels = levels(next, top);
                        extra = 0;
                        return levels;
                    }
                }
                return top == null ? 0 : top.inner + (top.caseBody ? 1 : 0);
            }
            startsStatement = !first.is(")") && !first.is("]");
            if (first.close()) {
                Open match = match(first);
                if (match != null) {
                    return match.indent; // a closing bracket goes under the line that opened it
                }
            }
            if (top != null && !top.block) {
                startsStatement = false;
                if (top.alignOld >= 0 && width(line.text, first.start) == top.alignOld) {
                    alignColumn = top.alignNew;
                    return top.inner;
                }
                // an argument that goes on is indented again
                boolean goesOn = top.wrapped && top.call && continued(first, tokens);
                return top.inner + (goesOn ? continuation : 0);
            }
            int base = top == null ? 0 : top.inner;
            if (top != null) {
                boolean label = first.type == TokenType.KEYWORD && (first.text.equals("case") || first.text.equals("default"));
                if (caseAtSwitchLevel ? label : accessLabels && accessLabel(tokens)) {
                    return top.indent;
                }
                if (top.caseBody && !label) {
                    base++;
                }
                if (top.line == previousLine) {
                    return base; // the first line in a block
                }
            }
            if (plain) {
                // a value that goes on: "transition: a 1s," and then "b 2s;"
                return base + (top != null && previousLast != null && !endsStatement(previousLast) && !first.open()
                        && !tokens.stream().allMatch(Token::comment) && declaration(index, 0) ? 1 : 0);
            }
            if (continued(first, tokens)) {
                startsStatement = false;
                return base + continuation;
            }
            if (previousDangling >= 0 && !first.open() && !tokens.stream().allMatch(Token::comment)) {
                extra = previousDangling + 1;
                return base + extra;
            }
            return base;
        }

        /**
         * Whether a line right inside the bracket at token {@code open} of a line starts something
         * of its own: it comes after a comma, or is the first after the bracket.
         */
        private boolean wraps(int index, int open) {
            int depth = 0;
            Token previous = lines.get(index).tokens.get(open);
            for (int l = index; l < lines.size() && l < index + 200; l++) {
                Line line = lines.get(l);
                if (line.verbatim) {
                    continue;
                }
                boolean atStart = l > index && line.startState == Lexer.INITIAL_STATE;
                for (int i = l == index ? open + 1 : 0; i < line.tokens.size(); i++) {
                    Token token = line.tokens.get(i);
                    if (token.comment()) {
                        continue;
                    }
                    if (token.close() && depth-- == 0) {
                        return false;
                    }
                    if (atStart && depth == 0 && (previous.is(",") || (l == index + 1 && previous.open()))) {
                        return true;
                    }
                    atStart = false;
                    previous = token;
                    if (token.open()) {
                        depth++;
                    }
                }
            }
            return false;
        }

        /** The next line that has code on it, or -1 if that is far off or starts inside a comment or a string. */
        private int nextCode(int index) {
            for (int i = index + 1; i < lines.size() && i < index + 50; i++) {
                Line line = lines.get(i);
                if (line.verbatim || line.tokens.isEmpty() || line.tokens.stream().allMatch(Token::comment)) {
                    continue;
                }
                return line.startState == Lexer.INITIAL_STATE ? i : -1;
            }
            return -1;
        }

        /** Whether the line goes on with the statement of the line before it. */
        private boolean continued(Token first, List<Token> tokens) {
            Token last = previousLast;
            if (plain || last == null || first.open() || tokens.stream().allMatch(Token::comment)) {
                return false;
            }
            if (first.type == TokenType.KEYWORD && CLAUSES.contains(first.text)) {
                return !endsStatement(last);
            }
            if (first.is(".") || last.is(".")) {
                return true; // a chain of calls
            }
            if (last.ternary || (last.operator() && ENDS_UNFINISHED.contains(last.text) && (ternary || !last.text.equals("?")))) {
                return true;
            }
            if (!first.operator()) {
                return false;
            }
            if (first.text.equals("?")) {
                return ternary;
            }
            if (STARTS_CONTINUED.contains(first.text)) {
                return true;
            }
            return semicolons && previousDangling < 0 && MAY_START_CONTINUED.contains(first.text) && !endsStatement(last)
                    && !last.is(",") && !last.is(":");
        }

        private boolean endsStatement(Token last) {
            return last.is(";") || last.is("{") || last.is("}");
        }

        private boolean accessLabel(List<Token> tokens) {
            return tokens.size() >= 2 && ACCESS.contains(tokens.get(0).text) && tokens.get(1).is(":");
        }

        /** Whether the statement that the token is in is a declaration of CSS: it ends before a brace opens. */
        private boolean declaration(int index, int token) {
            int seen = 0;
            for (int l = index; l < lines.size(); l++) {
                List<Token> tokens = lines.get(l).tokens;
                for (int i = l == index ? token : 0; i < tokens.size(); i++) {
                    Token t = tokens.get(i);
                    if (t.is(";") || t.is("}")) {
                        return true;
                    }
                    if (t.is("{") || ++seen > 400) {
                        return false;
                    }
                }
            }
            return false;
        }

        private Open match(Token close) {
            char kind = "({[".charAt(")}]".indexOf(close.text));
            for (Open open : stack) {
                if (open.kind == kind) {
                    return open;
                }
            }
            return null;
        }

        /** Takes the brackets of a line into account, and notes what the next line needs to know of this one. */
        private void after(List<Token> tokens, int from, int index, int levels) {
            if (tokens.subList(from, tokens.size()).stream().allMatch(Token::comment)) {
                return;
            }
            Open atStart = stack.peek();
            Token first = tokens.get(from);
            boolean chain = first.is(".") || (first.operator() && first.text.equals("?."));
            boolean noted = !startsStatement;
            Token before = previousLast;
            Deque<Integer> opened = new ArrayDeque<>(); // the brackets opened on this line, as indexes
            int parenthesis = -1; // where the last closing parenthesis was opened, if on this line
            Token last = null;
            int lastIndex = -1;
            for (int i = from; i < tokens.size(); i++) {
                Token token = tokens.get(i);
                if (token.comment()) {
                    continue;
                }
                if (!noted && !token.close()) {
                    // the statement starts here, after the brackets that the line closes first
                    statementTop = stack.peek();
                    statementLevels = levels;
                    noted = true;
                }
                if (token.open()) {
                    // The brace that ends the head of a statement belongs to its first line, however
                    // many lines the head took: "if (a &&" and then "b) {". Not so the brace of a
                    // lambda in a chain of calls, which belongs to its own line.
                    Open around = stack.peek();
                    boolean array = token.is("{") && arrayInitializers && before != null && (before.is("=") || before.is("]")
                            || before.is("(") || ((before.is(",") || before.is("{")) && around != null && !around.block));
                    Open open;
                    if (token.is("{") && !array) {
                        int indent = around == statementTop && !chain ? statementLevels : levels;
                        open = new Open('{', indent, indent + 1, index);
                        open.block = true;
                    } else {
                        // Inside a bracket opened on the same line the indents add up, "a(b(" and then the
                        // arguments of b, if the outer bracket has lines of its own as well.
                        boolean nested = nestedContinuation && around != null && around.line == index && !around.block;
                        int base = nested ? (around.wrapped ? around.inner : around.indent) : levels;
                        open = new Open(token.text.charAt(0), base, base + continuation, index);
                        open.wrapped = nestedContinuation && wraps(index, i);
                        open.call = token.is("(") && before != null && (NAMES.contains(before.type) || before.is(")")
                                || before.is("]") || before.angle == '>' || before.type == TokenType.ANNOTATION);
                        if (i < lastCode) {
                            open.alignOld = token.oldEnd;
                            open.alignNew = token.newEnd;
                        }
                    }
                    stack.push(open);
                    opened.push(i);
                } else if (token.close()) {
                    Open match = match(token);
                    Integer start = null;
                    if (match != null) {
                        // a bracket left open inside, as in code half typed, is given up
                        Open popped;
                        while ((popped = stack.pop()) != match) {
                            if (popped.line == index) {
                                opened.poll();
                            }
                        }
                        start = match.line == index ? opened.poll() : null;
                    }
                    parenthesis = token.is(")") && start != null ? start : -1;
                }
                before = token;
                last = token;
                lastIndex = i;
            }
            if (!noted) {
                statementTop = stack.peek();
                statementLevels = levels;
            }
            if (last == null) {
                return;
            }
            boolean label = first.type == TokenType.KEYWORD && (first.text.equals("case") || first.text.equals("default"));
            if (label && !caseAtSwitchLevel && atStart != null && atStart.kind == '{'
                    && (last.is(":") || (last.is("{") && lastIndex > from && tokens.get(lastIndex - 1).is(":")))) {
                atStart.caseBody = true;
            }
            // "if (x)" with its statement on the next line
            boolean header = last.type == TokenType.KEYWORD && (last.text.equals("else") || last.text.equals("do"));
            if (last.is(")") && parenthesis > from) {
                Token keyword = tokens.get(parenthesis - 1);
                header = keyword.type == TokenType.KEYWORD && HEADERS.contains(keyword.text)
                        && !(first.is("}") && keyword.text.equals("while"));
            }
            previousDangling = header ? extra : -1;
            previousLast = last;
            previousLine = index;
        }

        // ---- Tokens ----

        /** Finds out, for the tokens of a line, what the spacing rules need to know of them. */
        private void prepare(Line line, int from, Open top, int index) {
            if (line.prepared) {
                return;
            }
            line.prepared = true;
            if (plain) {
                int depth = top != null && top.kind == '(' ? 1 : 0;
                int braces = 0;
                for (Open open : stack) {
                    braces += open.kind == '{' ? 1 : 0;
                }
                // where the statement starts that the token is in, if that is on this line
                int statement = previousLast == null || endsStatement(previousLast) ? from : -1;
                for (int i = from; i < line.tokens.size(); i++) {
                    Token token = line.tokens.get(i);
                    token.exact = depth > 0; // "url(data:image/png;base64,...)" must stay as it is
                    if (token.is("(")) {
                        depth++;
                    } else if (token.is(")") && depth > 0) {
                        depth--;
                    } else if (token.is("{") || token.is("}") || token.is(";")) {
                        braces += token.is("{") ? 1 : token.is("}") ? -1 : 0;
                        statement = i + 1;
                    } else if (token.is(":") && i == statement + 1 && statement >= from && braces > 0 && depth == 0
                            && declaration(index, i)) {
                        // the colon of "color: red", and not that of "a:hover"
                        token.tight = true;
                        token.spaceAfter = true;
                    }
                }
                return;
            }
            if (generics) {
                markTypeArguments(line.tokens, from);
            }
            line.tokens = merged(line.tokens, from);
            spaceOperators(line.tokens, from, top);
        }

        private void markTypeArguments(List<Token> tokens, int from) {
            for (int i = from; i < tokens.size(); i++) {
                Token token = tokens.get(i);
                if (!token.is("<") || token.type != TokenType.OPERATOR) {
                    continue;
                }
                Token previous = i > from ? tokens.get(i - 1) : null;
                Token next = i + 1 < tokens.size() ? tokens.get(i + 1) : null;
                boolean partOfOperator = (previous != null && previous.end == token.start && previous.is("<"))
                        || (next != null && next.start == token.end && (next.is("<") || next.is("=") || next.is("-")));
                if (partOfOperator || (previous != null && (previous.text.equals("operator") || previous.is(")") || previous.is("]")
                        || previous.type == TokenType.NUMBER || previous.type == TokenType.STRING))) {
                    continue;
                }
                int close = typeArgumentsEnd(tokens, i);
                if (close < 0) {
                    continue;
                }
                Token following = close + 1 < tokens.size() ? tokens.get(close + 1) : null;
                if (following != null && (following.type == TokenType.NUMBER || following.type == TokenType.STRING
                        || following.type == TokenType.LITERAL)) {
                    continue;
                }
                // "a < b, c > d" reads the same as type arguments: with plain names on both sides
                // it is taken for two comparisons unless a call follows
                boolean plainNames = false;
                for (int k = i + 1; k < close; k++) {
                    plainNames |= tokens.get(k).type == TokenType.IDENTIFIER;
                }
                if (plainNames && previous != null && previous.type == TokenType.IDENTIFIER
                        && !(following != null && (following.is("(") || following.is(".")))) {
                    continue;
                }
                for (int k = i; k <= close; k++) {
                    Token inner = tokens.get(k);
                    if (inner.type == TokenType.OPERATOR && (inner.text.equals("<") || inner.text.equals(">"))) {
                        inner.angle = inner.text.charAt(0);
                    }
                }
                tokens.get(close).tight = previous != null && previous.is(".");
                i = close;
            }
        }

        /** The index of the {@code >} that closes the type arguments opened at {@code open}, or -1 if they are none. */
        private int typeArgumentsEnd(List<Token> tokens, int open) {
            int depth = 1;
            for (int i = open + 1; i < tokens.size(); i++) {
                Token token = tokens.get(i);
                Token next = i + 1 < tokens.size() ? tokens.get(i + 1) : null;
                boolean doubled = (next != null && next.start == token.end && next.text.equals(token.text))
                        || (tokens.get(i - 1).end == token.start && tokens.get(i - 1).text.equals(token.text));
                if (token.type == TokenType.OPERATOR) {
                    switch (token.text) {
                        case "<":
                            depth++;
                            break;
                        case ">":
                            if (--depth == 0) {
                                return i;
                            }
                            break;
                        case "?":
                        case "*":
                            break;
                        case "&":
                            if (doubled) {
                                return -1;
                            }
                            break;
                        case ":":
                            if (!doubled) {
                                return -1;
                            }
                            break;
                        default:
                            return -1;
                    }
                } else if (token.type == TokenType.PUNCTUATION) {
                    if (!(token.is(",") || token.is(".") || token.is("[") || token.is("]"))) {
                        return -1;
                    }
                } else if (token.type != TokenType.IDENTIFIER && token.type != TokenType.TYPE && token.type != TokenType.KEYWORD
                        && token.type != TokenType.CONSTANT && token.type != TokenType.ANNOTATION) {
                    return -1;
                }
            }
            return -1;
        }

        /** Joins the chars of an operator, which the lexers give one at a time: {@code =} and {@code =} to {@code ==}. */
        private List<Token> merged(List<Token> tokens, int from) {
            List<Token> merged = new ArrayList<>(tokens.size());
            for (int i = 0; i < tokens.size(); i++) {
                Token token = tokens.get(i);
                Token last = merged.isEmpty() ? null : merged.get(merged.size() - 1);
                if (i > from && operatorChar(last) && operatorChar(token) && last.end == token.start) {
                    last.end = token.end;
                    last.text += token.text;
                    last.type = TokenType.OPERATOR;
                } else {
                    merged.add(token);
                }
            }
            // What is left may be two operators that stand together, as in "x=-1" and "a:&b".
            for (int i = merged.size() - 1; i >= from; i--) {
                Token token = merged.get(i);
                if (!token.operator() || token.text.length() == 1 || known(token.text)) {
                    continue;
                }
                String run = token.text;
                merged.remove(i);
                int at = 0;
                int insert = i;
                while (at < run.length()) {
                    int length = Math.min(4, run.length() - at);
                    while (length > 1 && !known(run.substring(at, at + length))) {
                        length--;
                    }
                    Token part = new Token(token.start + at, token.start + at + length, TokenType.OPERATOR);
                    part.text = run.substring(at, at + length);
                    merged.add(insert++, part);
                    at += length;
                }
            }
            return merged;
        }

        private boolean known(String operator) {
            return BINARY.contains(operator) || OTHER_OPERATORS.contains(operator);
        }

        private boolean operatorChar(Token token) {
            return token != null && token.angle == 0 && (token.type == TokenType.OPERATOR
                    || (token.type == TokenType.PUNCTUATION && token.text.equals(".")));
        }

        /** Decides for each operator of a line whether it gets a space on each side. */
        private void spaceOperators(List<Token> tokens, int from, Open top) {
            int less = 0;
            int greater = 0;
            boolean logical = false;
            for (int i = from; i < tokens.size(); i++) {
                Token token = tokens.get(i);
                less += token.operator() && token.text.equals("<") ? 1 : 0;
                greater += token.operator() && token.text.equals(">") ? 1 : 0;
                logical |= token.operator() && (token.text.equals("&&") || token.text.equals("||"));
            }
            Deque<Character> around = new ArrayDeque<>();
            if (top != null) {
                around.push(top.kind);
            }
            for (int i = from; i < tokens.size(); i++) {
                Token token = tokens.get(i);
                if (token.open()) {
                    around.push(token.text.charAt(0));
                } else if (token.close() && !around.isEmpty()) {
                    around.pop();
                }
                Token previous = i > from ? tokens.get(i - 1) : null;
                Token next = i + 1 < tokens.size() ? tokens.get(i + 1) : null;
                if (!token.operator() || (previous != null && previous.text.equals("operator"))) {
                    continue;
                }
                boolean value = valueLike(previous);
                boolean both = false;
                switch (token.text) {
                    case "&&":
                    case "||":
                        both = value || (previous != null && previous.angle == '>');
                        break;
                    case "->":
                        both = !memberArrow;
                        break;
                    case "?:":
                        both = elvis;
                        token.spaceAfter = true; // where it is no operator it marks a member that may be left out: "b?: string"
                        break;
                    case "+":
                    case "-":
                        both = value && !cast(tokens, i, from);
                        break;
                    case "*":
                    case "&":
                        both = !pointers && value;
                        break;
                    case "/":
                    case "%":
                        both = value;
                        break;
                    case "|":
                    case "^":
                        both = !closureBars && value;
                        break;
                    case "<":
                    case ">":
                        // with both on a line they may be type arguments that were not recognised,
                        // unless the line has a condition in it
                        both = value && previous.type != TokenType.TYPE && next != null && (VALUES.contains(next.type) || next.is("("))
                                && !(generics && less > 0 && greater > 0 && !logical);
                        break;
                    case "?":
                        boolean optional = next == null || next.is(",") || next.is(")") || next.is("=")
                                || (next.is("(") && next.start == token.end);
                        Token colon = ternary && value && !optional ? ternaryColon(tokens, i) : null;
                        if (colon != null) {
                            both = true;
                            colon.ternary = true;
                            colon.spaceBefore = true;
                            colon.spaceAfter = true;
                        }
                        break;
                    case ":":
                        // "x: Int", "case 1: return", but not the colon of a slice, "a[1:2]"
                        token.spaceAfter |= !(sliceColons && !around.isEmpty() && around.peek() == '[');
                        break;
                    default:
                        both = BINARY.contains(token.text);
                }
                if (both) {
                    token.spaceBefore = true;
                    token.spaceAfter = true;
                }
            }
        }

        private boolean valueLike(Token token) {
            if (token == null || token.angle != 0) {
                return false;
            }
            return VALUES.contains(token.type) || token.is(")") || token.is("]")
                    || (token.type == TokenType.KEYWORD && VALUE_KEYWORDS.contains(token.text));
        }

        /** Whether the tokens before index {@code i} are a cast, "(int)", so that a sign and not a subtraction follows. */
        private boolean cast(List<Token> tokens, int i, int from) {
            return i - 3 >= from && tokens.get(i - 1).is(")") && tokens.get(i - 3).is("(")
                    && (tokens.get(i - 2).type == TokenType.TYPE || tokens.get(i - 2).type == TokenType.KEYWORD);
        }

        /** The colon that goes with the question mark at {@code index}, or null if the line has none. */
        private Token ternaryColon(List<Token> tokens, int index) {
            int depth = 0;
            for (int i = index + 1; i < tokens.size(); i++) {
                Token token = tokens.get(i);
                if (token.open()) {
                    depth++;
                } else if (token.close()) {
                    if (depth-- == 0) {
                        return null;
                    }
                } else if (depth == 0 && token.is(";")) {
                    return null;
                } else if (depth == 0 && token.is(":") && !token.ternary) {
                    return token;
                }
            }
            return null;
        }

        // ---- Spacing ----

        /** What goes between the token at {@code i} and the one before it, where {@code written} stood. */
        private String gap(List<Token> tokens, int i, String written) {
            Token a = tokens.get(i - 1);
            Token b = tokens.get(i);
            String kept = written.isEmpty() ? "" : keepAlignment ? written : " ";
            if (b.comment()) {
                // comments at the ends of lines are often lined up with one another
                return written.isEmpty() && b.text.startsWith("//") ? " " : written;
            }
            if (a.comment()) {
                return kept;
            }
            if (plain) {
                if (b.exact) {
                    return written;
                }
                if (b.is(",") || b.is(";")) {
                    return "";
                }
                if (a.is(",") || a.is(";")) {
                    return b.is("}") ? kept : " ";
                }
                if (b.is("{")) {
                    return a.open() ? kept : " ";
                }
                return b.tight ? "" : a.spaceAfter ? " " : kept;
            }
            if (b.is(")") || b.is("]") || a.is("(") || a.is("[") || b.is(",") || b.is(";")) {
                return "";
            }
            if (a.is(",") || a.is(";")) {
                return b.is("}") ? kept : " "; // "{ a; b; }" or "{a; b;}", as the first brace has it
            }
            if (b.is("(")) {
                if (a.type == TokenType.KEYWORD) {
                    return CONTROL.contains(a.text) ? " " : kept;
                }
                // "foo (x)" is "foo(x)", but in "a to (b)" the name is an operator
                if ((a.type == TokenType.FUNCTION || a.type == TokenType.IDENTIFIER) && !(i >= 2 && valueLike(tokens.get(i - 2)))) {
                    return "";
                }
            }
            if (b.is("{")) {
                if (a.is(")") || (braceInitializers && blockHead && i == lastCode && !a.open())) {
                    return " ";
                }
                if (a.type == TokenType.KEYWORD) {
                    return !braceInitializers || BLOCK_KEYWORDS.contains(a.text) ? " " : kept;
                }
                if (!braceInitializers && (NAMES.contains(a.type) || a.angle == '>')) {
                    return " ";
                }
            }
            if (a.is("}") && b.type == TokenType.KEYWORD) {
                return " ";
            }
            if (a.is(")") && (b.type == TokenType.KEYWORD
                    || (!pointers && (WORDS.contains(b.type) || (b.operator() && cast(tokens, i, 0)))))) {
                return " "; // "if (x) return", and after a cast
            }
            if (b.angle == '<') {
                return NAMES.contains(a.type) || a.is(".") || a.is("::") ? "" : kept;
            }
            if (a.angle == '<' || b.angle == '>') {
                return "";
            }
            if (a.angle == '>' && !a.tight && WORDS.contains(b.type)) {
                return " ";
            }
            return a.spaceAfter || b.spaceBefore ? " " : kept;
        }

        /** Whether a string has a template in it, {@code ${}, that is not closed where the string ends. */
        private boolean unfinishedTemplate(String string) {
            int depth = 0;
            for (int i = 0; i < string.length(); i++) {
                if (string.startsWith("${", i)) {
                    depth++;
                    i++;
                } else if (string.charAt(i) == '}' && depth > 0) {
                    depth--;
                }
            }
            return depth > 0;
        }

        /** A line of a comment with its indentation made wider or narrower by a number of cells. */
        private String shifted(String text, int cells) {
            int blanks = 0;
            while (blanks < text.length() && (text.charAt(blanks) == ' ' || text.charAt(blanks) == '\t')) {
                blanks++;
            }
            if (cells == 0 || blanks == text.length()) {
                return text;
            }
            return white(Math.max(0, width(text, blanks) + cells)) + text.substring(blanks);
        }

        /** White space that is a number of cells wide: tabs as far as they go where the options ask for tabs. */
        private String white(int cells) {
            int tab = options.tabSize();
            return options.useTabs() ? "\t".repeat(cells / tab) + " ".repeat(cells % tab) : " ".repeat(cells);
        }

        /** The cells that the first chars of a line take. */
        private int width(CharSequence line, int end) {
            char[] chars = new char[end];
            for (int i = 0; i < end; i++) {
                chars[i] = line.charAt(i);
            }
            return Cells.count(chars, 0, end, options.tabSize());
        }
    }
}
