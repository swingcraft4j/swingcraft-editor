package com.swingcraft4j.code.demo;

import com.swingcraft4j.code.autocomplete.AutoCompletion;
import com.swingcraft4j.code.autocomplete.Completion;
import com.swingcraft4j.code.autocomplete.CompletionKind;
import com.swingcraft4j.code.autocomplete.CompletionProvider;
import com.swingcraft4j.code.autocomplete.CompletionRequest;
import com.swingcraft4j.code.autocomplete.ParameterHints;
import com.swingcraft4j.code.editor.JCodeEditor;
import com.swingcraft4j.code.lexer.Languages;
import com.swingcraft4j.code.text.GapTextModel;

import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.EventQueue;
import java.awt.Toolkit;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.Map;

/**
 * Shows code completion on a script of JavaScript: the keywords, the snippets and the words of
 * the document that come with {@link AutoCompletion}, and beside them what an application adds
 * itself: the globals of the language, the members of the one before a dot, and a hint with
 * the parameters of a call.
 */
public final class CompletionDemoApp {

    private static final String SCRIPT = """
            // Code completion, tried on JavaScript. Type at the end of this script:
            //
            //   con       a global of the language, with what it is beside the list
            //   tp        totalPrice: the letters typed need not be the start of the word
            //   Math.     the members of Math open as soon as the dot is typed
            //   cart.     so do those of an array, for any name that is not a global
            //   forof     a snippet: Enter inserts it, Tab goes from one stop to the next
            //   round(    a hint with the parameters while the caret is in the parentheses
            //
            // Ctrl+Space opens the list anywhere.

            const cart = [
                { name: "Keyboard", quantity: 1, price: 49.9 },
                { name: "Mouse", quantity: 2, price: 19.5 },
                { name: "Cable", quantity: 3, price: 4.25 }
            ];

            function totalPrice(items) {
                return items.reduce((sum, item) => sum + item.quantity * item.price, 0);
            }

            function describe(item) {
                return item.name + " x " + item.quantity;
            }

            const expensive = cart.filter(item => item.price > 10);
            console.log(expensive.map(describe).join(", "));
            console.log("Total:", Math.round(totalPrice(cart)));

            """;

    private final JFrame frame = new JFrame("SwingCraft4j Editor - code completion demo");
    private final JCodeEditor editor = new JCodeEditor();
    private AutoCompletion completion;

    public static void main(String[] args) {
        EventQueue.invokeLater(() -> {
            DemoLookAndFeel.install();
            new CompletionDemoApp().show();
        });
    }

    private void show() {
        editor.setDocument(new GapTextModel(SCRIPT), Languages.byId("javascript").orElseThrow());
        // the keywords, the snippets of JavaScript and the words of the script
        completion = AutoCompletion.install(editor);
        completion.addProvider(new GlobalProvider());
        completion.addProvider(new MemberProvider());
        ParameterHints.install(editor, PARAMETERS::get);

        JLabel status = new JLabel(" Type a word, a dot after a name, or press Ctrl+Space");
        status.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
        frame.setJMenuBar(menuBar());
        frame.add(new JScrollPane(editor), BorderLayout.CENTER);
        frame.add(status, BorderLayout.SOUTH);
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.setSize(1000, 700);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);

        // the caret on the empty line at the end, where there is room to try
        int end = editor.getModel().length();
        editor.select(end, end);
        editor.requestFocusInWindow();
    }

    private JMenuBar menuBar() {
        int menuKey = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        JMenu options = DemoMenus.menu("Completion", 'C');
        options.add(DemoMenus.item("Show Suggestions", KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, menuKey),
                () -> completion.showCompletions()));
        options.addSeparator();
        // off, the list opens on Ctrl+Space only
        options.add(DemoMenus.option("Open While Typing", true, completion::setAutoActivation));
        options.add(DemoMenus.option("Open at the First Letter", false,
                on -> completion.setAutoActivationLength(on ? 1 : 2)));

        JMenuBar menuBar = new JMenuBar();
        menuBar.add(options);
        menuBar.add(DemoMenus.view(editor));
        menuBar.add(DemoMenus.themes(editor.getTheme(), editor::setTheme));
        menuBar.add(DemoMenus.fonts(editor));
        return menuBar;
    }

    /** The parameters of the functions of the script and of a few of the language, by name. */
    private static final Map<String, List<String>> PARAMETERS = Map.ofEntries(
            Map.entry("totalPrice", List.of("items")),
            Map.entry("describe", List.of("item")),
            Map.entry("log", List.of("...data")),
            Map.entry("round", List.of("x")),
            Map.entry("max", List.of("...values")),
            Map.entry("parse", List.of("text", "reviver?")),
            Map.entry("stringify", List.of("value", "replacer?", "space?")),
            Map.entry("filter", List.of("predicate(item, index)")),
            Map.entry("map", List.of("callback(item, index)")),
            Map.entry("reduce", List.of("callback(accumulator, item)", "initialValue?")),
            Map.entry("join", List.of("separator?")),
            Map.entry("push", List.of("...items")),
            Map.entry("setTimeout", List.of("handler", "milliseconds?")));

    private static Completion method(String name, String type, String template, String documentation) {
        return new Completion(name, CompletionKind.METHOD).withDetail(type).withTemplate(template)
                .withDocumentation(documentation);
    }

    private static Completion field(String name, String type, String documentation) {
        return new Completion(name, CompletionKind.FIELD).withDetail(type).withDocumentation(documentation);
    }

    /** Offers the globals of JavaScript wherever a word is typed that is not a member of something. */
    private static final class GlobalProvider implements CompletionProvider {

        private static final List<Completion> GLOBALS = List.of(
                global("console", "Writes messages to the console of the browser or of Node."),
                global("Math", "The mathematical constants and functions."),
                global("JSON", "Reads and writes JSON."),
                method("setTimeout", "number", "setTimeout(() => {\n\t$0\n}, ${milliseconds});",
                        "Calls the function once, after the given time."),
                method("parseInt", "number", "parseInt(${text}, ${radix})", "Reads an integer from a string."));

        private static Completion global(String name, String documentation) {
            return new Completion(name, CompletionKind.CLASS).withDetail("global").withDocumentation(documentation);
        }

        @Override
        public List<Completion> complete(CompletionRequest request) {
            return request.charBeforePrefix() == '.' ? List.of() : GLOBALS;
        }
    }

    /**
     * Offers the members of what stands before a dot: those of a global by its name, and those
     * of an array for any other name. A real application would find out the type instead.
     */
    private static final class MemberProvider implements CompletionProvider {

        private static final Map<String, List<Completion>> MEMBERS = Map.of(
                "console", List.of(
                        method("log", "void", "log(${message})", "Writes a message to the console."),
                        method("warn", "void", "warn(${message})", "Writes a warning to the console."),
                        method("error", "void", "error(${message})", "Writes an error to the console."),
                        method("table", "void", "table(${data})", "Shows an array or an object as a table.")),
                "Math", List.of(
                        field("PI", "number", "The ratio of the circumference of a circle to its diameter."),
                        method("round", "number", "round(${x})", "Rounds to the nearest integer."),
                        method("floor", "number", "floor(${x})", "The largest integer that is not above x."),
                        method("max", "number", "max(${a}, ${b})", "The largest of the values."),
                        method("random", "number", "random()", "A random number from 0 up to, not including, 1.")),
                "JSON", List.of(
                        method("parse", "any", "parse(${text})", "Reads a value from a string of JSON.\n\n"
                                + "Throws SyntaxError if the string is not JSON."),
                        method("stringify", "string", "stringify(${value})", "Writes a value as a string of JSON.")));

        private static final List<Completion> ARRAY = List.of(
                field("length", "number", "The number of elements."),
                method("filter", "Array", "filter(${item} => $0)", "The elements the function returns true for."),
                method("map", "Array", "map(${item} => $0)", "What the function returns for each element."),
                method("reduce", "any", "reduce((${sum}, ${item}) => $0, ${initial})",
                        "Brings the elements down to one value, from the left."),
                method("forEach", "void", "forEach(${item} => {\n\t$0\n});", "Calls the function for each element."),
                method("push", "number", "push(${item})", "Appends the item, and returns the new length."),
                method("join", "string", "join(${separator})", "The elements as one string, with the separator between."),
                method("includes", "boolean", "includes(${item})", "Tells whether the item is among the elements."));

        /** After a dot only the members are right, not the keywords or the words of the script. */
        @Override
        public boolean isExclusive(CompletionRequest request) {
            return request.charBeforePrefix() == '.';
        }

        @Override
        public List<Completion> complete(CompletionRequest request) {
            String qualifier = request.qualifier();
            // the dot of a number such as 4.25 has no members after it
            if (request.charBeforePrefix() != '.' || qualifier.isEmpty() || Character.isDigit(qualifier.charAt(0))) {
                return List.of();
            }
            return MEMBERS.getOrDefault(qualifier, ARRAY);
        }

        @Override
        public String triggerCharacters() {
            return "."; // open as soon as a dot is typed
        }
    }
}
