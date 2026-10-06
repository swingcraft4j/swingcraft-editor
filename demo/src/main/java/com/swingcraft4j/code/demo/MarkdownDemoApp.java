package com.swingcraft4j.code.demo;

import com.swingcraft4j.code.autocomplete.AutoCompletion;
import com.swingcraft4j.code.editor.JCodeEditor;
import com.swingcraft4j.code.lexer.Languages;
import com.swingcraft4j.code.theme.CodeTheme;
import com.swingcraft4j.code.theme.CodeThemes;
import com.swingcraft4j.markdown.JMarkdownPreview;

import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JToolBar;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.EventQueue;
import java.io.IOException;

/**
 * Shows {@link JMarkdownPreview} beside a {@link JCodeEditor}: Markdown is edited at the left
 * and shown at the right as the page it describes, a moment after each pause in typing.
 */
public final class MarkdownDemoApp {

    private final JFrame frame = new JFrame("SwingCraft4j Editor - Markdown preview demo");
    private final JCodeEditor editor = new JCodeEditor();
    private final JMarkdownPreview preview = new JMarkdownPreview();

    public static void main(String[] args) {
        EventQueue.invokeLater(() -> {
            DemoLookAndFeel.install();
            new MarkdownDemoApp().show();
        });
    }

    private void show() {
        JComboBox<CodeTheme> themes = new JComboBox<>(CodeThemes.all().toArray(new CodeTheme[0]));
        themes.setToolTipText("Theme");
        themes.setFocusable(false);
        themes.setMaximumSize(themes.getPreferredSize());
        themes.addActionListener(e -> {
            CodeTheme theme = (CodeTheme) themes.getSelectedItem();
            // the preview takes the theme from the editor, for its code blocks
            editor.setTheme(theme);
            DemoLookAndFeel.follow(theme);
        });
        for (int i = 0; i < themes.getItemCount(); i++) {
            if (themes.getItemAt(i).name().equals(editor.getTheme().name())) {
                themes.setSelectedIndex(i);
            }
        }

        JToolBar toolBar = new JToolBar();
        toolBar.setFloatable(false);
        toolBar.setBorder(BorderFactory.createEmptyBorder(DemoFonts.GAP, DemoFonts.GAP, DemoFonts.GAP, DemoFonts.GAP));
        toolBar.add(themes);
        DemoFonts.addTo(toolBar, editor);

        editor.setLineWrap(true);
        editor.setWrapStyleWord(true);
        editor.setLanguage(Languages.byId("markdown").orElse(null));
        try {
            editor.setText(Samples.read("markdown"));
        } catch (IOException e) {
            JOptionPane.showMessageDialog(frame, e.toString(), "Could not load the sample", JOptionPane.ERROR_MESSAGE);
        }
        AutoCompletion.install(editor);
        preview.follow(editor);
        // the font picked for the editor is the font of the whole preview, not of its code alone
        editor.addPropertyChangeListener("font", e -> preview.setFont(editor.getFont()));

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new JScrollPane(editor), new JScrollPane(preview));
        split.setResizeWeight(0.5);
        frame.add(toolBar, BorderLayout.NORTH);
        frame.add(split, BorderLayout.CENTER);
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.setSize(1200, 760);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        split.setDividerLocation(0.5);
        editor.requestFocusInWindow();
    }
}
