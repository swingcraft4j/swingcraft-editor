package com.swingcraft4j.code.demo;

import com.formdev.flatlaf.fonts.inter.FlatInterFont;
import com.formdev.flatlaf.fonts.jetbrains_mono.FlatJetBrainsMonoFont;
import com.formdev.flatlaf.fonts.roboto.FlatRobotoFont;
import com.formdev.flatlaf.fonts.roboto_mono.FlatRobotoMonoFont;
import com.formdev.flatlaf.util.FontUtils;
import com.swingcraft4j.code.config.EditorConfig;
import com.swingcraft4j.code.lexer.Language;
import com.swingcraft4j.code.lexer.Languages;
import com.swingcraft4j.code.theme.CodeTheme;
import com.swingcraft4j.code.theme.CodeThemes;
import com.swingcraft4j.code.viewer.JCodeViewer;

import javax.swing.ButtonGroup;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JFileChooser;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.Component;
import java.awt.Font;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/** The menus of the demos, and what their items are made of. */
final class DemoMenus {

    /** The fonts that come with FlatLaf; the first is the one of the Look and Feel of the demos. */
    private static final String[] FONT_FAMILIES = {
            FlatRobotoFont.FAMILY, FlatRobotoMonoFont.FAMILY, FlatInterFont.FAMILY, FlatJetBrainsMonoFont.FAMILY};
    private static final int[] FONT_SIZES = {10, 11, 12, 13, 14, 15, 16, 18, 20, 24};
    /** The themes the demo reads from XML, as an application reads those its users write; the light ones first. */
    private static final String[] XML_THEMES = {
            "one-light.xml", "gruvbox-light.xml", "catppuccin-latte.xml",
            "github-dark.xml", "github-dark-dimmed.xml", "solarized-dark.xml", "gruvbox-dark.xml",
            "tokyo-night.xml", "catppuccin-mocha.xml", "kanagawa.xml"};

    private DemoMenus() {
    }

    static JMenu menu(String text, char mnemonic) {
        JMenu menu = new JMenu(text);
        menu.setMnemonic(mnemonic);
        return menu;
    }

    /** An item that does something of the demo itself. */
    static JMenuItem item(String text, KeyStroke key, Runnable action) {
        JMenuItem item = new JMenuItem(text);
        item.setAccelerator(key);
        item.addActionListener(e -> action.run());
        return item;
    }

    /**
     * An item that runs an action of the viewer or the editor, and shows the key the action has
     * there. In the text it is the viewer that takes the key; the menu takes it elsewhere in
     * the window.
     */
    static JMenuItem item(String text, JCodeViewer viewer, String actionName) {
        KeyStroke key = viewer.getKeys(actionName).stream()
                .min(Comparator.comparingInt((KeyStroke k) -> Integer.bitCount(k.getModifiers()))
                        .thenComparingInt(k -> KeyEvent.getKeyText(k.getKeyCode()).length()))
                .orElse(null);
        return item(text, key, () -> viewer.getActionMap().get(actionName).actionPerformed(null));
    }

    /** An item that is ticked while an option is on. */
    static JCheckBoxMenuItem option(String text, boolean on, Consumer<Boolean> apply) {
        JCheckBoxMenuItem item = new JCheckBoxMenuItem(text, on);
        item.addActionListener(e -> apply.accept(item.isSelected()));
        return item;
    }

    /** How the viewer or the editor shows the text: each option as the viewer has it now. */
    static JMenu view(JCodeViewer viewer) {
        JMenu view = menu("View", 'V');
        view.add(option("Wrap Lines", viewer.isLineWrap(), viewer::setLineWrap));
        JMenuItem atWords = view.add(option("Wrap at Words", viewer.isWrapStyleWord(), viewer::setWrapStyleWord));
        atWords.setToolTipText("Break wrapped lines after a blank instead of anywhere");
        view.addSeparator();
        view.add(option("Line Numbers", viewer.isLineNumbersVisible(), viewer::setLineNumbersVisible));
        view.add(option("Fold Arrows", viewer.isFoldingEnabled(), viewer::setFoldingEnabled));
        view.add(option("Indent Guides", viewer.isIndentGuides(), viewer::setIndentGuides));
        view.add(option("Bracket Matching", viewer.isBracketMatching(), viewer::setBracketMatching));
        view.add(option("Rounded Selection", viewer.isRoundedSelection(), viewer::setRoundedSelection));
        return view;
    }

    /**
     * The themes; the one picked is also what decides between the light and the dark Look and
     * Feel. The built-in ones come first, then those the demo reads from XML files, and last an
     * item to open a theme file of one's own.
     */
    static JMenu themes(CodeTheme current, Consumer<CodeTheme> apply) {
        JMenu themes = menu("Theme", 'T');
        ButtonGroup group = new ButtonGroup();
        Consumer<CodeTheme> pick = theme -> {
            apply.accept(theme);
            DemoLookAndFeel.follow(theme);
        };
        for (CodeTheme theme : CodeThemes.all()) {
            themes.add(themeItem(theme, current, group, pick));
        }
        themes.addSeparator();
        for (String file : XML_THEMES) {
            JRadioButtonMenuItem item = themeItem(xmlTheme(file), current, group, pick);
            item.setToolTipText("Read from themes/" + file);
            themes.add(item);
        }
        themes.addSeparator();
        themes.add(item("Open Theme File...", null, () -> openThemeFile(themes, group, pick)));
        return themes;
    }

    private static JRadioButtonMenuItem themeItem(CodeTheme theme, CodeTheme current, ButtonGroup group, Consumer<CodeTheme> pick) {
        JRadioButtonMenuItem item = new JRadioButtonMenuItem(theme.toString(), theme.name().equals(current.name()));
        item.addActionListener(e -> pick.accept(theme));
        group.add(item);
        return item;
    }

    private static CodeTheme xmlTheme(String file) {
        try (InputStream in = DemoMenus.class.getResourceAsStream("themes/" + file)) {
            if (in == null) {
                throw new IllegalStateException("no theme file themes/" + file);
            }
            return EditorConfig.read(in).theme().orElseThrow();
        } catch (IOException e) {
            throw new UncheckedIOException("themes/" + file + ": " + e.getMessage(), e);
        }
    }

    /** Reads a theme from a file the user picks, and says what is wrong with one that cannot be read. */
    private static void openThemeFile(JMenu menu, ButtonGroup group, Consumer<CodeTheme> pick) {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("Theme files (*.xml)", "xml"));
        Component parent = SwingUtilities.getWindowAncestor(menu);
        if (chooser.showOpenDialog(parent) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Path file = chooser.getSelectedFile().toPath();
        try {
            Optional<CodeTheme> theme = EditorConfig.read(file).theme();
            if (theme.isEmpty()) {
                JOptionPane.showMessageDialog(parent, file.getFileName() + " has no <theme>.", "Open Theme File", JOptionPane.WARNING_MESSAGE);
                return;
            }
            group.clearSelection();
            pick.accept(theme.get());
        } catch (IOException e) {
            JOptionPane.showMessageDialog(parent, file.getFileName() + "\n" + e.getMessage(), "Open Theme File", JOptionPane.ERROR_MESSAGE);
        }
    }

    /** The family and the size of the font of a viewer or an editor. */
    static JMenu fonts(JCodeViewer viewer) {
        JMenu fonts = menu("Font", 'n');
        String[] family = {FONT_FAMILIES[0]};
        int[] size = {viewer.getFont().getSize()};
        Runnable apply = () -> viewer.setFont(FontUtils.getCompositeFont(family[0], Font.PLAIN, size[0]));
        ButtonGroup families = new ButtonGroup();
        for (String name : FONT_FAMILIES) {
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(name, name.equals(family[0]));
            item.addActionListener(e -> {
                family[0] = name;
                apply.run();
            });
            families.add(item);
            fonts.add(item);
        }
        fonts.addSeparator();
        JMenu sizes = new JMenu("Size");
        ButtonGroup sizeGroup = new ButtonGroup();
        for (int points : FONT_SIZES) {
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(Integer.toString(points), points == size[0]);
            item.addActionListener(e -> {
                size[0] = points;
                apply.run();
            });
            sizeGroup.add(item);
            sizes.add(item);
        }
        fonts.add(sizes);
        return fonts;
    }

    /** The languages to pick one from, with the one of the document on show ticked. */
    static final class LanguageMenu {

        private final JMenu menu = DemoMenus.menu("Language", 'L');
        private final ButtonGroup group = new ButtonGroup();
        /** The item of each language by its name; that of no language is under null. */
        private final Map<String, JRadioButtonMenuItem> items = new LinkedHashMap<>();
        private Language selected;

        /**
         * @param plainText the name of an item for no language at all, or null to have none
         * @param chosen    told the language that is picked from the menu, which is null for no language
         */
        LanguageMenu(String plainText, Consumer<Language> chosen) {
            if (plainText != null) {
                add(plainText, null, chosen);
                menu.addSeparator();
            }
            for (Language language : Languages.installed()) {
                add(language.displayName(), language, chosen);
            }
        }

        private void add(String text, Language language, Consumer<Language> chosen) {
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(text);
            item.addActionListener(e -> {
                selected = language;
                chosen.accept(language);
            });
            group.add(item);
            menu.add(item);
            items.put(language != null ? language.displayName() : null, item);
        }

        JMenu menu() {
            return menu;
        }

        /** The language that is ticked, or null where that is no language. */
        Language selected() {
            return selected;
        }

        /** Ticks the language of a document that was shown some other way; tells no one. */
        void select(Language language) {
            JRadioButtonMenuItem item = items.get(language != null ? language.displayName() : null);
            if (item != null) {
                selected = language;
                item.setSelected(true);
            } else {
                selected = null;
                group.clearSelection();
            }
        }
    }
}
