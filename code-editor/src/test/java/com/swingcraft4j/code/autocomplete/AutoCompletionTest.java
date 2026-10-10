package com.swingcraft4j.code.autocomplete;

import com.swingcraft4j.code.editor.JCodeEditor;
import com.swingcraft4j.code.lexer.Language;
import com.swingcraft4j.code.lexer.RuleLanguage;
import com.swingcraft4j.code.text.GapTextModel;
import org.junit.jupiter.api.Test;

import java.awt.EventQueue;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutoCompletionTest {

    private static final Language TOY = RuleLanguage.builder("toy", "Toy")
            .keywords("return", "record", "while")
            .literals("true")
            .build();
    private static final Language SQL = RuleLanguage.builder("mini-sql", "Mini SQL")
            .ignoreCase().keywords("select", "set", "from").build();

    private final JCodeEditor editor = new JCodeEditor();
    private final AutoCompletion completion = AutoCompletion.install(editor);

    /** Shows the text with the caret where the {@code |} is. */
    private void open(String textWithCaret, Language language) {
        int caret = textWithCaret.indexOf('|');
        editor.setDocument(new GapTextModel(textWithCaret.replace("|", "")), language);
        editor.select(caret, caret);
    }

    private List<String> offered() {
        return completion.getCompletions().stream().map(Completion::text).toList();
    }

    @Test
    void findsTheWordBeforeTheCaret() {
        open("int total = to|", TOY);
        assertEquals("to", completion.getPrefix());
        open("call(|", TOY);
        assertEquals("", completion.getPrefix());
        open("x = 12ab|", TOY);
        assertEquals("ab", completion.getPrefix(), "a word does not start with a digit");
        open("a$b_c1|d", TOY);
        assertEquals("a$b_c1", completion.getPrefix(), "only what is before the caret counts");
    }

    @Test
    void offersKeywordsAndWordsOfTheDocument() {
        open("int result = 1;\nreduce(result);\nre|", TOY);
        assertEquals(List.of("record", "reduce", "result", "return"), offered());
        assertEquals(CompletionKind.KEYWORD, completion.getCompletions().get(0).kind());
        assertEquals(CompletionKind.WORD, completion.getCompletions().get(1).kind());
    }

    @Test
    void leavesOutTheWordBeingTypedAndExactMatches() {
        open("valid val|ue", TOY);
        assertEquals(List.of("valid"), offered(), "the word around the caret is not a suggestion for itself");
        open("true tr|", TOY);
        assertEquals(List.of("true"), offered());
        assertEquals(CompletionKind.KEYWORD, completion.getCompletions().get(0).kind(),
                "a word of the document gives way to the keyword of the same name");
        open("true|", TOY);
        assertEquals(List.of(), offered());
    }

    @Test
    void matchesWithoutRegardToCaseButPrefersTheCaseTyped() {
        open("Total total TOTAL to|", TOY);
        assertEquals(List.of("total", "TOTAL", "Total"), offered());
        open("Total total TOTAL TO|", TOY);
        assertEquals(List.of("TOTAL", "Total", "total"), offered());
    }

    @Test
    void matchesTheStartsOfWordsInsideAName() {
        open("totalValue IllegalStateException MAX_ITEMS tvShow\ntv|", TOY);
        assertEquals(List.of("tvShow", "totalValue"), offered(), "a real prefix comes before a looser match");
        open("totalValue IllegalStateException MAX_ITEMS\nISE|", TOY);
        assertEquals(List.of("IllegalStateException"), offered());
        open("totalValue IllegalStateException MAX_ITEMS\nmi|", TOY);
        assertEquals(List.of("MAX_ITEMS"), offered());
        open("totalValue IllegalStateException MAX_ITEMS\nval|", TOY);
        assertEquals(List.of("totalValue"), offered(), "a word in the middle of a name can be typed too");
        open("totalValue IllegalStateException MAX_ITEMS\nxq|", TOY);
        assertEquals(List.of(), offered());

        assertArrayEquals(new int[]{0, 5}, FuzzyMatch.match("tv", "totalValue").positions());
        assertArrayEquals(new int[]{0, 7, 12}, FuzzyMatch.match("ISE", "IllegalStateException").positions());
        assertEquals(FuzzyMatch.SCATTERED, FuzzyMatch.match("rtn", "return").tier());
        assertNull(FuzzyMatch.match("etr", "return"), "scattered letters must begin with the first one");
        assertNull(FuzzyMatch.match("r", "after"), "one letter matches only a start");
    }

    @Test
    void offersKeywordsInCapitalsWhenTypedInCapitals() {
        open("SE|", SQL);
        assertEquals(List.of("SET", "SELECT"), offered());
        open("se|", SQL);
        assertEquals(List.of("set", "select"), offered());
    }

    @Test
    void offersEverythingForAnEmptyPrefix() {
        open("alpha beta |", TOY);
        assertTrue(offered().containsAll(List.of("alpha", "beta", "return", "while", "true")));
    }

    @Test
    void usesProvidersAddedByTheApplication() {
        open("pri|", null);
        assertEquals(List.of(), offered());
        completion.addProvider(request -> List.of(new Completion("println", CompletionKind.METHOD),
                new Completion("printf", CompletionKind.METHOD), new Completion("other", CompletionKind.METHOD)));
        assertEquals(List.of("printf", "println"), offered());
    }

    @Test
    void aProviderCanKeepTheOthersOut() {
        completion.addProvider(new CompletionProvider() {
            @Override
            public boolean isExclusive(CompletionRequest request) {
                return request.lineBefore().endsWith("{{" + request.prefix());
            }

            @Override
            public List<Completion> complete(CompletionRequest request) {
                return isExclusive(request)
                        ? List.of(new Completion("token", CompletionKind.VARIABLE), new Completion("total", CompletionKind.VARIABLE))
                        : List.of();
            }
        });
        open("return total; {{t|", TOY);
        assertEquals(List.of(CompletionKind.VARIABLE, CompletionKind.VARIABLE),
                completion.getCompletions().stream().map(Completion::kind).toList(),
                "inside the placeholder neither the keyword \"true\" nor the word \"total\" is offered");
        open("return total; t|", TOY);
        assertEquals(List.of("true", "total"), offered(), "elsewhere everything is as usual");
    }

    @Test
    void offersTheSameTextOncePerKind() {
        open("size siz|", null);
        completion.addProvider(request -> List.of(
                new Completion("size", CompletionKind.METHOD).withDetail("int"),
                new Completion("size", CompletionKind.METHOD).withDetail("again"),
                new Completion("size", CompletionKind.FIELD)));
        List<Completion> offered = completion.getCompletions();
        assertEquals(List.of(CompletionKind.METHOD, CompletionKind.FIELD), offered.stream().map(Completion::kind).toList(),
                "the bare word gives way, and the second method of the same name is a duplicate");
        assertEquals("int", offered.get(0).detail());
    }

    @Test
    void acceptingReplacesTheWordAsOneUndoStep() {
        open("x = res|;", TOY);
        completion.accept(new Completion("result"));
        assertEquals("x = result;", editor.getText());
        assertEquals(10, editor.getCaretPosition());
        editor.undo();
        assertEquals("x = res;", editor.getText());
        assertFalse(completion.isShowing());
    }

    @Test
    void readsOnlyTheLinesNearTheCaretOfAHugeDocument() {
        StringBuilder text = new StringBuilder("farAwayWord\n");
        text.append("filler line\n".repeat(5000));
        text.append("nearbyWord\n");
        int caret = text.length();
        text.append("\n").append("filler line\n".repeat(5000)).append("anotherFarWord\n");
        editor.setDocument(new GapTextModel(text), null);
        editor.select(caret, caret);
        List<String> words = offered();
        assertTrue(words.contains("nearbyWord"));
        assertTrue(words.contains("filler"));
        assertFalse(words.contains("farAwayWord"));
        assertFalse(words.contains("anotherFarWord"));
    }

    @Test
    void suggestionsOfABackgroundProviderJoinWhenTheyArrive() throws Exception {
        CountDownLatch asked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Thread[] thread = new Thread[1];
        int[] calls = new int[1];
        completion.addAsyncProvider(request -> {
            thread[0] = Thread.currentThread();
            calls[0]++;
            asked.countDown();
            try {
                release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return List.of(new Completion("remoteValue", CompletionKind.FIELD), new Completion("remoteCall", CompletionKind.METHOD));
        });
        // the editor is worked from the event dispatch thread, as in an application
        EventQueue.invokeAndWait(() -> {
            open("rem|", TOY);
            assertEquals(List.of(), offered(), "nothing yet: the provider has only just been asked");
        });
        assertTrue(asked.await(5, TimeUnit.SECONDS));
        assertNotEquals(Thread.currentThread(), thread[0]);
        release.countDown();
        List<String>[] later = new List[1];
        for (int i = 0; i < 100 && (later[0] == null || later[0].isEmpty()); i++) {
            Thread.sleep(20);
            EventQueue.invokeAndWait(() -> later[0] = offered());
        }
        assertEquals(List.of("remoteCall", "remoteValue"), later[0]);

        EventQueue.invokeAndWait(() -> {
            editor.replaceSelection("oteV");
            assertEquals(List.of("remoteValue"), offered(), "typing on narrows down what was received");
        });
        assertEquals(1, calls[0], "without asking the provider again");
    }

    @Test
    void aTriggerCharAsksForSuggestionsAlsoWhenItIsTypedWithItsPartner() {
        class Typed extends JCodeEditor {

            void type(char c) {
                processKeyEvent(new KeyEvent(this, KeyEvent.KEY_TYPED, 0, 0, KeyEvent.VK_UNDEFINED, c));
            }
        }
        Typed typed = new Typed();
        List<String> asked = new ArrayList<>();
        new AutoCompletion(typed).addProvider(new CompletionProvider() {
            @Override
            public List<Completion> complete(CompletionRequest request) {
                asked.add(request.lineBefore());
                return List.of();
            }

            @Override
            public String triggerCharacters() {
                return "{";
            }
        });
        typed.type('{');
        assertEquals("{}", typed.getText(), "the editor closes the brace");
        assertEquals(List.of("{"), asked);
        typed.type('{');
        assertEquals("{{}}", typed.getText());
        assertEquals(List.of("{", "{{"), asked);
    }
}
