package com.swingcraft4j.code.autocomplete;

import com.swingcraft4j.code.editor.JCodeEditor;
import com.swingcraft4j.code.lexer.Language;
import com.swingcraft4j.code.lexer.RuleLanguage;
import com.swingcraft4j.code.text.GapTextModel;
import org.junit.jupiter.api.Test;

import javax.swing.JComponent;
import javax.swing.KeyStroke;
import java.awt.event.KeyEvent;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SnippetSessionTest {

    private final JCodeEditor editor = new JCodeEditor();

    /** The text with {@code [} and {@code ]} around the selection, or {@code |} at the caret. */
    private String state() {
        StringBuilder text = new StringBuilder(editor.getText());
        if (editor.getSelectionStart() == editor.getSelectionEnd()) {
            return text.insert(editor.getCaretPosition(), '|').toString();
        }
        return text.insert(editor.getSelectionEnd(), ']').insert(editor.getSelectionStart(), '[').toString();
    }

    private Object tabBinding() {
        return editor.getInputMap(JComponent.WHEN_FOCUSED).get(KeyStroke.getKeyStroke(KeyEvent.VK_TAB, 0));
    }

    @Test
    void insertsTheSnippetAndStepsThroughItsTabStops() {
        editor.setText("x = ;");
        SnippetSession session = SnippetSession.start(editor, 4, 4, "max(${a}, ${b})");
        assertEquals("x = max([a], b);", state());
        assertTrue(session.isActive());
        assertEquals("snippet-next", tabBinding());

        editor.replaceSelection("left");
        assertEquals("x = max(left|, b);", state());
        session.next();
        assertEquals("x = max(left, [b]);", state(), "the later stop moved with the text typed before it");
        session.previous();
        assertEquals("x = max([left], b);", state(), "an earlier stop is found again with what was typed in it");
        session.next();
        editor.replaceSelection("right");
        session.next();
        assertEquals("x = max(left, right)|;", state(), "after the last stop the caret goes to the end of the snippet");
        assertFalse(session.isActive());
        assertEquals("insert-tab", tabBinding(), "Tab is given back");
    }

    @Test
    void putsTheCaretAtItsMarkedPlaceAndIndentsNewLines() {
        editor.setText("    void run() {\n        \n    }");
        int at = editor.getText().indexOf("\n    }");
        SnippetSession session = SnippetSession.start(editor, at, at, "if (${condition}) {\n\t$0\n}");
        assertEquals("    void run() {\n        if ([condition]) {\n            \n        }\n    }", state(),
                "lines after the first get the indentation of the line, and a tab one level more");
        session.next();
        assertEquals("    void run() {\n        if (condition) {\n            |\n        }\n    }", state());
        assertFalse(session.isActive());
    }

    @Test
    void tabStopsOfTheSameNameChangeTogether() {
        editor.setText("");
        SnippetSession session = SnippetSession.start(editor, 0, 0, "for (int ${i} = 0; ${i} < ${count}; ${i}++) {\n\t$0\n}");
        assertEquals("for (int [i] = 0; i < count; i++) {\n    \n}", state());

        editor.replaceSelection("k");
        assertEquals("for (int k| = 0; k < count; k++) {\n    \n}", state(), "all three follow, and the caret stays put");
        editor.replaceSelection("ey");
        assertEquals("for (int key| = 0; key < count; key++) {\n    \n}", state());

        session.next();
        assertEquals("for (int key = 0; key < [count]; key++) {\n    \n}", state(),
                "Tab passes over the stops that repeat a name already filled in");
        editor.replaceSelection("n");
        session.next();
        assertEquals("for (int key = 0; key < n; key++) {\n    |\n}", state());
        assertFalse(session.isActive());

        editor.undo();
        assertEquals("for (int key = 0; key < count; key++) {\n    \n}", editor.getText());
        editor.undo();
        assertEquals("for (int k = 0; k < count; k++) {\n    \n}", editor.getText(), "one undo takes back a change in all its places");
        editor.undo();
        editor.undo();
        assertEquals("", editor.getText());
    }

    @Test
    void isOneUndoStepAndEndsWhenTheCaretLeaves() {
        editor.setText("a  z");
        SnippetSession session = SnippetSession.start(editor, 2, 2, "f(${x})");
        assertEquals("a f([x]) z", state());
        editor.select(0, 0);
        assertFalse(session.isActive(), "moving the caret out of the snippet ends the session");
        assertEquals("insert-tab", tabBinding());
        editor.undo();
        assertEquals("a  z", editor.getText());
    }

    @Test
    void withoutTabStopsThereIsNothingToStepThrough() {
        editor.setText("list.");
        SnippetSession session = SnippetSession.start(editor, 5, 5, "size()");
        assertEquals("list.size()|", state());
        assertFalse(session.isActive());
        assertEquals("insert-tab", tabBinding());
    }

    @Test
    void completionInsertsSnippetsAndOffersThemBesideKeywords() {
        Language java = RuleLanguage.builder("java", "Java").keywords("for", "final").build();
        AutoCompletion completion = AutoCompletion.install(editor);
        editor.setDocument(new GapTextModel("for"), java);
        editor.select(3, 3);
        List<Completion> offered = completion.getCompletions();
        assertEquals(List.of("for", "foreach"), offered.stream().map(Completion::text).toList(),
                "the keyword itself is already typed; its snippet and the longer one are offered");
        assertEquals(CompletionKind.SNIPPET, offered.get(0).kind());

        completion.accept(offered.get(0));
        assertEquals("for (int [i] = 0; i < count; i++) {\n    \n}", state());

        // choosing a plain word while filling in a tab stop does not end the snippet
        editor.replaceSelection("fi");
        completion.accept(new Completion("final", CompletionKind.KEYWORD));
        assertEquals("for (int final| = 0; final < count; final++) {\n    \n}", state());
        assertEquals("snippet-next", tabBinding());
        editor.getActionMap().get("snippet-next").actionPerformed(null);
        assertEquals("for (int final = 0; final < [count]; final++) {\n    \n}", state());
        editor.getActionMap().get("snippet-end").actionPerformed(null);
        editor.undo();
        editor.undo();
        editor.undo();
        assertEquals("for", editor.getText());
    }

    @Test
    void aProviderCanCompleteAfterATriggerChar() {
        AutoCompletion completion = AutoCompletion.install(editor);
        String[] seen = new String[2];
        completion.addProvider(new CompletionProvider() {
            @Override
            public List<Completion> complete(CompletionRequest request) {
                if (request.charBeforePrefix() != '.') {
                    return List.of();
                }
                seen[0] = request.qualifier();
                seen[1] = request.prefix();
                return List.of(new Completion("size", CompletionKind.METHOD).withTemplate("size()"),
                        new Completion("stream", CompletionKind.METHOD));
            }

            @Override
            public String triggerCharacters() {
                return ".";
            }
        });
        Language toy = RuleLanguage.builder("toy", "Toy").keywords("static", "super").build();
        editor.setDocument(new GapTextModel("final items; items."), toy);
        editor.select(19, 19);
        assertEquals(List.of("size", "stream"), completion.getCompletions().stream().map(Completion::text).toList(),
                "right after the dot only the members are offered, not keywords or other words");
        assertEquals("items", seen[0]);
        assertEquals("", seen[1]);

        editor.replaceSelection("s");
        assertEquals(List.of("size", "stream"), completion.getCompletions().stream().map(Completion::text).toList(),
                "keywords are still left out after a dot");
        assertEquals("s", seen[1]);
        completion.accept(completion.getCompletions().get(0));
        assertEquals("final items; items.size()|", state());

        // a word of the document with the same name does not push the member aside
        editor.setDocument(new GapTextModel("size = 1; items.si"), toy);
        editor.select(18, 18);
        List<Completion> offered = completion.getCompletions();
        assertEquals(1, offered.size());
        assertEquals(CompletionKind.METHOD, offered.get(0).kind());
    }

    @Test
    void parameterHintsShowTheArgumentTheCaretIsIn() {
        ParameterHints hints = ParameterHints.install(editor,
                name -> name.equals("substring") ? List.of("int begin", "int end") : null);
        editor.setText("x = text.substring(1, y.length()); other(2);");
        int open = editor.getText().indexOf('(') + 1;
        editor.select(open, open);
        assertEquals("<html><nobr>substring(<b>int begin</b>, int end)</nobr></html>", hints.getHint());
        int second = editor.getText().indexOf(", y") + 2;
        editor.select(second, second);
        assertEquals("<html><nobr>substring(int begin, <b>int end</b>)</nobr></html>", hints.getHint());

        int inner = editor.getText().indexOf("length(") + 7;
        editor.select(inner, inner);
        assertNull(hints.getHint(), "inside the inner call, which is not known");
        int afterInner = inner + 1;
        editor.select(afterInner, afterInner);
        assertEquals("<html><nobr>substring(int begin, <b>int end</b>)</nobr></html>", hints.getHint(),
                "past the inner call the caret is in the outer one again");

        int outside = editor.getText().indexOf(';');
        editor.select(outside, outside);
        assertNull(hints.getHint());
        int unknown = editor.getText().indexOf("other(") + 6;
        editor.select(unknown, unknown);
        assertNull(hints.getHint());

        assertEquals(new ParameterHints.Call("", "f", 2, 5), ParameterHints.callAt(new GapTextModel("a = f(1, g(2, 3), "), 18));
        assertEquals(new ParameterHints.Call("app.headers", "set", 0, 15),
                ParameterHints.callAt(new GapTextModel("app.headers.set("), 16));
        assertNull(ParameterHints.callAt(new GapTextModel("list = [f(1), "), 14), "a list is not a call");
    }
}
