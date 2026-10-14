package com.swingcraft4j.code.demo;

import com.swingcraft4j.code.diff.UnifiedDiff;
import com.swingcraft4j.code.editor.JCodeEditor;
import com.swingcraft4j.code.lexer.Language;
import com.swingcraft4j.code.lexer.Languages;
import com.swingcraft4j.code.theme.CodeTheme;
import com.swingcraft4j.code.viewer.JCodeViewer;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JPanel;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.Timer;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.EventQueue;

/**
 * Compares two texts as {@code git diff} does: the original is edited at the left, the revised
 * version at the right, and below them the difference is shown as a unified diff, highlighted
 * as the language {@code diff}, a moment after each pause in typing.
 */
public final class DiffDemoApp {

    private static final String ORIGINAL = """
            import java.util.List;

            public class Cart {

                private final List<Item> items;

                public Cart(List<Item> items) {
                    this.items = items;
                }

                public double total() {
                    double sum = 0;
                    for (Item item : items) {
                        sum += item.price();
                    }
                    return sum;
                }

                public int size() {
                    return items.size();
                }
            }
            """;

    private static final String REVISED = """
            import java.util.ArrayList;
            import java.util.List;

            public class Cart {

                private final List<Item> items = new ArrayList<>();

                public void add(Item item) {
                    items.add(item);
                }

                public double total() {
                    return items.stream()
                            .mapToDouble(item -> item.price() * item.quantity())
                            .sum();
                }

                public int size() {
                    return items.size();
                }
            }
            """;

    /** How long typing must pause before the texts are compared again. */
    private static final int DELAY = 300;

    private final JFrame frame = new JFrame("SwingCraft4j Editor - diff demo");
    private final JCodeEditor original = new JCodeEditor();
    private final JCodeEditor revised = new JCodeEditor();
    private final JCodeViewer output = new JCodeViewer();
    private final JLabel status = new JLabel();
    private final Timer timer = new Timer(DELAY, e -> compare());
    private int context = 3;

    public static void main(String[] args) {
        EventQueue.invokeLater(() -> {
            DemoLookAndFeel.install();
            new DiffDemoApp().show();
        });
    }

    private void show() {
        Language java = Languages.byId("java").orElse(null);
        original.setLanguage(java);
        original.setText(ORIGINAL);
        revised.setLanguage(java);
        revised.setText(REVISED);
        output.setLanguage(Languages.byId("diff").orElse(null));
        timer.setRepeats(false);
        original.addEditListener(e -> timer.restart());
        revised.addEditListener(e -> timer.restart());
        // the font picked for the original is the font of all three
        original.addPropertyChangeListener("font", e -> {
            revised.setFont(original.getFont());
            output.setFont(original.getFont());
        });

        JSplitPane editors = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                titled("Original", original), titled("Revised", revised));
        editors.setResizeWeight(0.5);
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, editors, titled("git diff", output));
        split.setResizeWeight(0.5);
        status.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));

        frame.setJMenuBar(menuBar());
        frame.add(split, BorderLayout.CENTER);
        frame.add(status, BorderLayout.SOUTH);
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.setSize(1200, 860);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        editors.setDividerLocation(0.5);
        split.setDividerLocation(0.55);
        compare();
        revised.requestFocusInWindow();
    }

    /** Writes the difference of the two texts into the viewer below them, and counts its lines in the status bar. */
    private void compare() {
        String diff = UnifiedDiff.of(text(original), text(revised))
                .names("a/Cart.java", "b/Cart.java")
                .context(context)
                .format();
        output.setText(diff);
        int added = 0;
        int removed = 0;
        for (String line : diff.split("\n")) {
            if (line.startsWith("+") && !line.startsWith("+++ ")) {
                added++;
            } else if (line.startsWith("-") && !line.startsWith("--- ")) {
                removed++;
            }
        }
        status.setText(diff.isEmpty() ? "The texts are equal" : added + " lines added, " + removed + " removed");
    }

    private static String text(JCodeViewer viewer) {
        return viewer.getModel().getText(0, viewer.getModel().length());
    }

    private static JComponent titled(String title, JCodeViewer viewer) {
        JLabel label = new JLabel(title);
        label.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(label, BorderLayout.NORTH);
        panel.add(new JScrollPane(viewer), BorderLayout.CENTER);
        return panel;
    }

    private JMenuBar menuBar() {
        JMenuBar menuBar = new JMenuBar();
        menuBar.add(contextMenu());
        menuBar.add(DemoMenus.themes(original.getTheme(), this::setTheme));
        menuBar.add(DemoMenus.fonts(original));
        return menuBar;
    }

    /** How many unchanged lines are shown around each change. */
    private JMenu contextMenu() {
        JMenu menu = DemoMenus.menu("Context", 'C');
        ButtonGroup group = new ButtonGroup();
        for (int lines : new int[]{0, 1, 3, 5, 10}) {
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(lines + (lines == 1 ? " line" : " lines"), lines == context);
            item.addActionListener(e -> {
                context = lines;
                compare();
            });
            group.add(item);
            menu.add(item);
        }
        return menu;
    }

    private void setTheme(CodeTheme theme) {
        original.setTheme(theme);
        revised.setTheme(theme);
        output.setTheme(theme);
    }
}
