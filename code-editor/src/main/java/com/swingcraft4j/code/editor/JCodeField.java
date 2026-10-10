package com.swingcraft4j.code.editor;

import com.swingcraft4j.code.lexer.Language;
import com.swingcraft4j.code.text.GapTextModel;
import com.swingcraft4j.code.text.TextModel;
import com.swingcraft4j.code.viewer.JCodeViewer;

import javax.swing.JComponent;
import javax.swing.JTextField;
import javax.swing.JViewport;
import javax.swing.KeyStroke;
import javax.swing.UIManager;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

/**
 * A text field for one line of code: a {@link JCodeEditor} that looks and behaves like a text
 * field. It has the border of the text fields of the look and feel, takes no line break, leaves
 * the focus on Tab and tells its action listeners of Enter. As a text field of FlatLaf does, it
 * can show a placeholder while it is empty, and a component at each end inside its border.
 * <p>
 * The editing is done by an editor of its own, {@link #getEditor()}: that is where to add code
 * completion, markers and listeners, and to set the theme and the other options.
 */
public class JCodeField extends JComponent {

    /** The room at the end of the text for a caret that stands there. */
    private static final int CARET_ROOM = 2;

    /**
     * Never gets the focus and never has text: it lies under the editor and paints what a text
     * field of the look and feel looks like, whatever look and feel that is.
     */
    private final JTextField shell = new JTextField();
    private final JViewport viewport = new JViewport();
    private final FieldEditor editor = new FieldEditor();
    private final List<ActionListener> actionListeners = new CopyOnWriteArrayList<>();
    private JComponent leadingComponent;
    private JComponent trailingComponent;
    private String placeholder;
    private int columns;

    public JCodeField() {
        setFocusable(false);
        shell.setFocusable(false);
        shell.setRequestFocusEnabled(false);
        // FlatLaf asks this whether to paint the field as the one that has the focus
        shell.putClientProperty("JComponent.focusOwner", (Predicate<JComponent>) c -> editor.isFocusOwner());
        shell.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                editor.requestFocusInWindow(); // a click beside the text, inside the border
            }
        });
        editor.addFocusListener(new FocusListener() {
            @Override
            public void focusGained(FocusEvent e) {
                repaint();
            }

            @Override
            public void focusLost(FocusEvent e) {
                repaint();
            }
        });
        // what is set on the field to change its look is meant for the text field that paints it
        addPropertyChangeListener(event -> {
            String name = event.getPropertyName();
            if (name != null && (name.startsWith("JComponent.") || name.startsWith("FlatLaf."))) {
                shell.putClientProperty(name, event.getNewValue());
                sizeChanged();
                repaint();
            }
        });
        viewport.setOpaque(false);
        viewport.setScrollMode(JViewport.SIMPLE_SCROLL_MODE);
        viewport.setView(editor);
        add(viewport);
        add(shell);
    }

    public JCodeField(CharSequence text) {
        this();
        setText(text);
    }

    /** The editor that edits the text of the field. */
    public JCodeEditor getEditor() {
        return editor;
    }

    // ---- Text ----

    public String getText() {
        return editor.getText();
    }

    /** Sets the text. Of a text of more than one line the line breaks are left out, as when it is pasted. */
    public void setText(CharSequence text) {
        editor.setText(text);
    }

    public Language getLanguage() {
        return editor.getLanguage();
    }

    /** Sets the language used for highlighting; null shows plain text. */
    public void setLanguage(Language language) {
        editor.setLanguage(language);
    }

    public void selectAll() {
        editor.selectAll();
    }

    public boolean isEditable() {
        return editor.isEditable();
    }

    /** When false the text cannot be changed, and the field looks as a text field does that cannot be. */
    public void setEditable(boolean editable) {
        editor.setEditable(editable);
        shell.setEditable(editable);
    }

    /**
     * Adds a listener told when Enter is pressed in the field. While the popup of code completion
     * is open, Enter takes the suggestion instead.
     */
    public void addActionListener(ActionListener listener) {
        actionListeners.add(listener);
    }

    public void removeActionListener(ActionListener listener) {
        actionListeners.remove(listener);
    }

    private void fireActionPerformed() {
        ActionEvent event = new ActionEvent(this, ActionEvent.ACTION_PERFORMED, getText());
        for (ActionListener listener : actionListeners) {
            listener.actionPerformed(event);
        }
    }

    // ---- Appearance ----

    public String getPlaceholder() {
        return placeholder;
    }

    /** Sets the text shown, greyed, while the field is empty; null for none. */
    public void setPlaceholder(String placeholder) {
        this.placeholder = placeholder;
        editor.repaint();
    }

    public int getColumns() {
        return columns;
    }

    /**
     * Sets how many chars wide the field would like to be. With zero, the default, it would like
     * to be as wide as its text.
     */
    public void setColumns(int columns) {
        if (columns < 0) {
            throw new IllegalArgumentException("columns must not be negative: " + columns);
        }
        this.columns = columns;
        sizeChanged();
    }

    public JComponent getLeadingComponent() {
        return leadingComponent;
    }

    /**
     * Sets a component shown inside the border in front of the text, such as a combo box or an
     * icon; null for none. It is given the height of the field, and should not be opaque where
     * the border has round corners.
     */
    public void setLeadingComponent(JComponent component) {
        leadingComponent = replace(leadingComponent, component);
    }

    public JComponent getTrailingComponent() {
        return trailingComponent;
    }

    /** Sets a component shown inside the border after the text, such as a button; null for none. */
    public void setTrailingComponent(JComponent component) {
        trailingComponent = replace(trailingComponent, component);
    }

    private JComponent replace(JComponent old, JComponent component) {
        if (old != null) {
            remove(old);
        }
        if (component != null) {
            add(component, 0); // above the text field that paints the border
        }
        sizeChanged();
        repaint();
        return component;
    }

    /** Sets the font of the text; see {@link JCodeViewer#setFont}. */
    @Override
    public void setFont(Font font) {
        super.setFont(font);
        editor.setFont(font);
        sizeChanged();
    }

    @Override
    public Font getFont() {
        return editor != null ? editor.getFont() : super.getFont();
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        shell.setEnabled(enabled);
        editor.setEnabled(enabled);
    }

    @Override
    public void requestFocus() {
        editor.requestFocus();
    }

    @Override
    public boolean requestFocusInWindow() {
        return editor.requestFocusInWindow();
    }

    // ---- Layout ----

    /**
     * Has the field laid out again, and what it is in as well: called when the size the field
     * would like to have may be another, which {@link #revalidate()} alone does not pass on.
     */
    private void sizeChanged() {
        invalidate();
        if (getParent() instanceof JComponent parent) {
            parent.revalidate();
        }
        revalidate();
    }

    /** The insets of the text field without its margin: its border alone. */
    private Insets borderInsets() {
        Insets insets = shell.getInsets();
        Insets margin = shell.getMargin();
        if (margin != null) {
            insets.top = Math.max(0, insets.top - margin.top);
            insets.left = Math.max(0, insets.left - margin.left);
            insets.bottom = Math.max(0, insets.bottom - margin.bottom);
            insets.right = Math.max(0, insets.right - margin.right);
        }
        return insets;
    }

    private static int widthOf(Component component) {
        return component != null && component.isVisible() ? component.getPreferredSize().width : 0;
    }

    private static int heightOf(Component component) {
        return component != null && component.isVisible() ? component.getPreferredSize().height : 0;
    }

    /** Where the row of text begins in a field of the given height: in the middle between the borders. */
    private int textY(int height, Insets border) {
        int room = height - border.top - border.bottom;
        return border.top + Math.max(0, (room - editor.getRowHeight()) / 2);
    }

    @Override
    public void doLayout() {
        int width = getWidth();
        int height = getHeight();
        shell.setBounds(0, 0, width, height);
        Insets insets = shell.getInsets();
        Insets border = borderInsets();
        int inside = Math.max(0, height - border.top - border.bottom);
        int left = insets.left;
        int right = width - insets.right;
        // a component at an end stands against the border; the margin is between it and the text
        if (widthOf(leadingComponent) > 0) {
            int leading = Math.min(widthOf(leadingComponent), Math.max(0, right - border.left));
            leadingComponent.setBounds(border.left, border.top, leading, inside);
            left += leading;
        }
        if (widthOf(trailingComponent) > 0) {
            int trailing = Math.min(widthOf(trailingComponent), Math.max(0, right - left));
            trailingComponent.setBounds(width - border.right - trailing, border.top, trailing, inside);
            right -= trailing;
        }
        int y = textY(height, border);
        viewport.setBounds(left, y, Math.max(0, right - left), Math.max(0, Math.min(editor.getRowHeight(), height - border.bottom - y)));
    }

    @Override
    public Dimension getPreferredSize() {
        if (isPreferredSizeSet()) {
            return super.getPreferredSize();
        }
        Insets insets = shell.getInsets();
        Insets border = borderInsets();
        FontMetrics metrics = editor.getFontMetrics(editor.getFont());
        int text = columns > 0 ? columns * metrics.charWidth('m') + CARET_ROOM : editor.getPreferredSize().width;
        int width = insets.left + insets.right + text + widthOf(leadingComponent) + widthOf(trailingComponent);
        // as high as a text field of the look and feel, and as its font and what stands in it need
        int height = Math.max(shell.getPreferredSize().height, insets.top + insets.bottom + metrics.getHeight());
        int inside = Math.max(editor.getRowHeight(), Math.max(heightOf(leadingComponent), heightOf(trailingComponent)));
        return new Dimension(width, Math.max(height, border.top + border.bottom + inside));
    }

    @Override
    public Dimension getMinimumSize() {
        if (isMinimumSizeSet()) {
            return super.getMinimumSize();
        }
        Insets insets = shell.getInsets();
        int width = insets.left + insets.right + CARET_ROOM + widthOf(leadingComponent) + widthOf(trailingComponent);
        return new Dimension(width, getPreferredSize().height);
    }

    @Override
    public int getBaseline(int width, int height) {
        FontMetrics metrics = editor.getFontMetrics(editor.getFont());
        return textY(height, borderInsets()) + metrics.getLeading() + metrics.getAscent() + 1;
    }

    @Override
    public BaselineResizeBehavior getBaselineResizeBehavior() {
        return BaselineResizeBehavior.CENTER_OFFSET;
    }

    /** As a text field: the text growing or shrinking lays out the field again, not what the field is in. */
    @Override
    public boolean isValidateRoot() {
        return true;
    }

    /** The editor lies over the text field that paints the border. */
    @Override
    public boolean isOptimizedDrawingEnabled() {
        return false;
    }

    private static Color placeholderColor() {
        for (String key : new String[]{"TextField.placeholderForeground", "Label.disabledForeground", "textInactiveText"}) {
            Color color = UIManager.getColor(key);
            if (color != null) {
                return color;
            }
        }
        return Color.GRAY;
    }

    /** The text as one line: the line breaks at its ends left out, each of the others a blank. */
    static String oneLine(String text) {
        if (text.indexOf('\n') < 0 && text.indexOf('\r') < 0) {
            return text;
        }
        return text.replaceAll("^[\\r\\n]+|[\\r\\n]+$", "").replaceAll("[\\r\\n]+", " ");
    }

    /**
     * The editor of the field: one line, no background of its own, and none of what an editor has
     * for many lines.
     */
    private final class FieldEditor extends JCodeEditor {

        FieldEditor() {
            setOpaque(false);
            setLeftPadding(0);
            setRightPadding(CARET_ROOM);
            setLineNumbersVisible(false);
            setFoldingEnabled(false);
            setHighlightCurrentLine(false);
            setBracketMatching(false);
            // Tab is not typed: it goes on to the next component. The actions keep their names,
            // so that code completion, which takes Tab while its popup is open, gives it back.
            bindAction("insert-tab", this::transferFocus, KeyStroke.getKeyStroke(KeyEvent.VK_TAB, 0));
            bindAction("unindent", this::transferFocusBackward, KeyStroke.getKeyStroke(KeyEvent.VK_TAB, InputEvent.SHIFT_DOWN_MASK));
            bindAction("insert-break", JCodeField.this::fireActionPerformed, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0));
            for (String action : new String[]{ACTION_GO_TO_LINE, ACTION_COLLAPSE_FOLD, ACTION_EXPAND_FOLD, ACTION_EXPAND_ALL_FOLDS,
                    ACTION_DUPLICATE_LINES, ACTION_MOVE_LINES_UP, ACTION_MOVE_LINES_DOWN, ACTION_TOGGLE_COMMENT, ACTION_FORMAT}) {
                setKeys(action); // no keys: these are for a text of many lines
            }
        }

        @Override
        String accepted(String text) {
            return oneLine(text);
        }

        @Override
        public void setDocument(TextModel model, Language language) {
            super.setDocument(model.lineCount() > 1 ? new GapTextModel(oneLine(model.getText(0, model.length()))) : model, language);
        }

        /** The placeholder, where the text would be. */
        @Override
        protected void paintBackgroundLayer(Graphics2D g, Rectangle clip) {
            if (placeholder == null || getModel().length() > 0) {
                return;
            }
            Graphics2D text = (Graphics2D) g.create();
            try {
                applyTextHints(text);
                text.setFont(getFont());
                text.setColor(placeholderColor());
                FontMetrics metrics = text.getFontMetrics();
                Rectangle start = getOffsetBounds(0);
                text.drawString(placeholder, start.x, start.y + metrics.getLeading() + metrics.getAscent() + 1);
            } finally {
                text.dispose();
            }
        }
    }
}
