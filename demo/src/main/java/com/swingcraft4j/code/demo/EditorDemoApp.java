package com.swingcraft4j.code.demo;

import com.swingcraft4j.code.autocomplete.AutoCompletion;
import com.swingcraft4j.code.autocomplete.Completion;
import com.swingcraft4j.code.autocomplete.CompletionKind;
import com.swingcraft4j.code.autocomplete.ParameterHints;
import com.swingcraft4j.code.autocomplete.CompletionProvider;
import com.swingcraft4j.code.autocomplete.CompletionRequest;
import com.swingcraft4j.code.editor.JCodeEditor;
import com.swingcraft4j.code.lexer.Language;
import com.swingcraft4j.code.lexer.Languages;
import com.swingcraft4j.code.text.ArrayTextModel;
import com.swingcraft4j.code.text.GapTextModel;
import com.swingcraft4j.code.text.TextModel;
import com.swingcraft4j.code.viewer.JCodeFindBar;

import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.SwingWorker;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.EventQueue;
import java.awt.Toolkit;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

/** Shows {@link JCodeEditor}: edit a sample for each language, or open and save a file of your own. */
public final class EditorDemoApp {

    private static final int BIG_FILE_LINES = 1_000_000;

    private final JFrame frame = new JFrame("SwingCraft4j Editor - editor demo");
    private final JCodeEditor editor = new JCodeEditor();
    private final JLabel status = new JLabel(" ");
    /** A language picked here shows its sample. */
    private final DemoMenus.LanguageMenu languages = new DemoMenus.LanguageMenu(null, this::showSample);
    private Path file;
    /** Extra text for the status bar about the document on show. */
    private String documentNote = "";

    public static void main(String[] args) {
        EventQueue.invokeLater(() -> {
            DemoLookAndFeel.install();
            new EditorDemoApp().show();
        });
    }

    private void show() {
        editor.addSelectionListener(e -> showPosition());
        // a star in the title while there are changes that have not been saved
        editor.addPropertyChangeListener("modified", e -> showTitle());
        // suggestions open while typing, or on Ctrl+Space
        AutoCompletion completion = AutoCompletion.install(editor);
        completion.addProvider(new MemberProvider());
        // problems underlined in the text and marked in the gutter, found again after each pause in typing
        editor.setMarkerProvider(DemoLinter::check);
        // a hint with the parameters while the caret is inside the parentheses of a call
        ParameterHints.install(editor, PARAMETERS::get);

        JPanel south = new JPanel(new BorderLayout());
        south.add(new JCodeFindBar(editor), BorderLayout.NORTH);
        south.add(status, BorderLayout.SOUTH);
        frame.setJMenuBar(menuBar()); // after the find bar, which gives the editor the action that opens it
        frame.add(new JScrollPane(editor), BorderLayout.CENTER);
        frame.add(south, BorderLayout.SOUTH);
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.setSize(1150, 700);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);

        Language first = Languages.installed().get(0);
        languages.select(first);
        showSample(first);
        editor.requestFocusInWindow();
    }

    private JMenuBar menuBar() {
        int menuKey = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        JMenu file = DemoMenus.menu("File", 'F');
        file.add(DemoMenus.item("Open...", KeyStroke.getKeyStroke(KeyEvent.VK_O, menuKey), this::open));
        file.add(DemoMenus.item("Save", KeyStroke.getKeyStroke(KeyEvent.VK_S, menuKey), this::save));
        file.addSeparator();
        file.add(DemoMenus.item("Generate 1M Lines", null, this::showGenerated));

        JMenu edit = DemoMenus.menu("Edit", 'E');
        edit.add(DemoMenus.item("Undo", editor, JCodeEditor.ACTION_UNDO));
        edit.add(DemoMenus.item("Redo", editor, JCodeEditor.ACTION_REDO));
        edit.addSeparator();
        edit.add(DemoMenus.item("Cut", editor, JCodeEditor.ACTION_CUT));
        edit.add(DemoMenus.item("Copy", editor, JCodeEditor.ACTION_COPY));
        edit.add(DemoMenus.item("Paste", editor, JCodeEditor.ACTION_PASTE));
        edit.add(DemoMenus.item("Select All", editor, JCodeEditor.ACTION_SELECT_ALL));
        edit.addSeparator();
        edit.add(DemoMenus.item("Find...", editor, JCodeEditor.ACTION_FIND));
        edit.add(DemoMenus.item("Go to Line...", editor, JCodeEditor.ACTION_GO_TO_LINE));
        edit.addSeparator();
        edit.add(DemoMenus.item("Format", editor, JCodeEditor.ACTION_FORMAT));
        edit.add(DemoMenus.item("Toggle Comment", editor, JCodeEditor.ACTION_TOGGLE_COMMENT));
        edit.add(DemoMenus.item("Duplicate Lines", editor, JCodeEditor.ACTION_DUPLICATE_LINES));
        edit.addSeparator();
        edit.add(DemoMenus.option("Read Only", !editor.isEditable(), on -> editor.setEditable(!on)));

        JMenu view = DemoMenus.view(editor);
        view.add(DemoMenus.option("Current Line", editor.isHighlightCurrentLine(), editor::setHighlightCurrentLine));

        // the problems the linter of the demo finds
        JMenu markers = DemoMenus.menu("Markers", 'M');
        markers.add(DemoMenus.item("Next Marker", editor, JCodeEditor.ACTION_NEXT_MARKER));
        markers.add(DemoMenus.item("Previous Marker", editor, JCodeEditor.ACTION_PREVIOUS_MARKER));
        markers.addSeparator();
        markers.add(DemoMenus.option("Dots in the Gutter", editor.isMarkerDots(), editor::setMarkerDots));
        markers.add(DemoMenus.option("Stripe at the Right", editor.isMarkerStripe(), editor::setMarkerStripe));

        JMenuBar menuBar = new JMenuBar();
        menuBar.add(file);
        menuBar.add(edit);
        menuBar.add(view);
        menuBar.add(markers);
        menuBar.add(languages.menu());
        menuBar.add(DemoMenus.themes(editor.getTheme(), editor::setTheme));
        menuBar.add(DemoMenus.fonts(editor));
        return menuBar;
    }

    private void showSample(Language language) {
        if (language == null) {
            return;
        }
        try {
            showDocument(new GapTextModel(Samples.read(language.id())), language, null);
        } catch (IOException e) {
            showError("Could not load the sample", e);
        }
    }

    /** Shows a document and brings the language menu and the status bar in line with it. */
    private void showDocument(TextModel model, Language language, Path source) {
        file = source;
        documentNote = "";
        editor.setDocument(model, language);
        showTitle();
        languages.select(language);
        showPosition();
        editor.requestFocusInWindow();
    }

    /** Builds a million-line document off the event thread, then shows it for editing. */
    private void showGenerated() {
        status.setText(" Generating " + String.format("%,d", BIG_FILE_LINES) + " lines...");
        long started = System.nanoTime();
        new SwingWorker<GapTextModel, Void>() {
            @Override
            protected GapTextModel doInBackground() {
                return new GapTextModel(Samples.generateJava(BIG_FILE_LINES));
            }

            @Override
            protected void done() {
                try {
                    GapTextModel model = get();
                    long built = System.nanoTime();
                    showDocument(model, Languages.byId("java").orElse(null), null);
                    documentNote = String.format("  -  generated in %d ms, shown in %d ms",
                            (built - started) / 1_000_000, (System.nanoTime() - built) / 1_000_000);
                    showPosition();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException e) {
                    showError("Could not generate the document", e.getCause());
                }
            }
        }.execute();
    }

    private void open() {
        JFileChooser chooser = new JFileChooser();
        if (chooser.showOpenDialog(frame) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Path chosen = chooser.getSelectedFile().toPath();
        try {
            TextModel loaded = ArrayTextModel.load(chosen, StandardCharsets.UTF_8);
            showDocument(loaded, Languages.forFileName(chosen.getFileName().toString()).orElse(null), chosen);
        } catch (IOException e) {
            showError("Could not open " + chosen.getFileName(), e);
        }
    }

    private void save() {
        if (file == null) {
            JFileChooser chooser = new JFileChooser();
            if (chooser.showSaveDialog(frame) != JFileChooser.APPROVE_OPTION) {
                return;
            }
            file = chooser.getSelectedFile().toPath();
        }
        try {
            Files.writeString(file, editor.getText(), StandardCharsets.UTF_8);
            editor.markSaved();
            showTitle();
            status.setText("Saved " + file);
        } catch (IOException e) {
            showError("Could not save " + file.getFileName(), e);
        }
    }

    private void showTitle() {
        String name = file != null ? file.getFileName().toString() : "sample";
        frame.setTitle((editor.isModified() ? "* " : "") + name + " - SwingCraft4j Editor - editor demo");
    }

    private void showPosition() {
        TextModel model = editor.getModel();
        int caret = editor.getCaretPosition();
        int line = model.lineOfOffset(caret);
        int selected = editor.getSelectionEnd() - editor.getSelectionStart();
        status.setText(String.format(" Ln %,d, Col %,d  -  %,d lines%s%s", line + 1, caret - model.lineStart(line) + 1,
                model.lineCount(), selected > 0 ? String.format("  -  %,d selected", selected) : "",
                file != null ? "  -  " + file : documentNote));
    }

    /** The parameters of a few functions, for the parameter hints; a real application would look them up. */
    private static final Map<String, List<String>> PARAMETERS = Map.of(
            "add", List.of("E item"),
            "get", List.of("int index"),
            "equals", List.of("Object other"),
            "substring", List.of("int beginIndex", "int endIndex"),
            "requireNonNull", List.of("T object", "String message"),
            "Item", List.of("String name", "int quantity", "double price"),
            "println", List.of("Object value"));

    /**
     * Shows how an application adds suggestions of its own. A real one would look up the
     * members of the type before the dot; this offers the same few after any dot.
     */
    private static final class MemberProvider implements CompletionProvider {

        private static final List<Completion> MEMBERS = List.of(
                method("size", "int", "size()", "Returns the number of elements."),
                method("length", "int", "length()", "Returns the length of the string."),
                method("isEmpty", "boolean", "isEmpty()", "Returns true if there are no elements."),
                method("add", "boolean", "add(${item})", "Appends the item to the end of the list."),
                method("get", "E", "get(${index})", "Returns the element at the given position.\n\n"
                        + "Throws IndexOutOfBoundsException if the index is out of range."),
                method("equals", "boolean", "equals(${other})", "Tells whether the other object is equal to this one."),
                method("forEach", "void", "forEach(${item} -> $0)", "Performs the action for each element."),
                method("stream", "Stream<E>", "stream()", "Returns a sequential stream over the elements."),
                method("toString", "String", "toString()", "Returns a string representation of the object."),
                new Completion("length", CompletionKind.FIELD).withDetail("int")
                        .withDocumentation("The number of elements of an array."));

        private static Completion method(String name, String type, String template, String documentation) {
            return new Completion(name, CompletionKind.METHOD).withDetail(type).withTemplate(template)
                    .withDocumentation(documentation);
        }

        @Override
        public List<Completion> complete(CompletionRequest request) {
            return request.charBeforePrefix() == '.' ? MEMBERS : List.of();
        }

        @Override
        public String triggerCharacters() {
            return "."; // open as soon as a dot is typed
        }
    }

    private void showError(String title, Throwable e) {
        JOptionPane.showMessageDialog(frame, e.toString(), title, JOptionPane.ERROR_MESSAGE);
    }
}
