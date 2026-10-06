package com.swingcraft4j.code.search;

import com.swingcraft4j.code.search.TextSearch.Match;
import com.swingcraft4j.code.text.ArrayTextModel;
import com.swingcraft4j.code.text.TextModel;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.PatternSyntaxException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TextSearchTest {

    private final TextModel model = ArrayTextModel.of("int count = 1;\r\nCount++;\nrecount(count);");

    private List<String> all(TextSearch search) {
        List<String> found = new ArrayList<>();
        for (Match match = search.findNext(model, 0); match != null; match = search.findNext(model, match.end())) {
            found.add(match.start() + ":" + model.getText(match.start(), match.end()));
        }
        return found;
    }

    @Test
    void findsLiteralTextIgnoringCaseByDefault() {
        assertEquals(List.of("4:count", "16:Count", "27:count", "33:count"),
                all(new TextSearch("count", false, false, false)));
    }

    @Test
    void honoursMatchCaseAndWholeWord() {
        assertEquals(List.of("4:count", "27:count", "33:count"), all(new TextSearch("count", true, false, false)));
        assertEquals(List.of("4:count", "33:count"), all(new TextSearch("count", true, true, false)));
    }

    @Test
    void wholeWordWorksForQueriesThatAreNotWords() {
        assertEquals(List.of("21:++"), all(new TextSearch("++", false, true, false)));
        assertEquals(List.of("32:(count)"), all(new TextSearch("(count)", false, true, false)));
    }

    @Test
    void treatsRegexQueriesPerLine() {
        assertEquals(List.of("0:int", "16:Count", "25:recount"), all(new TextSearch("^\\w+", true, false, true)));
        assertEquals(List.of("13:;", "23:;", "39:;"), all(new TextSearch(";$", true, false, true)));
        // a pattern that can match nothing must not loop or report empty matches
        assertEquals(List.of("12:1"), all(new TextSearch("\\d*", true, false, true)));
        assertThrows(PatternSyntaxException.class, () -> new TextSearch("(", false, false, true));
    }

    @Test
    void searchesBackwards() {
        TextSearch search = new TextSearch("count", false, false, false);
        assertEquals(new Match(33, 38), search.findPrevious(model, model.length()));
        assertEquals(new Match(27, 32), search.findPrevious(model, 33));
        assertEquals(new Match(27, 32), search.findPrevious(model, 37));
        assertEquals(new Match(4, 9), search.findPrevious(model, 16));
        assertNull(search.findPrevious(model, 8));
        assertNull(search.findNext(model, 34));
    }

    @Test
    void reportsMatchesWithinALine() {
        List<String> found = new ArrayList<>();
        char[] text = "a-b-c???".toCharArray();
        new TextSearch("-", false, false, false).findInLine(text, 5, (start, end) -> found.add(start + "-" + end));
        assertEquals(List.of("1-2", "3-4"), found);
    }
}
