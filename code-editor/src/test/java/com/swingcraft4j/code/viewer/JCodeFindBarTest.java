package com.swingcraft4j.code.viewer;

import org.junit.jupiter.api.Test;

import javax.swing.AbstractButton;
import java.awt.Dimension;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JCodeFindBarTest {

    private final JCodeViewer viewer = new JCodeViewer("one two\nTwo three two\nfour");
    private final JCodeFindBar bar = new JCodeFindBar(viewer);

    private String status() {
        return bar.getStatusLabel().getText();
    }

    @Test
    void saysWhichOfHowManyMatchesIsSelected() {
        bar.open();
        bar.getSearchField().setText("two");
        assertEquals("1 of 3", status());
        assertEquals("two", viewer.getSelectedText());
        bar.getNextButton().doClick();
        assertEquals("2 of 3", status());
        bar.getNextButton().doClick();
        bar.getNextButton().doClick();
        assertEquals("1 of 3", status(), "after the last match comes the first again");
        bar.getPreviousButton().doClick();
        assertEquals("3 of 3", status());

        bar.getMatchCaseButton().doClick();
        assertEquals("2 of 2", status(), "the selected match is still one, and is counted among those left");
    }

    @Test
    void saysWhenThereIsNoMatchOrNoValidExpression() {
        bar.open();
        bar.getSearchField().setText("five");
        assertEquals("No matches", status());
        assertEquals("error", bar.getSearchField().getClientProperty("JComponent.outline"));
        bar.getRegexButton().doClick();
        bar.getSearchField().setText("t(wo");
        assertEquals("Invalid expression", status());
        bar.getSearchField().setText("t(w|h)");
        assertEquals("1 of 4", status());
        assertNull(bar.getSearchField().getClientProperty("JComponent.outline"));
        bar.getSearchField().setText("");
        assertEquals(" ", status());
    }

    @Test
    void closingClearsTheSearchOfTheViewer() {
        bar.open();
        bar.getSearchField().setText("two");
        assertTrue(bar.isVisible());
        bar.getCloseButton().doClick();
        assertFalse(bar.isVisible());
        assertNull(viewer.getSearch());
    }

    @Test
    void givesItsButtonsOneHeightAndKeepsASizeThatWasSet() {
        bar.getRegexButton().setPreferredSize(new Dimension(40, 11));
        bar.getPreferredSize();
        List<AbstractButton> sized = List.of(bar.getMatchCaseButton(), bar.getWholeWordButton(), bar.getPreviousButton(),
                bar.getNextButton(), bar.getCloseButton());
        int height = sized.get(0).getPreferredSize().height;
        for (AbstractButton button : sized) {
            Dimension size = button.getPreferredSize();
            if (button.getIcon() == null) {
                assertEquals(height, size.height);
            } else {
                assertTrue(size.height == height || size.height == height + 1, "at most a pixel more");
                assertEquals(size.height, size.width, "a button with an icon is as wide as it is high");
                assertEquals(0, (size.width - button.getIcon().getIconWidth()) % 2, "the icon can stand in its middle");
            }
        }
        assertEquals(new Dimension(40, 11), bar.getRegexButton().getPreferredSize());
    }
}
