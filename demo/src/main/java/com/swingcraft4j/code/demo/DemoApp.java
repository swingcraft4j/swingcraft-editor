package com.swingcraft4j.code.demo;

import com.swingcraft4j.code.lexer.Language;
import com.swingcraft4j.code.lexer.Languages;
import com.swingcraft4j.code.text.ArrayTextModel;
import com.swingcraft4j.code.text.TextModel;
import com.swingcraft4j.code.viewer.JCodeFindBar;
import com.swingcraft4j.code.viewer.JCodeViewer;

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

    private final JFrame frame = new JFrame("SwingCraft4j Editor - viewer demo");
    private final JCodeViewer viewer = new JCodeViewer();
    private final JLabel status = new JLabel(" ");
    private final DemoMenus.LanguageMenu languages = new DemoMenus.LanguageMenu(PLAIN_TEXT, this::languageChosen);
    /** Whether the document on show is one of the bundled samples rather than the user's own. */
    private boolean showingSample;

    public static void main(String[] args) {
        EventQueue.invokeLater(() -> {
            DemoLookAndFeel.install();
            new DemoApp().show();
        });
    }

    private void show() {
        Language java = Languages.byId("java").orElse(null);

        frame.add(new JScrollPane(viewer), BorderLayout.CENTER);
        // Ctrl+F in the viewer opens the find bar
        JPanel south = new JPanel(new BorderLayout());
        south.add(new JCodeFindBar(viewer), BorderLayout.NORTH);
        south.add(status, BorderLayout.SOUTH);
        frame.add(south, BorderLayout.SOUTH);
        frame.setJMenuBar(menuBar(java)); // after the find bar, which gives the viewer the action that opens it
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.setSize(1150, 700);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);

        loadSample(java);
        viewer.requestFocusInWindow();
    }

    private JMenuBar menuBar(Language java) {
        int menuKey = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        JMenu file = DemoMenus.menu("File", 'F');
        file.add(DemoMenus.item("Open...", KeyStroke.getKeyStroke(KeyEvent.VK_O, menuKey), this::openFile));
        file.addSeparator();
        file.add(DemoMenus.item("Sample of the Language", null,
                () -> loadSample(languages.selected() != null ? languages.selected() : java)));
        file.add(DemoMenus.item("Generate 1M Lines", null,
                () -> load("Generated", () -> ArrayTextModel.of(Samples.generateJava(BIG_FILE_LINES)), java, false)));

        JMenu edit = DemoMenus.menu("Edit", 'E');
        edit.add(DemoMenus.item("Copy", viewer, JCodeViewer.ACTION_COPY));
        edit.add(DemoMenus.item("Select All", viewer, JCodeViewer.ACTION_SELECT_ALL));
        edit.addSeparator();
        edit.add(DemoMenus.item("Find...", viewer, JCodeViewer.ACTION_FIND));
        edit.add(DemoMenus.item("Go to Line...", viewer, JCodeViewer.ACTION_GO_TO_LINE));

        JMenu view = DemoMenus.view(viewer);
        view.addSeparator();
        view.add(DemoMenus.item("Collapse Fold", viewer, JCodeViewer.ACTION_COLLAPSE_FOLD));
        view.add(DemoMenus.item("Expand Fold", viewer, JCodeViewer.ACTION_EXPAND_FOLD));
        view.add(DemoMenus.item("Expand All Folds", viewer, JCodeViewer.ACTION_EXPAND_ALL_FOLDS));

        JMenuBar menuBar = new JMenuBar();
        menuBar.add(file);
        menuBar.add(edit);
        menuBar.add(view);
        menuBar.add(languages.menu());
        menuBar.add(DemoMenus.themes(viewer.getTheme(), viewer::setTheme));
        menuBar.add(DemoMenus.fonts(viewer));
        return menuBar;
    }

    /** Shows the sample of the chosen language, or only re-highlights a document of the user's own. */
    private void languageChosen(Language language) {
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
                    languages.select(language);
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

    private static TextModel readSample(String languageId) throws IOException {
        return ArrayTextModel.of(Samples.read(languageId));
    }
}
