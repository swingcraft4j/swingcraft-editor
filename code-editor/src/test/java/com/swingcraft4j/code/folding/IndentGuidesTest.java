package com.swingcraft4j.code.folding;

import com.swingcraft4j.code.text.ArrayTextModel;
import com.swingcraft4j.code.text.TextModel;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IndentGuidesTest {

    /** The columns of the guides of each line from the given one on, a list for each line. */
    private static List<List<Integer>> guides(int firstLine, String... lines) {
        TextModel model = ArrayTextModel.of(String.join("\n", lines));
        IndentGuides guides = new IndentGuides(model, 4);
        guides.startAt(firstLine);
        List<List<Integer>> result = new ArrayList<>();
        for (int line = firstLine; line < lines.length; line++) {
            List<Integer> columns = new ArrayList<>();
            int count = guides.next(line);
            for (int i = 0; i < count; i++) {
                columns.add(guides.column(i));
            }
            result.add(columns);
        }
        return result;
    }

    @Test
    void joinsAnOpeningBracketToItsClosingOne() {
        assertEquals(List.of(List.of(), List.of(0), List.of(0, 4), List.of(0, 4), List.of(0), List.of()),
                guides(0,
                        "{",
                        "    \"k\":[",
                        "",
                        "        {\"k\":\"v\"}",
                        "    ]",
                        "}"),
                "a guide at the column of each line a line is indented under, and none broken by a blank line");
    }

    @Test
    void aBlankLineHasTheGuidesOfTheDeeperOfTheLinesAroundIt() {
        assertEquals(List.of(List.of(), List.of(0), List.of(0, 2), List.of(0, 2), List.of(0), List.of(0), List.of()),
                guides(0,
                        "a {",
                        "  b {",
                        "    c",
                        "",
                        "  }",
                        "",
                        "}"),
                "it is inside every block that has not ended yet, also right before a closing bracket");
    }

    @Test
    void goesByTheColumnsOfTheLinesAboveWhateverTheirWidth() {
        assertEquals(List.of(List.of(), List.of(0), List.of(0, 3), List.of(0), List.of(0, 1)),
                guides(0,
                        "a",
                        "   b",
                        "\tc",
                        " d",
                        "    e"),
                "a tab reaches to the next tab stop, and a line indented less ends the guides of those above");
    }

    @Test
    void startsInTheMiddleOfADocument() {
        String[] lines = {"a {", "    b {", "        c", "", "        d", "    }", "}"};
        assertEquals(guides(0, lines).subList(4, 7), guides(4, lines), "as if the lines above had been asked about");
        assertEquals(guides(0, lines).subList(3, 7), guides(3, lines), "also from a blank line");
    }

    @Test
    void tellsTheLineEachGuideStartsUnder() {
        TextModel model = ArrayTextModel.of(String.join("\n", "a {", "    b {", "        c", "", "    }", "}"));
        IndentGuides guides = new IndentGuides(model, 4);
        guides.startAt(2);
        assertEquals(2, guides.next(2));
        assertEquals(0, guides.line(0));
        assertEquals(1, guides.line(1));
        assertEquals(2, guides.next(3), "a blank line");
        assertEquals(1, guides.line(1));
        assertEquals(1, guides.next(4));
        assertEquals(0, guides.line(0));
    }

    @Test
    void findsTheBlockALineIsIn() {
        TextModel model = ArrayTextModel.of(String.join("\n",
                "a {",          // 0
                "    b {",      // 1
                "        c",    // 2
                "",             // 3
                "        d",    // 4
                "    }",        // 5
                "    e",        // 6
                "",             // 7
                "}",            // 8
                "f"));          // 9
        assertEquals(0, IndentGuides.blockStart(model, 0, 4), "a line that starts a block is in it");
        assertEquals(1, IndentGuides.blockStart(model, 1, 4));
        assertEquals(1, IndentGuides.blockStart(model, 2, 4));
        assertEquals(1, IndentGuides.blockStart(model, 3, 4), "a blank line between the lines of a block");
        assertEquals(1, IndentGuides.blockStart(model, 4, 4));
        assertEquals(1, IndentGuides.blockStart(model, 5, 4), "the line that ends a block is in it");
        assertEquals(0, IndentGuides.blockStart(model, 6, 4), "a line after a block that has ended is in the one around it");
        assertEquals(0, IndentGuides.blockStart(model, 7, 4));
        assertEquals(0, IndentGuides.blockStart(model, 8, 4));
        assertEquals(-1, IndentGuides.blockStart(model, 9, 4), "at the top level there is none");
    }
}
