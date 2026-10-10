package com.swingcraft4j.code.demo;

import com.swingcraft4j.code.autocomplete.AutoCompletion;
import com.swingcraft4j.code.autocomplete.Completion;
import com.swingcraft4j.code.autocomplete.CompletionKind;
import com.swingcraft4j.code.autocomplete.CompletionProvider;
import com.swingcraft4j.code.autocomplete.CompletionRequest;
import com.swingcraft4j.code.editor.JCodeField;
import com.swingcraft4j.code.lexer.Language;
import com.swingcraft4j.code.lexer.Languages;
import com.swingcraft4j.code.lexer.RuleLanguage;
import com.swingcraft4j.code.lexer.TokenType;
import com.swingcraft4j.code.marker.Marker;
import com.swingcraft4j.code.theme.CodeTheme;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.EventQueue;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Shows {@link JCodeField}: text fields for one line of code. A template with <code>{{variables}}</code>
 * and a component at each end, a condition of SQL with code completion, a value of JSON, and a
 * regular expression that is checked while it is typed. A plain text field stands below them, to
 * see that they look alike.
 */
public final class FieldDemoApp {

    /** What a variable looks like; the same expression highlights them and finds them. */
    private static final String VARIABLE = "\\{\\{([^{}\\r\\n]*)\\}\\}";
    private static final Pattern VARIABLE_PATTERN = Pattern.compile(VARIABLE);
    private static final Map<String, String> VALUES = Map.of(
            "name", "Raven",
            "orderId", "A-1042",
            "date", "12 October");

    private final JFrame frame = new JFrame("SwingCraft4j Editor - field demo");
    private final JCodeField template = new JCodeField("Hello {{name}}, your order {{orderId}} ships on {{day}}");
    private final JCodeField where = new JCodeField("status = 'active' AND age >= 18");
    private final JCodeField json = new JCodeField("{\"name\": \"Raven\", \"tags\": [\"a\", \"b\"], \"active\": true}");
    private final JCodeField regex = new JCodeField("(\\d{4})-(\\d{2}");
    private final List<JCodeField> fields = List.of(template, where, json, regex);
    private final JLabel status = new JLabel(" ");

    public static void main(String[] args) {
        EventQueue.invokeLater(() -> {
            DemoLookAndFeel.install();
            new FieldDemoApp().show();
        });
    }

    private void show() {
        // the template is none of the languages: plain text, but for the variables
        template.setLanguage(RuleLanguage.builder("template", "Template").pattern(TokenType.VARIABLE, VARIABLE).build());
        template.setPlaceholder("A message - type {{ for the variables");
        JComboBox<String> kind = new JComboBox<>(new String[]{"Email", "SMS", "Letter"});
        JButton preview = new JButton("Preview");
        // with FlatLaf: a button without a border of its own, as in a toolbar
        preview.putClientProperty("JButton.buttonType", "toolBarButton");
        preview.setFocusable(false);
        template.setLeadingComponent(kind);
        template.setTrailingComponent(preview);
        template.getEditor().addEditListener(e -> checkTemplate());
        new AutoCompletion(template.getEditor()).addProvider(new VariableProvider());
        Runnable show = () -> status.setText(kind.getSelectedItem() + ": " + resolve(template.getText(), new ArrayList<>()));
        template.addActionListener(e -> show.run());
        preview.addActionListener(e -> show.run());

        // the keywords of the language, and the words of the text
        Language sql = Languages.byId("sql").orElse(null);
        where.setLanguage(sql);
        where.setPlaceholder("A condition, such as age >= 18");
        AutoCompletion.install(where.getEditor());
        where.addActionListener(e -> status.setText("SELECT * FROM users WHERE " + where.getText()));

        json.setLanguage(Languages.byId("json").orElse(null));
        json.setPlaceholder("A value of JSON");
        json.getEditor().setBracketMatching(true);
        json.addActionListener(e -> status.setText(json.getText().length() + " chars of JSON"));

        regex.setPlaceholder("A regular expression");
        regex.getEditor().addEditListener(e -> checkRegex());
        regex.addActionListener(e -> checkRegex());

        JCheckBox editable = new JCheckBox("Editable", true);
        editable.addActionListener(e -> fields.forEach(field -> field.setEditable(editable.isSelected())));
        JCheckBox enabled = new JCheckBox("Enabled", true);
        enabled.addActionListener(e -> fields.forEach(field -> field.setEnabled(enabled.isSelected())));
        JCheckBox dark = new JCheckBox("Dark");
        dark.addActionListener(e -> useTheme(dark.isSelected() ? CodeTheme.dark() : CodeTheme.light()));
        JPanel options = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        options.add(editable);
        options.add(enabled);
        options.add(dark);

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        addRow(form, "Template", template);
        addRow(form, "Where", where);
        addRow(form, "JSON", json);
        addRow(form, "Regex", regex);
        addRow(form, "Text field", new JTextField("A JTextField, to compare"));
        addRow(form, "", options);

        status.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
        frame.add(form, BorderLayout.NORTH);
        frame.add(status, BorderLayout.SOUTH);
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.setSize(720, 330);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        useTheme(CodeTheme.light());
        checkTemplate();
        checkRegex();
        status.setText("Enter in a field shows what it stands for; Tab goes on to the next field");
        template.requestFocusInWindow();
    }

    private static void addRow(JPanel form, String title, Component component) {
        GridBagConstraints label = new GridBagConstraints();
        label.gridx = 0;
        label.anchor = GridBagConstraints.BASELINE_LEADING;
        label.insets = new Insets(0, 0, 8, 12);
        form.add(new JLabel(title), label);
        GridBagConstraints field = new GridBagConstraints();
        field.gridx = 1;
        field.weightx = 1;
        field.fill = GridBagConstraints.HORIZONTAL;
        field.anchor = GridBagConstraints.BASELINE_LEADING;
        field.insets = new Insets(0, 0, 8, 0);
        form.add(component, field);
    }

    /** Gives every field the colours of the theme, and the look and feel that goes with them. */
    private void useTheme(CodeTheme theme) {
        fields.forEach(field -> field.getEditor().setTheme(theme));
        DemoLookAndFeel.follow(theme);
    }

    /** Underlines the variables of the template that have no value. */
    private void checkTemplate() {
        List<Marker> markers = new ArrayList<>();
        resolve(template.getText(), markers);
        template.getEditor().setMarkers(markers);
    }

    /**
     * @param markers gets a marker for each variable of the text that has no value
     * @return the text with the values of the variables filled in
     */
    private static String resolve(String text, List<Marker> markers) {
        StringBuilder resolved = new StringBuilder();
        Matcher matcher = VARIABLE_PATTERN.matcher(text);
        int copied = 0;
        while (matcher.find()) {
            String name = matcher.group(1).trim();
            String value = VALUES.get(name);
            if (value == null) {
                markers.add(Marker.warning(matcher.start(), matcher.end(), "Unresolved variable: " + name));
            }
            resolved.append(text, copied, matcher.start()).append(value != null ? value : matcher.group());
            copied = matcher.end();
        }
        return resolved.append(text, copied, text.length()).toString();
    }

    /** Marks where the regular expression is wrong, and gives the field the outline of an error. */
    private void checkRegex() {
        String text = regex.getText();
        try {
            Pattern.compile(text);
            regex.getEditor().setMarkers(List.of());
            // with FlatLaf: what is set on the field goes to the text field that paints its border
            regex.putClientProperty("JComponent.outline", null);
            status.setText(text.isEmpty() ? " " : "The regular expression is fine");
        } catch (PatternSyntaxException e) {
            int at = Math.max(0, Math.min(e.getIndex(), text.length()));
            // the place of the error is often the end of the text: mark the char before it then
            int start = at == text.length() ? Math.max(0, at - 1) : at;
            regex.getEditor().setMarkers(List.of(Marker.error(start, Math.max(at, start + 1), e.getDescription())));
            regex.putClientProperty("JComponent.outline", "error");
            status.setText(e.getDescription() + " - hover the underlined text");
        }
    }

    /** Offers the variables once <code>{{</code> has been typed. */
    private static final class VariableProvider implements CompletionProvider {

        /** Whether the word being typed comes right after <code>{{</code>. */
        private static boolean inPlaceholder(CompletionRequest request) {
            String before = request.lineBefore().substring(0, request.lineBefore().length() - request.prefix().length());
            return before.endsWith("{{");
        }

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
            boolean closed = request.offset() + 2 <= request.model().length()
                    && request.model().getText(request.offset(), request.offset() + 2).equals("}}");
            List<Completion> completions = new ArrayList<>();
            VALUES.forEach((name, value) -> {
                Completion completion = new Completion(name, CompletionKind.VARIABLE).withDetail(value);
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
