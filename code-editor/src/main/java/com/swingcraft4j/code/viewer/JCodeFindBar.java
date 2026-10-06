package com.swingcraft4j.code.viewer;

import com.swingcraft4j.code.search.TextSearch;

import javax.swing.AbstractAction;
import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.KeyStroke;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.regex.PatternSyntaxException;

/**
 * A search bar for a {@link JCodeViewer}. Add it next to the viewer's scroll pane; it stays
 * hidden until the user presses Ctrl+F in the viewer, and Escape hides it again. It searches
 * as the user types; Enter and Shift+Enter move to the next and previous match.
 */
public class JCodeFindBar extends JPanel {

    private final JCodeViewer viewer;
    private final JTextField field = new JTextField(24);
    private final JToggleButton matchCase = toggle("Aa", "Match case");
    private final JToggleButton wholeWord = toggle("W", "Whole word");
    private final JToggleButton regex = toggle(".*", "Regular expression");
    private final JLabel status = new JLabel(" ");

    public JCodeFindBar(JCodeViewer viewer) {
        super(new BorderLayout());
        this.viewer = viewer;

        JButton previous = button("↑", "Previous match (Shift+Enter)");
        previous.addActionListener(e -> find(false));
        JButton next = button("↓", "Next match (Enter)");
        next.addActionListener(e -> find(true));
        JButton close = button("×", "Close (Escape)");
        close.addActionListener(e -> close());

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        controls.add(new JLabel("Find:"));
        controls.add(field);
        controls.add(matchCase);
        controls.add(wholeWord);
        controls.add(regex);
        controls.add(previous);
        controls.add(next);
        controls.add(status);
        add(controls, BorderLayout.CENTER);
        add(close, BorderLayout.EAST);
        setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 2));

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
            status.setText(" ");
            return;
        }
        try {
            viewer.setSearch(new TextSearch(query, matchCase.isSelected(), wholeWord.isSelected(), regex.isSelected()));
            status.setText(viewer.findFromSelectionStart() ? " " : "No matches");
        } catch (PatternSyntaxException e) {
            viewer.setSearch(null);
            status.setText("Invalid expression");
        }
    }

    private void find(boolean forward) {
        if (viewer.getSearch() != null) {
            boolean found = forward ? viewer.findNext() : viewer.findPrevious();
            status.setText(found ? " " : "No matches");
        }
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

    private static JButton button(String text, String toolTip) {
        return configure(new JButton(text), toolTip);
    }

    private static <B extends AbstractButton> B configure(B button, String toolTip) {
        button.setToolTipText(toolTip);
        button.setFocusable(false);
        button.setMargin(new Insets(1, 6, 1, 6));
        return button;
    }
}
