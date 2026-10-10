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
}
