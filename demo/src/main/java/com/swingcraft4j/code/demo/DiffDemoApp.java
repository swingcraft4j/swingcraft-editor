package com.swingcraft4j.code.demo;

import com.swingcraft4j.code.diff.DiffHighlighter;
import com.swingcraft4j.code.diff.Granularity;
import com.swingcraft4j.code.diff.UnifiedDiff;
import com.swingcraft4j.code.editor.JCodeEditor;
import com.swingcraft4j.code.lexer.Language;
import com.swingcraft4j.code.lexer.Languages;
import com.swingcraft4j.code.lexer.TokenType;
import com.swingcraft4j.code.theme.CodeTheme;
import com.swingcraft4j.code.theme.TokenStyle;
import com.swingcraft4j.code.viewer.JCodeViewer;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.Icon;
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
import java.awt.Color;
import java.awt.Component;
import java.awt.EventQueue;
import java.awt.Graphics;
import java.util.List;

/**
 * Compares two texts as {@code git diff} does: the original is edited at the left, the revised
 * version at the right, and below them the difference is shown as a unified diff, highlighted
 * as the language {@code diff}, a moment after each pause in typing. The lines that changed
 * get a green or red background in all three, and the chars or words that changed in them a
 * stronger one; the menu Highlight picks how, and in which colours.
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
    private final DiffHighlighter highlighter = new DiffHighlighter();
    private int context = 3;
    private boolean highlightEditors = true;
    private boolean highlightOutput = true;
    /** The theme picked in the menu, before the colours of the diff are laid over it. */
    private CodeTheme baseTheme;
    private DiffColors colors = DiffColors.THEME;
    private Strength strength = Strength.NORMAL;

    /**
     * The colours of a diff to choose from: a colour for what was put in and one for what was
     * taken away. The theme has its own; the others show how an application sets its own with
     * the builder of the theme, here faded into the background so that they suit a light and a
     * dark theme alike.
     */
    private enum DiffColors {
        THEME("From the Theme", null, null),
        CLASSIC("Green and Red", new Color(0x2DA44E), new Color(0xCF222E)),
        BLUE_ORANGE("Blue and Orange (Colour-Blind Safe)", new Color(0x1F77D0), new Color(0xE66A00)),
        BLUE_RED("Blue and Red", new Color(0x0969DA), new Color(0xD1242F)),
        TEAL_PURPLE("Teal and Purple", new Color(0x14A39A), new Color(0xA63DC9)),
        CYAN_MAGENTA("Cyan and Magenta", new Color(0x0FA3B1), new Color(0xC2185B)),
        MINT_CORAL("Mint and Coral", new Color(0x2EC4A0), new Color(0xF0604D)),
        LIME_PINK("Lime and Pink", new Color(0x7CB518), new Color(0xE84393)),
        GOLD_VIOLET("Gold and Violet", new Color(0xC99A06), new Color(0x7B4DE0)),
        SKY_AMBER("Sky and Amber", new Color(0x3AA0E8), new Color(0xD99A00));

        final String label;
        final Color inserted;
        final Color deleted;

        DiffColors(String label, Color inserted, Color deleted) {
            this.label = label;
            this.inserted = inserted;
            this.deleted = deleted;
        }

        /** The theme with the backgrounds of a diff in these colours and as strong as asked. */
        CodeTheme apply(CodeTheme theme, Strength strength) {
            if (inserted == null && strength == Strength.NORMAL) {
                return theme;
            }
            // the theme's own colours stand in where the choice has none
            Color in = inserted != null ? inserted : theme.style(TokenType.INSERTED).color();
            Color out = deleted != null ? deleted : theme.style(TokenType.DELETED).color();
            Color background = theme.background();
            // as much as the theme fades its own, times the strength
            float line = (theme.isDark() ? 0.16f : 0.12f) * strength.factor;
            float text = Math.min(0.6f, (theme.isDark() ? 0.34f : 0.28f) * strength.factor);
            CodeTheme.Builder builder = theme.toBuilder()
                    .insertedLineBackground(mix(background, in, line))
                    .insertedTextBackground(mix(background, in, text))
                    .deletedLineBackground(mix(background, out, line))
                    .deletedTextBackground(mix(background, out, text));
            if (inserted != null) {
                builder.style(TokenType.INSERTED, TokenStyle.of(readable(inserted, theme)))
                        .style(TokenType.DELETED, TokenStyle.of(readable(deleted, theme)));
            }
            return builder.build();
        }

        /**
         * A tone of the colour for text that stands on its own faded colour: lighter on a dark
         * theme, darker on a light one, as far as it takes for the text to be read.
         */
        private static Color readable(Color color, CodeTheme theme) {
            Color toward = theme.isDark() ? Color.WHITE : Color.BLACK;
            Color background = theme.background();
            Color tone = color;
            for (float part = 0.1f; contrast(tone, background) < 4.5 && part <= 0.8f; part += 0.1f) {
                tone = mix(color, toward, part);
            }
            return tone;
        }

        private static double contrast(Color a, Color b) {
            double la = luminance(a);
            double lb = luminance(b);
            return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
        }

        private static double luminance(Color color) {
            return 0.2126 * channel(color.getRed()) + 0.7152 * channel(color.getGreen()) + 0.0722 * channel(color.getBlue());
        }

        private static double channel(int value) {
            double c = value / 255.0;
            return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
        }

        /** Two squares, the colour put in and the colour taken away, to show beside the name in the menu. */
        Icon swatch() {
            return new Icon() {
                @Override
                public void paintIcon(Component c, Graphics g, int x, int y) {
                    Color in = inserted != null ? inserted : CodeTheme.light().style(TokenType.INSERTED).color();
                    Color out = deleted != null ? deleted : CodeTheme.light().style(TokenType.DELETED).color();
                    g.setColor(in);
                    g.fillRect(x, y + 2, 10, 10);
                    g.setColor(out);
                    g.fillRect(x + 12, y + 2, 10, 10);
                }

                @Override
                public int getIconWidth() {
                    return 22;
                }

                @Override
                public int getIconHeight() {
                    return 14;
                }
            };
        }
    }

    /** How strongly the backgrounds of a diff stand out from the page. */
    private enum Strength {
        SOFT("Soft", 0.6f),
        NORMAL("Normal", 1f),
        STRONG("Strong", 1.6f);

        final String label;
        final float factor;

        Strength(String label, float factor) {
            this.label = label;
            this.factor = factor;
        }
    }

    private static Color mix(Color from, Color to, float part) {
        return new Color(
                Math.round(from.getRed() + (to.getRed() - from.getRed()) * part),
                Math.round(from.getGreen() + (to.getGreen() - from.getGreen()) * part),
                Math.round(from.getBlue() + (to.getBlue() - from.getBlue()) * part));
    }

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
        baseTheme = original.getTheme();
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

    /**
     * Writes the difference of the two texts into the viewer below them, lays its backgrounds
     * on the three, and counts its lines in the status bar.
     */
    private void compare() {
        String left = text(original);
        String right = text(revised);
        String diff = UnifiedDiff.of(left, right)
                .names("a/Cart.java", "b/Cart.java")
                .context(context)
                .format();
        output.setText(diff);
        CodeTheme theme = output.getTheme();
        output.setHighlights(highlightOutput ? highlighter.patch(diff, theme) : List.of());
        if (highlightEditors) {
            DiffHighlighter.Sides sides = highlighter.compare(left, right, theme);
            original.setHighlights(sides.original());
            revised.setHighlights(sides.revised());
        } else {
            original.setHighlights(List.of());
            revised.setHighlights(List.of());
        }
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
        menuBar.add(highlightMenu());
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

    /** How the changes are marked: how finely, with or without line backgrounds, where, and in which colours. */
    private JMenu highlightMenu() {
        JMenu menu = DemoMenus.menu("Highlight", 'H');
        ButtonGroup granularities = new ButtonGroup();
        String[] labels = {"Changed Lines Only", "Changed Words", "Changed Chars"};
        for (Granularity granularity : Granularity.values()) {
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(labels[granularity.ordinal()],
                    granularity == highlighter.getGranularity());
            item.addActionListener(e -> {
                highlighter.granularity(granularity);
                compare();
            });
            granularities.add(item);
            menu.add(item);
        }
        // off, the changes are marked also within two lines that have little in common
        menu.add(DemoMenus.option("Only Within Similar Lines", highlighter.getMinSimilarity() > 0, on -> {
            highlighter.minSimilarity(on ? 0.5 : 0);
            compare();
        }));
        menu.addSeparator();
        menu.add(DemoMenus.option("Line Backgrounds", highlighter.isLineBackgrounds(), on -> {
            highlighter.lineBackgrounds(on);
            compare();
        }));
        menu.add(DemoMenus.option("In the Editors", highlightEditors, on -> {
            highlightEditors = on;
            compare();
        }));
        menu.add(DemoMenus.option("In the Diff", highlightOutput, on -> {
            highlightOutput = on;
            compare();
        }));
        menu.addSeparator();
        JCheckBoxMenuItem rounded = DemoMenus.option("Rounded", original.isRoundedHighlights(), on -> {
            for (JCodeViewer viewer : List.of(original, revised, output)) {
                viewer.setRoundedHighlights(on);
            }
        });
        menu.add(rounded);
        JMenu radiusMenu = new JMenu("Corner Radius");
        ButtonGroup radiusGroup = new ButtonGroup();
        for (int radius : new int[]{2, 4, 6, 10}) {
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(radius + " px" + (radius == 10 ? " (Pill)" : ""),
                    radius == original.getHighlightRadius());
            item.addActionListener(e -> {
                for (JCodeViewer viewer : List.of(original, revised, output)) {
                    viewer.setHighlightRadius(radius);
                    // a radius is only seen on rounded highlights
                    viewer.setRoundedHighlights(true);
                }
                rounded.setSelected(true);
            });
            radiusGroup.add(item);
            radiusMenu.add(item);
        }
        menu.add(radiusMenu);
        menu.addSeparator();
        JMenu colorMenu = new JMenu("Colours");
        ButtonGroup colorGroup = new ButtonGroup();
        for (DiffColors choice : DiffColors.values()) {
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(choice.label, choice.swatch(), choice == colors);
            item.addActionListener(e -> {
                colors = choice;
                setTheme(baseTheme);
            });
            colorGroup.add(item);
            colorMenu.add(item);
        }
        menu.add(colorMenu);
        JMenu strengthMenu = new JMenu("Strength");
        ButtonGroup strengthGroup = new ButtonGroup();
        for (Strength choice : Strength.values()) {
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(choice.label, choice == strength);
            item.addActionListener(e -> {
                strength = choice;
                setTheme(baseTheme);
            });
            strengthGroup.add(item);
            strengthMenu.add(item);
        }
        menu.add(strengthMenu);
        return menu;
    }

    /** Shows the theme with the colours of the diff picked, and marks the changes again in them. */
    private void setTheme(CodeTheme theme) {
        baseTheme = theme;
        CodeTheme shown = colors.apply(theme, strength);
        original.setTheme(shown);
        revised.setTheme(shown);
        output.setTheme(shown);
        compare();
    }
}
