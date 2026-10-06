package com.swingcraft4j.code.demo;

import com.swingcraft4j.code.lexer.Language;
import com.swingcraft4j.code.lexer.Languages;
import com.swingcraft4j.code.text.ArrayTextModel;
import com.swingcraft4j.code.text.TextModel;
import com.swingcraft4j.code.theme.CodeTheme;
import com.swingcraft4j.code.theme.CodeThemes;
import com.swingcraft4j.code.viewer.JCodeFindBar;
import com.swingcraft4j.code.viewer.JCodeViewer;

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
import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

/**
 * Shows {@link JCodeViewer} with a sample for each language, a file of your choice or a generated
 * million-line file.
 */
public final class DemoApp {

    private static final int BIG_FILE_LINES = 1_000_000;
    private static final String PLAIN_TEXT = "Plain text";

    private final JFrame frame = new JFrame("SwingCraft Code - viewer demo");
    private final JCodeViewer viewer = new JCodeViewer();
    private final JLabel status = new JLabel(" ");
    private final JComboBox<String> languages = new JComboBox<>();
    /** Whether the document on show is one of the bundled samples rather than the user's own. */
    private boolean showingSample;
    /** Set while the picker is updated to match a loaded document, not changed by the user. */
    private boolean syncingLanguages;

    public static void main(String[] args) {
        EventQueue.invokeLater(() -> {
            DemoLookAndFeel.install();
            new DemoApp().show();
        });
    }

    private void show() {
        Language java = Languages.byId("java").orElse(null);

        JButton sample = new JButton("Sample");
        sample.addActionListener(e -> loadSample(selectedLanguage() != null ? selectedLanguage() : java));
        JButton open = new JButton("Open...");
        open.addActionListener(e -> openFile());
        JButton big = new JButton("1M lines");
        big.addActionListener(e -> load("Generated", () -> ArrayTextModel.of(Samples.generateJava(BIG_FILE_LINES)), java, false));

        JCheckBox wrap = new JCheckBox("Wrap");
        wrap.addActionListener(e -> viewer.setLineWrap(wrap.isSelected()));
        JCheckBox words = new JCheckBox("At words");
        words.setToolTipText("Break wrapped lines after a blank instead of anywhere");
        words.addActionListener(e -> viewer.setWrapStyleWord(words.isSelected()));
        JCheckBox rounded = new JCheckBox("Rounded selection", true);
        rounded.addActionListener(e -> viewer.setRoundedSelection(rounded.isSelected()));
        JComboBox<CodeTheme> themes = new JComboBox<>(CodeThemes.all().toArray(new CodeTheme[0]));
        themes.setToolTipText("Theme");
        themes.setMaximumSize(themes.getPreferredSize());
        themes.addActionListener(e -> {
            CodeTheme theme = (CodeTheme) themes.getSelectedItem();
            viewer.setTheme(theme);
            DemoLookAndFeel.follow(theme);
        });
        // start on the theme the component picked for the Look and Feel
        for (int i = 0; i < themes.getItemCount(); i++) {
            if (themes.getItemAt(i).name().equals(viewer.getTheme().name())) {
                themes.setSelectedIndex(i);
            }
        }
        JCheckBox lineNumbers = new JCheckBox("Line numbers", true);
        lineNumbers.addActionListener(e -> viewer.setLineNumbersVisible(lineNumbers.isSelected()));

        languages.addItem(PLAIN_TEXT);
        for (Language language : Languages.installed()) {
            languages.addItem(language.displayName());
        }
        languages.setMaximumSize(languages.getPreferredSize());
        languages.addActionListener(e -> languageChosen());

        JToolBar toolBar = new JToolBar();
        toolBar.setFloatable(false);
        toolBar.add(sample);
        toolBar.add(open);
        toolBar.add(big);
        toolBar.addSeparator();
        toolBar.add(wrap);
        toolBar.add(words);
        toolBar.add(rounded);
        toolBar.add(lineNumbers);
        toolBar.addSeparator();
        toolBar.add(languages);
        toolBar.add(themes);

        frame.add(toolBar, BorderLayout.NORTH);
        frame.add(new JScrollPane(viewer), BorderLayout.CENTER);
        // Ctrl+F in the viewer opens the find bar
        JPanel south = new JPanel(new BorderLayout());
        south.add(new JCodeFindBar(viewer), BorderLayout.NORTH);
        south.add(status, BorderLayout.SOUTH);
        frame.add(south, BorderLayout.SOUTH);
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.setSize(1000, 700);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);

        loadSample(java);
    }

    /** Shows the sample of the chosen language, or only re-highlights a document of the user's own. */
    private void languageChosen() {
        if (syncingLanguages) {
            return;
        }
        Language language = selectedLanguage();
        if (showingSample && language != null) {
            loadSample(language);
        } else {
            viewer.setLanguage(language);
        }
    }

    private void loadSample(Language language) {
        load(language.displayName() + " sample", () -> readSample(language.id()), language, true);
    }

    private void openFile() {
        JFileChooser chooser = new JFileChooser();
        if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
            Path file = chooser.getSelectedFile().toPath();
            String name = file.getFileName().toString();
            load(name, () -> ArrayTextModel.load(file, StandardCharsets.UTF_8), Languages.forFileName(name).orElse(null), false);
        }
    }

    private void load(String name, Callable<TextModel> source, Language language, boolean sample) {
        status.setText("Loading " + name + "...");
        long started = System.nanoTime();
        new SwingWorker<TextModel, Void>() {
            @Override
            protected TextModel doInBackground() throws Exception {
                return source.call();
            }

            @Override
            protected void done() {
                try {
                    TextModel model = get();
                    long loaded = System.nanoTime();
                    viewer.setDocument(model, language);
                    showingSample = sample;
                    syncingLanguages = true;
                    languages.setSelectedItem(language != null ? language.displayName() : PLAIN_TEXT);
                    syncingLanguages = false;
                    status.setText(String.format("%s - %,d lines, %,d chars - loaded in %d ms, laid out in %d ms",
                            name, model.lineCount(), model.length(),
                            (loaded - started) / 1_000_000, (System.nanoTime() - loaded) / 1_000_000));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException e) {
                    status.setText(" ");
                    JOptionPane.showMessageDialog(frame, e.getCause().toString(), "Could not load " + name,
                            JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private Language selectedLanguage() {
        Object name = languages.getSelectedItem();
        return Languages.installed().stream()
                .filter(language -> language.displayName().equals(name))
                .findFirst().orElse(null);
    }

    private static TextModel readSample(String languageId) throws IOException {
        return ArrayTextModel.of(Samples.read(languageId));
    }
}
