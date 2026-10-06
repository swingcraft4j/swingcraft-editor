package com.swingcraft4j.code.demo;

import com.swingcraft4j.code.autocomplete.AutoCompletion;
import com.swingcraft4j.code.autocomplete.Completion;
import com.swingcraft4j.code.autocomplete.CompletionKind;
import com.swingcraft4j.code.autocomplete.CompletionProvider;
import com.swingcraft4j.code.autocomplete.CompletionRequest;
import com.swingcraft4j.code.editor.JCodeEditor;
import com.swingcraft4j.code.lexer.Language;
import com.swingcraft4j.code.lexer.Languages;
import com.swingcraft4j.code.lexer.OverlayLanguage;
import com.swingcraft4j.code.lexer.TokenType;
import com.swingcraft4j.code.marker.Marker;
import com.swingcraft4j.code.text.ArrayTextModel;
import com.swingcraft4j.code.text.TextModel;
import com.swingcraft4j.code.theme.CodeTheme;
import com.swingcraft4j.code.theme.TokenStyle;
import com.swingcraft4j.code.viewer.JCodeViewer;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.WindowConstants;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.EventQueue;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shows syntax of one's own laid over a language: a JSON request body with
 * <code>{{variables}}</code> in it, as in an API client. The variables are highlighted
 * wherever they stand, also inside strings; those without a value are underlined; typing
 * <code>{{</code> offers the ones there are; and the body with the values filled in is shown
 * below.
 */
public final class VariablesDemoApp {

    /** What a variable looks like; the same expression highlights them and finds them. */
    private static final String VARIABLE = "\\{\\{([^{}\\r\\n]*)\\}\\}";
    private static final Pattern VARIABLE_PATTERN = Pattern.compile(VARIABLE);

    private static final String BODY = """
            {
              "url": "{{baseUrl}}/users/{{userId}}",
              "method": "POST",
              "headers": {
                "Authorization": "Bearer {{token}}",
                "X-Trace-Id": "{{traceId}}"
              },
              "body": {
                "name": "{{userName}}",
                "age": {{age}},
                "active": true,
                "note": "{{missing}} has no value yet"
              }
            }
            """;

    private final JFrame frame = new JFrame("SwingCraft4j Editor - custom syntax demo");
    private final JCodeEditor editor = new JCodeEditor();
    private final JCodeViewer preview = new JCodeViewer();
    private final DefaultTableModel variables = new DefaultTableModel(new Object[]{"Variable", "Value"}, 0);
    private final JLabel status = new JLabel(" ");
    private Language json;

    public static void main(String[] args) {
        EventQueue.invokeLater(() -> {
            DemoLookAndFeel.install();
            new VariablesDemoApp().show();
        });
    }

    private void show() {
        json = Languages.byId("json").orElseThrow();
        // JSON, with anything of the form {{name}} shown as a variable wherever it stands
        Language template = OverlayLanguage.over(json)
                .displayName("JSON with variables")
                .pattern(TokenType.VARIABLE, VARIABLE)
                .build();
        // a theme is adjusted the same way: variables in bold orange
        CodeTheme theme = CodeTheme.light().toBuilder()
                .style(TokenType.VARIABLE, new TokenStyle(new Color(0xE8590C), Font.BOLD))
                .build();

        variables.addRow(new Object[]{"baseUrl", "https://api.example.com"});
        variables.addRow(new Object[]{"userId", "42"});
        variables.addRow(new Object[]{"token", "abc123"});
        variables.addRow(new Object[]{"userName", "Raven"});
        variables.addRow(new Object[]{"age", "30"});
        variables.addTableModelListener(e -> refresh());

        editor.setTheme(theme);
        editor.setText(BODY);
        editor.setLanguage(template);
        editor.addEditListener(e -> refresh());
        AutoCompletion.install(editor).addProvider(new VariableProvider());

        preview.setTheme(theme);
        preview.setLineNumbersVisible(false);
        preview.setBracketMatching(false); // nobody puts a caret in the preview

        JTable table = new JTable(variables);
        table.putClientProperty("terminateEditOnFocusLost", true);
        JButton add = new JButton("Add");
        add.addActionListener(e -> variables.addRow(new Object[]{"name", "value"}));
        JButton remove = new JButton("Remove");
        remove.addActionListener(e -> {
            if (table.isEditing()) {
                table.getCellEditor().stopCellEditing();
            }
            if (table.getSelectedRow() >= 0) {
                variables.removeRow(table.getSelectedRow());
            }
        });
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
        buttons.add(add);
        buttons.add(remove);
        JPanel side = new JPanel(new BorderLayout());
        side.add(titled("Variables - edit a value, or type {{ in the body"), BorderLayout.NORTH);
        side.add(new JScrollPane(table), BorderLayout.CENTER);
        side.add(buttons, BorderLayout.SOUTH);

        JPanel body = new JPanel(new BorderLayout());
        body.add(titled("Request body"), BorderLayout.NORTH);
        body.add(new JScrollPane(editor), BorderLayout.CENTER);
        JPanel resolved = new JPanel(new BorderLayout());
        resolved.add(titled("With the values filled in"), BorderLayout.NORTH);
        resolved.add(new JScrollPane(preview), BorderLayout.CENTER);

        JSplitPane left = new JSplitPane(JSplitPane.VERTICAL_SPLIT, body, resolved);
        left.setResizeWeight(0.55);
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, side);
        split.setResizeWeight(0.68);

        status.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
        frame.add(split, BorderLayout.CENTER);
        frame.add(status, BorderLayout.SOUTH);
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.setSize(1100, 720);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        refresh();
        editor.requestFocusInWindow();
    }

    private static JLabel titled(String text) {
        JLabel label = new JLabel(text);
        label.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
        return label;
    }

    /** The variables of the table by name; a later row of the same name wins. */
    private Map<String, String> values() {
        Map<String, String> values = new LinkedHashMap<>();
        for (int row = 0; row < variables.getRowCount(); row++) {
            Object name = variables.getValueAt(row, 0);
            Object value = variables.getValueAt(row, 1);
            if (name != null && !name.toString().isBlank()) {
                values.put(name.toString().trim(), value == null ? "" : value.toString());
            }
        }
        return values;
    }

    /** Underlines the variables that have no value, and shows the body with the others filled in. */
    private void refresh() {
        Map<String, String> values = values();
        String text = editor.getText();
        List<Marker> markers = new ArrayList<>();
        StringBuilder resolved = new StringBuilder();
        Matcher matcher = VARIABLE_PATTERN.matcher(text);
        int copied = 0;
        while (matcher.find()) {
            String name = matcher.group(1).trim();
            String value = values.get(name);
            if (name.isEmpty()) {
                markers.add(Marker.error(matcher.start(), matcher.end(), "A variable needs a name"));
            } else if (value == null) {
                markers.add(Marker.warning(matcher.start(), matcher.end(), "Unresolved variable: " + name));
            }
            resolved.append(text, copied, matcher.start()).append(value != null ? value : matcher.group());
            copied = matcher.end();
        }
        resolved.append(text, copied, text.length());
        editor.setMarkers(markers);
        preview.setDocument(ArrayTextModel.of(resolved), json);
        status.setText(markers.isEmpty() ? "Every variable has a value"
                : markers.size() + (markers.size() == 1 ? " variable has" : " variables have") + " no value - hover the underlined text");
    }

    /** Offers the variables of the table once <code>{{</code> has been typed. */
    private final class VariableProvider implements CompletionProvider {

        /** Whether the word being typed comes right after <code>{{</code>. */
        private static boolean inPlaceholder(CompletionRequest request) {
            String before = request.lineBefore().substring(0, request.lineBefore().length() - request.prefix().length());
            return before.endsWith("{{");
        }

        /** Inside a placeholder only the variables are offered, not the words of the document. */
        @Override
        public boolean isExclusive(CompletionRequest request) {
            return inPlaceholder(request);
        }

        @Override
        public List<Completion> complete(CompletionRequest request) {
            if (!inPlaceholder(request)) {
                return List.of();
            }
            // closed already, as when the editor added the closing braces itself?
            TextModel model = request.model();
            boolean closed = request.offset() + 2 <= model.length()
                    && model.getText(request.offset(), request.offset() + 2).equals("}}");
            List<Completion> completions = new ArrayList<>();
            values().forEach((name, value) -> {
                Completion completion = new Completion(name, CompletionKind.VARIABLE).withDetail(value)
                        .withDocumentation("{{" + name + "}} stands for\n\n" + value);
                completions.add(closed ? completion : completion.withTemplate(name + "}}"));
            });
            return completions;
        }

        @Override
        public String triggerCharacters() {
            return "{"; // the list opens as soon as the second brace is typed
        }
    }
}
