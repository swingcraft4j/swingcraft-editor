package com.swingcraft4j.code.autocomplete;

import com.swingcraft4j.code.editor.JCodeEditor;
import com.swingcraft4j.code.text.TextModel;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JWindow;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.event.ChangeListener;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GraphicsConfiguration;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.util.List;

/**
 * Shows the parameters of a call while the caret is between its parentheses, with the one
 * being typed in bold. The hint sits just above the line and never takes the focus.
 * <pre>{@code
 * ParameterHints.install(editor, name -> name.equals("substring") ? List.of("int begin", "int end") : null);
 * }</pre>
 * Only the caret's line is looked at, so a call whose opening parenthesis is on an earlier
 * line gets no hint.
 */
public final class ParameterHints {

    /** Knows the parameters of functions by name. */
    @FunctionalInterface
    public interface Provider {

        /** The parameters of the function as they should be shown, or null if it is not known. */
        List<String> parameters(String functionName);

        /**
         * The parameters of a function that is called on something, for a provider that has
         * functions of one name with different parameters. Unless it is overridden, the function
         * is looked up by its name alone.
         *
         * @param qualifier what the function is called on: {@code app.headers} for
         *                  {@code app.headers.set(}; empty when it is called on nothing
         */
        default List<String> parameters(String qualifier, String functionName) {
            return parameters(functionName);
        }
    }

    /**
     * A call found around the caret.
     *
     * @param qualifier what the name is called on, such as {@code app.headers}; may be empty
     * @param name      the name before the opening parenthesis
     * @param argument  the zero-based index of the argument the caret is in
     * @param parenthesis the offset of the opening parenthesis
     */
    record Call(String qualifier, String name, int argument, int parenthesis) {
    }

    private final JCodeEditor editor;
    private final Provider provider;
    private final JLabel label = new JLabel();
    private JWindow window;

    private final ChangeListener caretListener = e -> update();
    private final FocusListener focusListener = new FocusAdapter() {
        @Override
        public void focusLost(FocusEvent e) {
            hide();
        }
    };
    private final ComponentListener editorListener = new ComponentAdapter() {
        @Override
        public void componentMoved(ComponentEvent e) {
            update();
        }
    };

    private ParameterHints(JCodeEditor editor, Provider provider) {
        this.editor = editor;
        this.provider = provider;
        editor.addSelectionListener(caretListener);
        editor.addFocusListener(focusListener);
        editor.addComponentListener(editorListener);
    }

    /** Adds parameter hints to an editor. */
    public static ParameterHints install(JCodeEditor editor, Provider provider) {
        return new ParameterHints(editor, provider);
    }

    public void uninstall() {
        hide();
        editor.removeSelectionListener(caretListener);
        editor.removeFocusListener(focusListener);
        editor.removeComponentListener(editorListener);
        if (window != null) {
            window.dispose();
            window = null;
        }
    }

    public boolean isShowing() {
        return window != null && window.isVisible();
    }

    /** The hint for the caret as HTML, or null when the caret is not in a call that is known. */
    public String getHint() {
        Call call = callAt(editor.getModel(), editor.getCaretPosition());
        List<String> parameters = call != null ? provider.parameters(call.qualifier(), call.name()) : null;
        if (parameters == null) {
            return null;
        }
        StringBuilder html = new StringBuilder("<html><nobr>").append(escape(call.name())).append('(');
        for (int i = 0; i < parameters.size(); i++) {
            boolean current = i == Math.min(call.argument(), parameters.size() - 1);
            html.append(i > 0 ? ", " : "").append(current ? "<b>" : "").append(escape(parameters.get(i)))
                    .append(current ? "</b>" : "");
        }
        return html.append(")</nobr></html>").toString();
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /**
     * The innermost call on the caret's line whose parentheses the offset lies between.
     * Parentheses and commas inside string literals are not told apart from real ones.
     */
    static Call callAt(TextModel model, int offset) {
        int lineStart = model.lineStart(model.lineOfOffset(offset));
        int depth = 0;
        int commas = 0;
        for (int i = offset - 1; i >= lineStart; i--) {
            char c = model.charAt(i);
            if (c == ')' || c == ']' || c == '}') {
                depth++;
            } else if (c == '[' || c == '{') {
                if (depth == 0) {
                    return null; // inside a list or block, not directly inside a call
                }
                depth--;
            } else if (c == '(') {
                if (depth > 0) {
                    depth--;
                    continue;
                }
                int end = i;
                while (end > lineStart && model.charAt(end - 1) == ' ') {
                    end--;
                }
                int start = end;
                while (start > lineStart && CompletionProviders.isWordPart(model.charAt(start - 1))) {
                    start--;
                }
                if (start == end) {
                    return null;
                }
                // the names and dots the function is called on: "app.headers" before ".set("
                int qualifierStart = start;
                while (qualifierStart > lineStart && (model.charAt(qualifierStart - 1) == '.'
                        || CompletionProviders.isWordPart(model.charAt(qualifierStart - 1)))) {
                    qualifierStart--;
                }
                String qualifier = qualifierStart < start ? model.getText(qualifierStart, start - 1) : "";
                return new Call(qualifier, model.getText(start, end), commas, i);
            } else if (c == ',' && depth == 0) {
                commas++;
            }
        }
        return null;
    }

    private void update() {
        boolean selection = editor.getSelectionStart() != editor.getSelectionEnd();
        String hint = selection || !editor.isFocusOwner() ? null : getHint();
        Window owner = SwingUtilities.getWindowAncestor(editor);
        if (hint == null || owner == null || !editor.isShowing()) {
            hide();
            return;
        }
        Call call = callAt(editor.getModel(), editor.getCaretPosition());
        Rectangle parenthesis = call != null ? editor.getOffsetBounds(call.parenthesis()) : null;
        if (parenthesis == null || !editor.getVisibleRect().intersects(parenthesis)) {
            hide();
            return;
        }
        if (window == null || window.getOwner() != owner) {
            if (window != null) {
                window.dispose();
            }
            window = new JWindow(owner);
            window.setFocusableWindowState(false);
            window.setType(Window.Type.POPUP);
            window.add(label);
        }
        Color background = UIManager.getColor("ToolTip.background");
        Color foreground = UIManager.getColor("ToolTip.foreground");
        Color border = UIManager.getColor("Component.borderColor");
        label.setOpaque(true);
        label.setBackground(background != null ? background : new Color(0xF7F7F7));
        label.setForeground(foreground != null ? foreground : Color.BLACK);
        label.setFont(editor.getFont());
        label.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(border != null ? border : Color.GRAY),
                BorderFactory.createEmptyBorder(2, 6, 2, 6)));
        label.setText(hint);
        Dimension size = label.getPreferredSize();
        // above the line, starting at the parenthesis; below it if there is no room above
        Point location = new Point(parenthesis.x, parenthesis.y - size.height - 2);
        SwingUtilities.convertPointToScreen(location, editor);
        GraphicsConfiguration configuration = editor.getGraphicsConfiguration();
        if (configuration != null) {
            Rectangle screen = configuration.getBounds();
            if (location.y < screen.y) {
                location.y += size.height + 2 + parenthesis.height * 2;
            }
            location.x = Math.max(screen.x, Math.min(location.x, screen.x + screen.width - size.width));
        }
        window.setBounds(location.x, location.y, size.width, size.height);
        window.validate();
        if (!window.isVisible()) {
            window.setVisible(true);
        }
    }

    public void hide() {
        if (isShowing()) {
            window.setVisible(false);
        }
    }
}
