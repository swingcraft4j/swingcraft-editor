package com.swingcraft4j.code.viewer;

import com.swingcraft4j.code.search.TextSearch;
import com.swingcraft4j.code.text.TextModel;

import javax.swing.AbstractAction;
import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.KeyStroke;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.geom.Path2D;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.PatternSyntaxException;

/**
 * A search bar for a {@link JCodeViewer}. Add it next to the viewer's scroll pane; it stays
 * hidden until the user presses Ctrl+F in the viewer, and Escape hides it again. It searches
 * as the user types, and says which of how many matches is the selected one; Enter and
 * Shift+Enter move to the next and the previous match.
 * <p>
 * Its buttons are all of one size, and with FlatLaf they are painted as those of a toolbar.
 * The parts of the bar can be had, to give them another look.
 */
public class JCodeFindBar extends JPanel {

    /** Past this many matches they are no longer counted, and neither when counting takes longer than this. */
    private static final int MAX_COUNTED = 9999;
    private static final long COUNT_NANOS = 25_000_000;

    private final JCodeViewer viewer;
    private final JTextField field = new JTextField(24);
    private final JToggleButton matchCase = toggle("Aa", "Match case");
    private final JToggleButton wholeWord = toggle("W", "Whole word");
    private final JToggleButton regex = toggle(".*", "Regular expression");
    private final JButton previous = button(new BarIcon(BarIcon.Kind.PREVIOUS), "Previous match (Shift+Enter)");
    private final JButton next = button(new BarIcon(BarIcon.Kind.NEXT), "Next match (Enter)");
    private final JButton close = button(new BarIcon(BarIcon.Kind.CLOSE), "Close (Escape)");
    private final JLabel label = new JLabel("Find:");
    private final JLabel status = new JLabel(" ");
    /** The sizes the bar gave its buttons: one that has another by now was given it by the application. */
    private final Map<AbstractButton, Dimension> buttonSizes = new HashMap<>();
    private boolean buttonSizesStale = true;

    public JCodeFindBar(JCodeViewer viewer) {
        super(new BorderLayout());
        this.viewer = viewer;

        previous.addActionListener(e -> find(false));
        next.addActionListener(e -> find(true));
        close.addActionListener(e -> close());

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        controls.setOpaque(false);
        controls.add(label);
        controls.add(field);
        controls.add(matchCase);
        controls.add(wholeWord);
        controls.add(regex);
        controls.add(previous);
        controls.add(next);
        controls.add(status);
        add(controls, BorderLayout.CENTER);
        // in a panel of its own, where it keeps its size: the bar would give it its whole height
        JPanel end = new JPanel(new GridBagLayout());
        end.setOpaque(false);
        end.add(close);
        add(end, BorderLayout.EAST);
        // no line of its own: the scroll pane it stands next to has a border
        setBorder(BorderFactory.createEmptyBorder(2, 2, 1, 4));

        field.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                queryChanged();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                queryChanged();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                queryChanged();
            }
        });
        for (JToggleButton option : new JToggleButton[]{matchCase, wholeWord, regex}) {
            option.addActionListener(e -> queryChanged());
        }
        field.addActionListener(e -> find(true));
        bindField("find-previous", () -> find(false), KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.SHIFT_DOWN_MASK));
        bindField("close", this::close, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0));

        int menuKey = GraphicsEnvironment.isHeadless()
                ? InputEvent.CTRL_DOWN_MASK
                : Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        viewer.bindAction(JCodeViewer.ACTION_FIND, this::open, KeyStroke.getKeyStroke(KeyEvent.VK_F, menuKey));
        viewer.bindAction("close-find", this::close, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0));
        setVisible(false);
    }

    // ---- The parts of the bar, to give them another look: an icon in place of a text, a style ----

    /** The label in front of the field. */
    public JLabel getLabel() {
        return label;
    }

    /** The field the text to find is typed in. */
    public JTextField getSearchField() {
        return field;
    }

    public JToggleButton getMatchCaseButton() {
        return matchCase;
    }

    public JToggleButton getWholeWordButton() {
        return wholeWord;
    }

    public JToggleButton getRegexButton() {
        return regex;
    }

    public JButton getPreviousButton() {
        return previous;
    }

    public JButton getNextButton() {
        return next;
    }

    public JButton getCloseButton() {
        return close;
    }

    /** The label that says which match is selected, that there is none, or that the expression is not a valid one. */
    public JLabel getStatusLabel() {
        return status;
    }

    /** Shows the bar and puts the keyboard focus in its field, starting from the viewer's selection. */
    public void open() {
        String selected = viewer.getSelectionEnd() - viewer.getSelectionStart() <= 200 ? viewer.getSelectedText() : "";
        if (!selected.isEmpty() && selected.indexOf('\n') < 0 && selected.indexOf('\r') < 0) {
            // still hidden, so the document listener does not search yet
            field.setText(selected);
        }
        setVisible(true);
        queryChanged();
        field.selectAll();
        field.requestFocusInWindow();
    }

    /** Hides the bar, clears the match marks and returns the focus to the viewer. */
    public void close() {
        if (isVisible()) {
            setVisible(false);
            viewer.setSearch(null);
            viewer.requestFocusInWindow();
        }
    }

    private void queryChanged() {
        if (!isVisible()) {
            return;
        }
        String query = field.getText();
        if (query.isEmpty()) {
            viewer.setSearch(null);
            showStatus(" ", false);
            return;
        }
        try {
            viewer.setSearch(new TextSearch(query, matchCase.isSelected(), wholeWord.isSelected(), regex.isSelected()));
            showFound(viewer.findFromSelectionStart());
        } catch (PatternSyntaxException e) {
            viewer.setSearch(null);
            showStatus("Invalid expression", true);
        }
    }

    private void find(boolean forward) {
        if (viewer.getSearch() != null) {
            showFound(forward ? viewer.findNext() : viewer.findPrevious());
        }
    }

    private void showFound(boolean found) {
        showStatus(found ? matchPosition() : "No matches", !found);
    }

    /**
     * @param failed whether there is nothing to show for the query; with FlatLaf the field gets
     *               the outline of an error then
     */
    private void showStatus(String text, boolean failed) {
        status.setText(text);
        field.putClientProperty("JComponent.outline", failed ? "error" : null);
    }

    /**
     * Which of how many matches the selected one is, as "3 of 12". Just a blank where there are
     * very many matches or the document is so long that counting them would hold typing up.
     */
    private String matchPosition() {
        TextSearch search = viewer.getSearch();
        TextModel model = viewer.getModel();
        int selected = viewer.getSelectionStart();
        long deadline = System.nanoTime() + COUNT_NANOS;
        int count = 0;
        int position = 0;
        int from = 0;
        while (from <= model.length()) {
            TextSearch.Match match = search.findNext(model, from);
            if (match == null) {
                break;
            }
            count++;
            if (match.start() == selected) {
                position = count;
            }
            if (count > MAX_COUNTED || ((count & 63) == 0 && System.nanoTime() > deadline)) {
                return " ";
            }
            // on, also past a match of no length
            from = Math.max(match.end(), match.start() + 1);
        }
        return position > 0 ? position + " of " + count : " ";
    }

    private void bindField(String name, Runnable action, KeyStroke key) {
        field.getInputMap(WHEN_FOCUSED).put(key, name);
        field.getActionMap().put(name, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                action.run();
            }
        });
    }

    private static JToggleButton toggle(String text, String toolTip) {
        return configure(new JToggleButton(text), toolTip);
    }

    private static JButton button(Icon icon, String toolTip) {
        return configure(new JButton(icon), toolTip);
    }

    private static <B extends AbstractButton> B configure(B button, String toolTip) {
        button.setToolTipText(toolTip);
        button.setFocusable(false);
        button.setMargin(new Insets(1, 4, 1, 4));
        // with FlatLaf: no border until the mouse is over it, as a button of a toolbar
        button.putClientProperty("JButton.buttonType", "toolBarButton");
        return button;
    }

    // ---- The size of the buttons ----

    /** The buttons are measured again once the look and feel has changed: their sizes are those of the old one. */
    @Override
    public void updateUI() {
        super.updateUI();
        buttonSizesStale = true;
    }

    @Override
    public Dimension getPreferredSize() {
        sizeButtons();
        return super.getPreferredSize();
    }

    @Override
    public void doLayout() {
        sizeButtons();
        super.doLayout();
    }

    /**
     * Gives the buttons one height, the largest that any of them would like, and those with an
     * icon that as their width too; a button with an icon may be a pixel larger, to have the icon
     * in its middle. A button the application has given a size of its own keeps it.
     */
    private void sizeButtons() {
        // the constructor of JPanel comes here before the buttons are there
        if (!buttonSizesStale || close == null) {
            return;
        }
        buttonSizesStale = false;
        List<AbstractButton> buttons = List.of(matchCase, wholeWord, regex, previous, next, close);
        int height = 0;
        for (AbstractButton button : buttons) {
            if (isSizedByBar(button)) {
                button.setPreferredSize(null);
                height = Math.max(height, button.getPreferredSize().height);
            }
        }
        for (AbstractButton button : buttons) {
            if (isSizedByBar(button)) {
                Dimension size;
                if (button.getIcon() != null) {
                    // a pixel more where that leaves the icon as much room at one side as at the other
                    size = new Dimension(centering(height, button.getIcon().getIconWidth()), centering(height, button.getIcon().getIconHeight()));
                } else {
                    // a little more than a text asks for: some look and feels measure it to the pixel
                    size = new Dimension(Math.max(height, button.getPreferredSize().width + 4), height);
                }
                button.setPreferredSize(size);
                buttonSizes.put(button, size);
            }
        }
    }

    /** The length that has an icon of the given length in its very middle: the one asked for, or a pixel more. */
    private static int centering(int length, int iconLength) {
        return length + ((length - iconLength) & 1);
    }

    private boolean isSizedByBar(AbstractButton button) {
        return !button.isPreferredSizeSet() || button.getPreferredSize().equals(buttonSizes.get(button));
    }

    /**
     * The icons of the buttons that have no text: drawn, in the color of the text of the button,
     * so that they do not depend on what glyphs a font has.
     */
    private static final class BarIcon implements Icon {

        private enum Kind {
            PREVIOUS, NEXT, CLOSE
        }

        private static final int SIZE = 16;

        private final Kind kind;

        private BarIcon(Kind kind) {
            this.kind = kind;
        }

        @Override
        public void paintIcon(Component c, Graphics graphics, int x, int y) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
                g.setColor(c.isEnabled() ? c.getForeground() : UIManager.getColor("Label.disabledForeground"));
                g.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.translate(x, y);
                Path2D.Float path = new Path2D.Float();
                switch (kind) {
                    case PREVIOUS -> {
                        path.moveTo(4, 10);
                        path.lineTo(8, 6);
                        path.lineTo(12, 10);
                    }
                    case NEXT -> {
                        path.moveTo(4, 6);
                        path.lineTo(8, 10);
                        path.lineTo(12, 6);
                    }
                    case CLOSE -> {
                        path.moveTo(4.5f, 4.5f);
                        path.lineTo(11.5f, 11.5f);
                        path.moveTo(11.5f, 4.5f);
                        path.lineTo(4.5f, 11.5f);
                    }
                }
                g.draw(path);
            } finally {
                g.dispose();
            }
        }

        @Override
        public int getIconWidth() {
            return SIZE;
        }

        @Override
        public int getIconHeight() {
            return SIZE;
        }
    }
}
