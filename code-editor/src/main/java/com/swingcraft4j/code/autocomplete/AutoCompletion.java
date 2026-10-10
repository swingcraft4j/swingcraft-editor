package com.swingcraft4j.code.autocomplete;

import com.swingcraft4j.code.editor.JCodeEditor;
import com.swingcraft4j.code.text.ArrayTextModel;
import com.swingcraft4j.code.text.TextModel;
import com.swingcraft4j.code.viewer.JCodeViewer;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JWindow;
import javax.swing.KeyStroke;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.event.ChangeEvent;
import javax.swing.plaf.basic.BasicSplitPaneUI;
import javax.swing.event.ChangeListener;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Code completion for a {@link JCodeEditor}: a popup of suggestions that opens as a word is
 * typed, or on Ctrl+Space, and is narrowed down by typing on.
 * <pre>{@code
 * AutoCompletion completion = AutoCompletion.install(editor);
 * completion.addProvider(request -> List.of(new Completion("println", CompletionKind.METHOD)));
 * }</pre>
 * What is typed need not be the start of a suggestion: {@code tv} finds {@code totalValue}.
 * While the popup is open, Up, Down, Page Up and Page Down move through it, Enter or Tab
 * inserts the selected suggestion and Escape closes it. The focus stays in the editor.
 */
public final class AutoCompletion {

    private static final int MAX_SUGGESTIONS = 200;
    private static final int VISIBLE_ROWS = 10;
    private static final int DOCUMENTATION_WIDTH = 300;
    private static final int MAX_DOCUMENTATION_HEIGHT = 260;
    private static final int MIN_POPUP_WIDTH = 160;
    private static final int MIN_DOCUMENTATION_WIDTH = 120;
    private static final int DIVIDER_SIZE = 5;
    private static final String SHOW = "completion-show";

    /** A suggestion with the positions in its text that matched what was typed. */
    private record Suggestion(Completion completion, int[] matched) {
    }

    /** What the answers of the background providers were asked for. */
    private record AsyncContext(TextModel model, int prefixStart, String prefix) {
    }

    private final JCodeEditor editor;
    private final List<CompletionProvider> providers = new ArrayList<>();
    private final List<CompletionProvider> asyncProviders = new ArrayList<>();
    private boolean autoActivation = true;
    private int autoActivationLength = 2;

    private final DefaultListModel<Suggestion> suggestions = new DefaultListModel<>();
    private final JList<Suggestion> list = new JList<>(suggestions);
    private final JScrollPane scrollPane = new JScrollPane(list);
    /** What is said about the selected suggestion, where it has documentation. */
    private final JTextArea documentation = new JTextArea();
    private final JScrollPane documentationScroll = new JScrollPane(documentation);
    /** What a snippet inserts, highlighted as the editor would. */
    private final JCodeViewer preview = new JCodeViewer();
    private final JScrollPane previewScroll = new JScrollPane(preview);
    /** The one of the two that the selected suggestion has. */
    private final CardLayout sideCards = new CardLayout();
    private final JPanel side = new JPanel(sideCards);
    /** What the side shows, so that it is not set again, and scrolled back, while it stays the same. */
    private String sideText = "";
    /** The list at the left and the documentation of the selected suggestion at the right, with a divider to drag between them. */
    private final JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, scrollPane, side);
    private final JPanel content = new JPanel(new BorderLayout());
    private final ResizeGrip grip = new ResizeGrip();
    private JWindow window;
    /** The width and the height the user gave the list by dragging, or that were set; 0 while the popup chooses it. */
    private int popupWidth;
    private int popupHeight;
    private int documentationWidth = DOCUMENTATION_WIDTH;
    private final MouseAdapter dividerListener = new MouseAdapter() {
        @Override
        public void mouseReleased(MouseEvent e) {
            dividerDragged();
        }
    };
    /** Whether the popup stands above the word, for want of room below it. */
    private boolean above;
    private final List<ChangeListener> popupSizeListeners = new CopyOnWriteArrayList<>();

    /** Where the word being completed starts; valid while the popup is open. */
    private int prefixStart;
    /** Whether the popup was asked for, and so stays open when nothing is typed yet. */
    private boolean explicit;
    /** Whether suggestions are wanted at the caret, so that late answers may still open the popup. */
    private boolean wanted;
    private boolean accepting;
    // what the editor looked like at the last caret event, to tell typing from moving about
    private TextModel lastModel;
    private int lastLength;
    private int lastCaret;

    // answers of the background providers, and what they are answers to
    private ExecutorService executor;
    private AsyncContext asyncContext;
    private List<Completion> asyncResults = new ArrayList<>();
    private int asyncGeneration;
    private int asyncPending;

    private final KeyOverrides keys;
    /** The snippet whose tab stops are being filled in, if any. */
    private SnippetSession snippet;
    private final ChangeListener caretListener = e -> caretMoved();
    private final FocusListener focusListener = new FocusAdapter() {
        @Override
        public void focusLost(FocusEvent e) {
            hide();
        }
    };
    private final ComponentListener editorListener = new ComponentAdapter() {
        @Override
        public void componentMoved(ComponentEvent e) {
            // the editor was scrolled: follow the word, or close once it is out of sight
            if (isShowing()) {
                position();
            }
        }
    };
    private final ComponentListener ownerListener = new ComponentAdapter() {
        @Override
        public void componentMoved(ComponentEvent e) {
            hide();
        }

        @Override
        public void componentResized(ComponentEvent e) {
            hide();
        }
    };

    /** Adds completion to an editor, with no providers yet. */
    public AutoCompletion(JCodeEditor editor) {
        this.editor = editor;
        this.keys = new KeyOverrides(editor);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setFocusable(false);
        list.setCellRenderer(new Renderer());
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int index = list.locationToIndex(e.getPoint());
                if (index >= 0) {
                    list.setSelectedIndex(index);
                    accept();
                }
            }
        });
        list.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && isShowing()) {
                position();
            }
        });
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        documentation.setEditable(false);
        documentation.setFocusable(false);
        documentation.setLineWrap(true);
        documentation.setWrapStyleWord(true);
        scrollPane.setMinimumSize(new Dimension(MIN_POPUP_WIDTH, 0));
        preview.setLineNumbersVisible(false);
        preview.setFoldingEnabled(false);
        preview.setBracketMatching(false);
        preview.setIndentGuides(false);
        preview.setFocusable(false);
        preview.setComponentPopupMenu(null);
        side.add(documentationScroll, "text");
        side.add(previewScroll, "code");
        side.setMinimumSize(new Dimension(MIN_DOCUMENTATION_WIDTH, 0));
        split.setContinuousLayout(true);
        split.setBorder(null);
        listenToDivider();
        content.add(split, BorderLayout.CENTER);
        content.add(grip, BorderLayout.SOUTH);

        int menu = GraphicsEnvironment.isHeadless()
                ? InputEvent.CTRL_DOWN_MASK
                : Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        editor.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, menu), SHOW);
        action(SHOW, this::showCompletions);
        action("completion-up", () -> move(-1));
        action("completion-down", () -> move(1));
        action("completion-page-up", () -> move(-VISIBLE_ROWS));
        action("completion-page-down", () -> move(VISIBLE_ROWS));
        action("completion-accept", this::accept);
        action("completion-hide", this::hide);

        editor.addSelectionListener(caretListener);
        editor.addFocusListener(focusListener);
        editor.addComponentListener(editorListener);
        remember();
    }

    /**
     * Adds completion that offers the keywords of the language, the snippets that come with
     * the library for it, and the words in the document.
     */
    public static AutoCompletion install(JCodeEditor editor) {
        AutoCompletion completion = new AutoCompletion(editor);
        completion.addProvider(CompletionProviders.keywords());
        completion.addProvider(CompletionProviders.snippets());
        completion.addProvider(CompletionProviders.words());
        return completion;
    }

    /** Removes completion from the editor again. */
    public void uninstall() {
        hide();
        editor.removeSelectionListener(caretListener);
        editor.removeFocusListener(focusListener);
        editor.removeComponentListener(editorListener);
        int menu = GraphicsEnvironment.isHeadless()
                ? InputEvent.CTRL_DOWN_MASK
                : Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        editor.getInputMap(JComponent.WHEN_FOCUSED).remove(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, menu));
        if (window != null) {
            window.dispose();
            window = null;
        }
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }

    /** Adds a source of suggestions that answers at once, on the event dispatch thread. */
    public void addProvider(CompletionProvider provider) {
        providers.add(provider);
    }

    /**
     * Adds a source of suggestions that may take its time, such as one asking a server. It is
     * called on a background thread and its suggestions join the list when they arrive, if
     * the caret is still at the same word. Further typing in that word narrows down the
     * suggestions already received instead of asking again.
     * <p>
     * The provider must not read {@link CompletionRequest#model()}; the rest of the request is
     * a snapshot it may use freely.
     */
    public void addAsyncProvider(CompletionProvider provider) {
        asyncProviders.add(provider);
    }

    public void removeProvider(CompletionProvider provider) {
        providers.remove(provider);
        asyncProviders.remove(provider);
    }

    /**
     * The size of the list of the popup as the user made it or as it was set, or null while
     * the popup sizes itself. A width or a height of 0 is one the popup still chooses itself.
     */
    public Dimension getPopupSize() {
        return popupWidth == 0 && popupHeight == 0 ? null : new Dimension(popupWidth, popupHeight);
    }

    /**
     * Sets the size of the list of the popup, as the user does by dragging the bar at its
     * edge: the list is that wide, and grows no taller than that, with as many rows as fit.
     * A width or a height of 0 leaves that one to the popup, and null both. An application
     * that remembers the size of the user sets it here; see {@link #addPopupSizeListener}.
     */
    public void setPopupSize(Dimension size) {
        popupWidth = size == null ? 0 : Math.max(0, size.width);
        popupHeight = size == null ? 0 : Math.max(0, size.height);
        if (isShowing()) {
            position();
        }
    }

    /** The width of the documentation beside the list. */
    public int getDocumentationWidth() {
        return documentationWidth;
    }

    /** Sets the width of the documentation beside the list, as the user does by dragging the divider between the two. */
    public void setDocumentationWidth(int width) {
        documentationWidth = Math.max(MIN_DOCUMENTATION_WIDTH, width);
        if (isShowing()) {
            position();
        }
    }

    /** The divider is part of the Look and Feel, so it is another one after the Look and Feel changed. */
    private void listenToDivider() {
        if (split.getUI() instanceof BasicSplitPaneUI ui) {
            ui.getDivider().removeMouseListener(dividerListener);
            ui.getDivider().addMouseListener(dividerListener);
        }
    }

    /** Takes the widths of the list and of the documentation from where the user left the divider. */
    private void dividerDragged() {
        if (!isShowing() || !side.isVisible()) {
            return;
        }
        int total = split.getWidth() - split.getDividerSize();
        int listWidth = Math.max(MIN_POPUP_WIDTH, Math.min(split.getDividerLocation(), total - MIN_DOCUMENTATION_WIDTH));
        popupWidth = listWidth;
        documentationWidth = total - listWidth;
        firePopupSizeChanged();
    }

    private void firePopupSizeChanged() {
        ChangeEvent event = new ChangeEvent(this);
        for (ChangeListener listener : popupSizeListeners) {
            listener.stateChanged(event);
        }
    }

    /** Adds a listener told when the user has given the popup or its documentation another size by dragging. */
    public void addPopupSizeListener(ChangeListener listener) {
        popupSizeListeners.add(listener);
    }

    public void removePopupSizeListener(ChangeListener listener) {
        popupSizeListeners.remove(listener);
    }

    public boolean isAutoActivation() {
        return autoActivation;
    }

    /** Whether the popup opens by itself while a word is typed (the default) or only on Ctrl+Space. */
    public void setAutoActivation(boolean autoActivation) {
        this.autoActivation = autoActivation;
    }

    public int getAutoActivationLength() {
        return autoActivationLength;
    }

    /** How many chars of a word must be typed before the popup opens by itself; two by default. */
    public void setAutoActivationLength(int length) {
        autoActivationLength = Math.max(1, length);
    }

    // ---- Suggestions ----

    private CompletionRequest request() {
        return CompletionRequest.at(editor.getModel(), editor.getCaretPosition(), editor.getLanguage());
    }

    /** The part of a word just before the caret. */
    public String getPrefix() {
        return request().prefix();
    }

    /**
     * The suggestions for the word before the caret: those of every provider that match it,
     * without duplicates, best match first. Those of background providers are included once
     * they have arrived.
     */
    public List<Completion> getCompletions() {
        return suggest(request()).stream().map(Suggestion::completion).toList();
    }

    private List<Suggestion> suggest(CompletionRequest request) {
        List<Completion> offered = new ArrayList<>();
        // a provider that claims the request for itself keeps the others out
        List<CompletionProvider> exclusive = providers.stream().filter(provider -> provider.isExclusive(request)).toList();
        for (CompletionProvider provider : exclusive.isEmpty() ? providers : exclusive) {
            offered.addAll(provider.complete(request));
        }
        if (exclusive.isEmpty() && !asyncProviders.isEmpty()) {
            askInBackground(request);
            offered.addAll(asyncResults);
        }
        return rank(request.prefix(), offered);
    }

    private static List<Suggestion> rank(String prefix, List<Completion> offered) {
        Set<String> seen = new HashSet<>();
        Set<String> described = new HashSet<>();
        List<Suggestion> matching = new ArrayList<>();
        List<Integer> tiers = new ArrayList<>();
        for (Completion completion : offered) {
            String text = completion.text();
            // nothing is gained by offering exactly what is already there
            if (text.equals(prefix) && !completion.hasTemplate()) {
                continue;
            }
            FuzzyMatch.Result match = FuzzyMatch.match(prefix, text);
            if (match != null && seen.add(text + '\u0000' + completion.kind())) {
                matching.add(new Suggestion(completion, match.positions()));
                tiers.add(match.tier());
                if (completion.kind() != CompletionKind.WORD) {
                    described.add(text);
                }
            }
        }
        // The same text may be offered as different things, such as a keyword and a snippet.
        // A bare word from the document adds nothing to one that says what it is.
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < matching.size(); i++) {
            Completion completion = matching.get(i).completion();
            if (completion.kind() != CompletionKind.WORD || !described.contains(completion.text())) {
                order.add(i);
            }
        }
        // closest match first; among equals the shorter, then alphabetical
        order.sort(Comparator.comparingInt((Integer i) -> tiers.get(i))
                .thenComparingInt(i -> matching.get(i).completion().text().length())
                .thenComparing(i -> matching.get(i).completion().text().toLowerCase(Locale.ROOT))
                .thenComparing(i -> matching.get(i).completion().text())
                .thenComparing(i -> matching.get(i).completion().hasTemplate()));
        List<Suggestion> ranked = new ArrayList<>();
        for (int i = 0; i < order.size() && i < MAX_SUGGESTIONS; i++) {
            ranked.add(matching.get(order.get(i)));
        }
        return ranked;
    }

    // ---- Background providers ----

    /** Asks the background providers, unless what they answered for this word is still good. */
    private void askInBackground(CompletionRequest request) {
        if (isCurrent(request)) {
            return;
        }
        asyncContext = new AsyncContext(request.model(), request.prefixStart(), request.prefix());
        asyncResults = new ArrayList<>();
        asyncPending = asyncProviders.size();
        int generation = ++asyncGeneration;
        if (executor == null) {
            executor = Executors.newSingleThreadExecutor(task -> {
                Thread thread = new Thread(task, "code-completion");
                thread.setDaemon(true);
                return thread;
            });
        }
        for (CompletionProvider provider : asyncProviders) {
            executor.execute(() -> {
                List<Completion> answer;
                try {
                    answer = List.copyOf(provider.complete(request));
                } catch (RuntimeException e) {
                    answer = List.of();
                }
                List<Completion> results = answer;
                EventQueue.invokeLater(() -> arrived(generation, results));
            });
        }
    }

    /** Whether the answers held are for the word the request is about, typed further or not. */
    private boolean isCurrent(CompletionRequest request) {
        return asyncContext != null && asyncContext.model() == request.model()
                && asyncContext.prefixStart() == request.prefixStart()
                && request.prefix().startsWith(asyncContext.prefix());
    }

    private void arrived(int generation, List<Completion> results) {
        if (generation != asyncGeneration) {
            return; // an answer to a question no longer asked
        }
        asyncPending--;
        asyncResults.addAll(results);
        if (!results.isEmpty() && wanted && isCurrent(request())) {
            refresh();
        }
    }

    private void forgetAnswers() {
        asyncGeneration++;
        asyncContext = null;
        asyncResults = new ArrayList<>();
        asyncPending = 0;
    }

    /**
     * Replaces the word before the caret with a suggestion, as one undo step. A template is
     * inserted with its first tab stop selected; see {@link SnippetSession}.
     */
    public void accept(Completion completion) {
        int caret = editor.getCaretPosition();
        int start = caret - getPrefix().length();
        // close the popup first, so that it gives its keys back before a snippet takes them
        hide();
        accepting = true;
        try {
            if (completion.hasTemplate()) {
                // A snippet inside a snippet takes over; a plain word chosen while filling in a
                // tab stop leaves the snippet around it going.
                if (snippet != null) {
                    snippet.end();
                }
                snippet = SnippetSession.start(editor, start, caret, completion.template(), this::hide);
            } else {
                editor.replaceRange(start, caret, completion.text());
            }
        } finally {
            accepting = false;
        }
        remember();
    }

    // ---- The popup ----

    public boolean isShowing() {
        return window != null && window.isVisible();
    }

    /** Opens the popup for the word before the caret, even when nothing has been typed of it yet. */
    public void showCompletions() {
        explicit = true;
        refresh();
    }

    /** Closes the popup and stops waiting for suggestions still on their way. */
    public void hide() {
        explicit = false;
        wanted = false;
        forgetAnswers();
        closeWindow();
    }

    private void closeWindow() {
        if (isShowing()) {
            window.setVisible(false);
            keys.restore();
            Window owner = window.getOwner();
            if (owner != null) {
                owner.removeComponentListener(ownerListener);
            }
        }
    }

    private void caretMoved() {
        TextModel model = editor.getModel();
        int caret = editor.getCaretPosition();
        int length = model.length();
        boolean sameDocument = model == lastModel;
        // one char typed; with it may have come the partner of a bracket or quote, put after the caret
        boolean inserted = sameDocument && caret == lastCaret + 1 && (length == lastLength + 1 || length == lastLength + 2);
        boolean typed = inserted && CompletionProviders.isWordPart(model.charAt(caret - 1));
        boolean triggered = inserted && !typed && isTrigger(model.charAt(caret - 1));
        boolean deleted = sameDocument && length == lastLength - 1 && caret == lastCaret - 1;
        remember();
        // Edits a snippet makes to its linked tab stops are not the user moving the caret, and
        // text still being composed with an input method is not yet a word to complete.
        if (accepting || editor.isComposing() || (snippet != null && snippet.isUpdatingLinks())) {
            return;
        }
        if (triggered && autoActivation) {
            // a trigger char asks for suggestions before any letter of the word is typed
            explicit = true;
            refresh();
        } else if (isShowing() || wanted) {
            // typing narrows the list down; moving the caret any other way dismisses it
            if (typed || deleted) {
                refresh();
            } else {
                hide();
            }
        } else if (typed && autoActivation && getPrefix().length() >= autoActivationLength) {
            refresh();
        }
    }

    private boolean isTrigger(char c) {
        return providers.stream().anyMatch(provider -> provider.triggerCharacters().indexOf(c) >= 0)
                || asyncProviders.stream().anyMatch(provider -> provider.triggerCharacters().indexOf(c) >= 0);
    }

    private void remember() {
        lastModel = editor.getModel();
        lastLength = lastModel.length();
        lastCaret = editor.getCaretPosition();
    }

    private void refresh() {
        CompletionRequest request = request();
        boolean selection = editor.getSelectionStart() != editor.getSelectionEnd();
        if (!editor.isEditable() || selection || (request.prefix().isEmpty() && !explicit)) {
            hide();
            return;
        }
        wanted = true;
        List<Suggestion> found = suggest(request);
        if (found.isEmpty()) {
            if (asyncPending > 0) {
                closeWindow(); // nothing yet, but answers are on their way
            } else {
                hide();
            }
            return;
        }
        Completion selected = list.getSelectedValue() != null ? list.getSelectedValue().completion() : null;
        boolean showing = isShowing();
        suggestions.clear();
        suggestions.addAll(found);
        list.setVisibleRowCount(Math.min(VISIBLE_ROWS, found.size()));
        // late answers must not move the selection away from what the user is looking at
        int keep = 0;
        if (showing && selected != null) {
            for (int i = 0; i < found.size(); i++) {
                if (found.get(i).completion().equals(selected)) {
                    keep = i;
                }
            }
        }
        list.setSelectedIndex(keep);
        list.ensureIndexIsVisible(keep);
        prefixStart = request.prefixStart();
        show();
    }

    private void show() {
        Window owner = SwingUtilities.getWindowAncestor(editor);
        if (owner == null || !editor.isShowing()) {
            return;
        }
        if (window == null || window.getOwner() != owner) {
            if (window != null) {
                window.dispose();
            }
            window = new JWindow(owner);
            window.setFocusableWindowState(false); // the editor keeps the keyboard
            window.setType(Window.Type.POPUP);
            window.add(content);
        }
        list.setFont(editor.getFont());
        boolean opening = !window.isVisible();
        if (opening) {
            // the Look and Feel may have changed while the popup was closed
            SwingUtilities.updateComponentTreeUI(content);
            split.setBorder(null);
            listenToDivider();
            scrollPane.setBorder(BorderFactory.createEmptyBorder());
            content.setBorder(BorderFactory.createLineBorder(borderColor()));
            documentationScroll.setBorder(BorderFactory.createEmptyBorder());
            previewScroll.setBorder(BorderFactory.createEmptyBorder());
            documentation.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
            documentation.setBackground(list.getBackground());
            documentation.setForeground(list.getForeground());
        }
        position();
        if (opening && isCaretInView()) {
            window.setVisible(true);
            replaceKeys();
            owner.addComponentListener(ownerListener);
        }
    }

    private boolean isCaretInView() {
        return editor.getVisibleRect().intersects(editor.getOffsetBounds(prefixStart));
    }

    /** What is shown beside the list for a suggestion: its documentation, or else what a snippet inserts. */
    private static String documentationOf(Completion completion) {
        if (!completion.documentation().isEmpty()) {
            return completion.documentation();
        }
        if (completion.kind() == CompletionKind.SNIPPET && completion.hasTemplate()) {
            return completion.template().replaceAll("\\$\\{([^}]*)}", "$1").replace("$0", "").replace("\t", "    ");
        }
        return "";
    }

    /** Sizes the popup and puts it under the word being completed, or above it when there is no room below. */
    private void position() {
        if (!isCaretInView()) {
            hide();
            return;
        }
        Suggestion selected = list.getSelectedValue();
        String text = selected != null ? documentationOf(selected.completion()) : "";
        // what a snippet inserts is code; the rest is prose
        boolean code = !text.isEmpty() && selected.completion().documentation().isEmpty();
        side.setVisible(!text.isEmpty());
        split.setDividerSize(text.isEmpty() ? 0 : DIVIDER_SIZE);

        // A change in the number of rows shown does not make the list ask to be measured
        // again, so the scroll pane would answer with the size it had for the last list.
        list.invalidate();
        if (popupHeight > 0 && !suggestions.isEmpty()) {
            // as many rows as fit in the height the user chose, and no more than there are
            int rowHeight = Math.max(1, list.getCellBounds(0, 0).height);
            list.setVisibleRowCount(Math.max(1, Math.min(suggestions.size(), popupHeight / rowHeight)));
        }
        Dimension size = scrollPane.getPreferredSize();
        size.width = popupWidth > 0 ? Math.max(MIN_POPUP_WIDTH, popupWidth) : Math.max(240, Math.min(560, size.width + 24));
        int listWidth = size.width;
        size.height += 2 + grip.getPreferredSize().height;
        if (!text.isEmpty()) {
            int needed;
            if (code) {
                // as the editor shows code: its language, its colours and its font
                preview.setTheme(editor.getTheme());
                preview.setFont(editor.getFont());
                preview.setTabSize(editor.getTabSize());
                if (!text.equals(sideText) || preview.getLanguage() != editor.getLanguage()) {
                    preview.setDocument(ArrayTextModel.of(text), editor.getLanguage());
                }
                previewScroll.getViewport().setBackground(preview.getTheme().background());
                Dimension all = preview.getPreferredSize();
                // room for the bar below a line that is wider than the side
                needed = all.height + (all.width > documentationWidth ? previewScroll.getHorizontalScrollBar().getPreferredSize().height : 0);
            } else {
                documentation.setFont(UIManager.getFont("Label.font"));
                if (!text.equals(sideText)) {
                    documentation.setText(text);
                    documentation.setCaretPosition(0);
                }
                documentation.setSize(documentationWidth, Short.MAX_VALUE);
                needed = documentation.getPreferredSize().height;
            }
            sideText = text;
            sideCards.show(side, code ? "code" : "text");
            // The side is as tall as its text needs, within reason, even when the list beside it
            // is a single row: a snippet is several lines long. What is longer is scrolled.
            // The border of the popup and the bar it is resized with take their part of the height.
            int around = 2 + grip.getPreferredSize().height;
            size.height = Math.max(size.height, Math.min(needed, MAX_DOCUMENTATION_HEIGHT) + around);
            side.setPreferredSize(new Dimension(documentationWidth, size.height));
            size.width += DIVIDER_SIZE + documentationWidth;
        }
        Rectangle word = editor.getOffsetBounds(prefixStart);
        Point location = new Point(word.x, word.y + word.height);
        SwingUtilities.convertPointToScreen(location, editor);
        GraphicsConfiguration configuration = editor.getGraphicsConfiguration();
        if (configuration != null) {
            Rectangle screen = configuration.getBounds();
            // while its size is dragged the popup stays on the side of the word it is on
            if (!grip.isDragging()) {
                above = location.y + size.height > screen.y + screen.height;
            }
            location.x = Math.max(screen.x, Math.min(location.x, screen.x + screen.width - size.width));
        }
        if (above) {
            location.y -= word.height + size.height;
        }
        // the bar to drag is at the edge away from the word, which is the one that moves
        String edge = above ? BorderLayout.NORTH : BorderLayout.SOUTH;
        if (((BorderLayout) content.getLayout()).getLayoutComponent(edge) != grip) {
            content.add(grip, edge);
        }
        window.setBounds(location.x, location.y, size.width, size.height);
        // laid out at its new size first: a split pane that is resized moves its divider itself
        window.validate();
        split.setDividerLocation(listWidth);
        window.validate();
    }

    private void move(int rows) {
        int index = Math.max(0, Math.min(suggestions.size() - 1, list.getSelectedIndex() + rows));
        list.setSelectedIndex(index);
        list.ensureIndexIsVisible(index);
    }

    private void accept() {
        Suggestion selected = list.getSelectedValue();
        if (selected != null) {
            accept(selected.completion());
        } else {
            hide();
        }
    }

    // ---- Keys ----

    private void action(String name, Runnable action) {
        editor.getActionMap().put(name, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                action.run();
            }
        });
    }

    /** While the popup is open these keys work it instead of the editor. */
    private void replaceKeys() {
        replaceKey(KeyEvent.VK_UP, "completion-up");
        replaceKey(KeyEvent.VK_DOWN, "completion-down");
        replaceKey(KeyEvent.VK_PAGE_UP, "completion-page-up");
        replaceKey(KeyEvent.VK_PAGE_DOWN, "completion-page-down");
        replaceKey(KeyEvent.VK_ENTER, "completion-accept");
        replaceKey(KeyEvent.VK_TAB, "completion-accept");
        replaceKey(KeyEvent.VK_ESCAPE, "completion-hide");
    }

    private void replaceKey(int keyCode, String action) {
        keys.replace(KeyStroke.getKeyStroke(keyCode, 0), action);
    }

    // ---- Appearance ----

    private static Color borderColor() {
        Color color = UIManager.getColor("Component.borderColor");
        return color != null ? color : Color.GRAY;
    }

    /**
     * The bar at the edge of the popup that is dragged to resize it: up and down anywhere, and
     * at its end, where the lines are, to the side as well.
     */
    private final class ResizeGrip extends JComponent {

        private static final int HEIGHT = 7;
        private static final int CORNER = 18;

        /** Where the drag began on the screen, or null when there is none. */
        private Point pressed;
        private Dimension startSize;
        private boolean corner;

        ResizeGrip() {
            setPreferredSize(new Dimension(0, HEIGHT));
            MouseAdapter mouse = new MouseAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    setCursor(Cursor.getPredefinedCursor(cursorAt(e.getX())));
                }

                @Override
                public void mousePressed(MouseEvent e) {
                    if (SwingUtilities.isLeftMouseButton(e)) {
                        pressed = e.getLocationOnScreen();
                        startSize = scrollPane.getSize();
                        corner = isCorner(e.getX());
                    }
                }

                @Override
                public void mouseDragged(MouseEvent e) {
                    if (pressed == null) {
                        return;
                    }
                    Point now = e.getLocationOnScreen();
                    int dy = above ? pressed.y - now.y : now.y - pressed.y;
                    if (corner) {
                        popupWidth = Math.max(MIN_POPUP_WIDTH, startSize.width + now.x - pressed.x);
                    }
                    popupHeight = Math.max(1, startSize.height + dy);
                    position();
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    if (pressed != null) {
                        pressed = null;
                        firePopupSizeChanged();
                    }
                }
            };
            addMouseListener(mouse);
            addMouseMotionListener(mouse);
        }

        boolean isDragging() {
            return pressed != null;
        }

        private boolean isCorner(int x) {
            return x >= getWidth() - CORNER;
        }

        private int cursorAt(int x) {
            if (!isCorner(x)) {
                return above ? Cursor.N_RESIZE_CURSOR : Cursor.S_RESIZE_CURSOR;
            }
            return above ? Cursor.NE_RESIZE_CURSOR : Cursor.SE_RESIZE_CURSOR;
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setColor(list.getBackground());
            g.fillRect(0, 0, getWidth(), getHeight());
            // three short lines at the end, where the popup is also made wider
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(borderColor());
            int y = getHeight() / 2;
            for (int i = 0; i < 3; i++) {
                int x = getWidth() - 5 - i * 4;
                g.fillOval(x - 1, y - 1, 2, 2);
            }
            g.dispose();
        }
    }

    /**
     * The small rounded square with a letter that tells what kind of thing a suggestion is: the
     * letter in the colour of the kind on a light wash of it, and as large as the font asks.
     */
    private static final class KindIcon implements Icon {

        private CompletionKind kind = CompletionKind.OTHER;
        private Font font = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
        private Color selectedForeground;

        /** @param selectedForeground the colour of the text of the row when it is selected, else null */
        void set(CompletionKind kind, Font font, Color selectedForeground) {
            this.kind = kind;
            this.font = font;
            this.selectedForeground = selectedForeground;
        }

        @Override
        public void paintIcon(Component c, Graphics graphics, int x, int y) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            int size = getIconWidth();
            int arc = Math.round(size * 0.4f);
            Color color = kind.color();
            // on the colour of a selected row the wash is of the text of that row, to be seen there
            Color ink = selectedForeground != null ? selectedForeground : color;
            g.setColor(new Color(ink.getRed(), ink.getGreen(), ink.getBlue(), selectedForeground != null ? 50 : 44));
            g.fillRoundRect(x, y, size, size, arc, arc);
            g.setColor(ink);
            g.setFont(font.deriveFont(Font.BOLD, size * 0.68f));
            String letter = String.valueOf(kind.letter());
            java.awt.geom.Rectangle2D bounds = g.getFont().createGlyphVector(g.getFontRenderContext(), letter).getVisualBounds();
            // centred on the ink of the letter, not on its line: a capital has nothing below the baseline
            g.drawString(letter, (float) (x + (size - bounds.getWidth()) / 2 - bounds.getX()),
                    (float) (y + (size - bounds.getHeight()) / 2 - bounds.getY()));
            g.dispose();
        }

        @Override
        public int getIconWidth() {
            return Math.max(14, Math.round(font.getSize2D() * 1.3f));
        }

        @Override
        public int getIconHeight() {
            return getIconWidth();
        }
    }

    /** A row of the popup: icon, the suggestion with what was typed in bold, and a quieter note on the right. */
    private static final class Renderer extends JPanel implements ListCellRenderer<Suggestion> {

        private final KindIcon icon = new KindIcon();
        private final JLabel text = new JLabel();
        private final JLabel detail = new JLabel();

        Renderer() {
            super(new BorderLayout(16, 0));
            setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 8));
            text.setIcon(icon);
            text.setIconTextGap(8);
            add(text, BorderLayout.CENTER);
            add(detail, BorderLayout.EAST);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends Suggestion> list, Suggestion value, int index,
                                                      boolean selected, boolean focused) {
            Completion completion = value.completion();
            icon.set(completion.kind(), list.getFont(), selected ? list.getSelectionForeground() : null);
            text.setText(emphasized(completion.text(), value.matched()));
            detail.setText(completion.detail().isEmpty() ? completion.kind().label() : completion.detail());
            text.setFont(list.getFont());
            detail.setFont(list.getFont().deriveFont(list.getFont().getSize2D() - 1));
            setBackground(selected ? list.getSelectionBackground() : list.getBackground());
            Color foreground = selected ? list.getSelectionForeground() : list.getForeground();
            text.setForeground(foreground);
            Color quiet = UIManager.getColor("Label.disabledForeground");
            detail.setForeground(selected || quiet == null ? foreground : quiet);
            return this;
        }

        /** The text as HTML with the matched chars in bold. */
        private static String emphasized(String text, int[] matched) {
            StringBuilder html = new StringBuilder("<html><nobr>");
            int next = 0;
            for (int i = 0; i < text.length(); i++) {
                boolean bold = next < matched.length && matched[next] == i;
                if (bold) {
                    next++;
                    html.append("<b>");
                }
                char c = text.charAt(i);
                html.append(c == '<' ? "&lt;" : c == '>' ? "&gt;" : c == '&' ? "&amp;" : String.valueOf(c));
                if (bold) {
                    html.append("</b>");
                }
            }
            return html.append("</nobr></html>").toString();
        }
    }
}
