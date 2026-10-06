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
import com.swingcraft4j.code.theme.CodeTheme;
import com.swingcraft4j.code.theme.CodeThemes;
import com.swingcraft4j.code.viewer.JCodeFindBar;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JToolBar;
import javax.swing.SwingWorker;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.EventQueue;
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
    private final JComboBox<String> languages = new JComboBox<>();
    private Path file;
    /** Extra text for the status bar about the document on show. */
    private String documentNote = "";
    /** Set while the picker is changed to match a document, not by the user. */
    private boolean syncingLanguages;


    public static void main(String[] args) {
        EventQueue.invokeLater(() -> {
            DemoLookAndFeel.install();
            new EditorDemoApp().show();
        });
    }

    private void show() {
        JButton open = new JButton("Open...");
        open.addActionListener(e -> open());
        JButton save = new JButton("Save");
        save.addActionListener(e -> save());
        JButton big = new JButton("1M lines");
        big.addActionListener(e -> showGenerated());
        JButton undo = new JButton("Undo");
        undo.addActionListener(e -> editor.undo());
        JButton redo = new JButton("Redo");
        redo.addActionListener(e -> editor.redo());
        for (JButton button : new JButton[]{open, save, big, undo, redo}) {
            button.setFocusable(false); // keep the caret in the editor
        }

        JCheckBox wrap = new JCheckBox("Wrap");
        wrap.addActionListener(e -> editor.setLineWrap(wrap.isSelected()));
        JCheckBox words = new JCheckBox("At words");
        words.setToolTipText("Break wrapped lines after a blank instead of anywhere");
        words.addActionListener(e -> editor.setWrapStyleWord(words.isSelected()));
        JCheckBox rounded = new JCheckBox("Rounded selection", true);
        rounded.addActionListener(e -> editor.setRoundedSelection(rounded.isSelected()));
        JComboBox<CodeTheme> themes = new JComboBox<>(CodeThemes.all().toArray(new CodeTheme[0]));
        themes.setToolTipText("Theme");
        themes.setMaximumSize(themes.getPreferredSize());
        themes.addActionListener(e -> {
            CodeTheme theme = (CodeTheme) themes.getSelectedItem();
            editor.setTheme(theme);
            DemoLookAndFeel.follow(theme);
        });
        // start on the theme the component picked for the Look and Feel
        for (int i = 0; i < themes.getItemCount(); i++) {
            if (themes.getItemAt(i).name().equals(editor.getTheme().name())) {
                themes.setSelectedIndex(i);
            }
        }
        JCheckBox readOnly = new JCheckBox("Read only");
        readOnly.addActionListener(e -> editor.setEditable(!readOnly.isSelected()));

        for (Language language : Languages.installed()) {
            languages.addItem(language.displayName());
        }
        languages.setMaximumSize(languages.getPreferredSize());
        languages.addActionListener(e -> {
            if (!syncingLanguages) {
                showSample(selectedLanguage());
            }
        });

        JToolBar toolBar = new JToolBar();
        toolBar.setFloatable(false);
        toolBar.setBorder(BorderFactory.createEmptyBorder(DemoFonts.GAP, DemoFonts.GAP, DemoFonts.GAP, DemoFonts.GAP));
        toolBar.add(open);
        toolBar.add(save);
        toolBar.add(big);
        toolBar.addSeparator();
        toolBar.add(undo);
        toolBar.add(redo);
        toolBar.addSeparator();
        toolBar.add(wrap);
        toolBar.add(words);
        toolBar.add(rounded);
        toolBar.add(readOnly);
        toolBar.addSeparator();
        toolBar.add(languages);
        toolBar.add(Box.createHorizontalStrut(DemoFonts.GAP));
        toolBar.add(themes);
        DemoFonts.addTo(toolBar, editor);

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
        frame.add(toolBar, BorderLayout.NORTH);
        frame.add(new JScrollPane(editor), BorderLayout.CENTER);
        frame.add(south, BorderLayout.SOUTH);
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.setSize(1150, 700);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);

        showSample(selectedLanguage());
        editor.requestFocusInWindow();
    }

    private Language selectedLanguage() {
        Object name = languages.getSelectedItem();
        return Languages.installed().stream()
                .filter(language -> language.displayName().equals(name))
                .findFirst().orElse(null);
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

    /** Shows a document and brings the language picker and the status bar in line with it. */
    private void showDocument(TextModel model, Language language, Path source) {
        file = source;
        documentNote = "";
        editor.setDocument(model, language);
        showTitle();
        if (language != null) {
            syncingLanguages = true;
            languages.setSelectedItem(language.displayName());
            syncingLanguages = false;
        }
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
