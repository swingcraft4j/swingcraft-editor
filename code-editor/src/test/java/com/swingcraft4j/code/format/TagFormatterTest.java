package com.swingcraft4j.code.format;

import com.swingcraft4j.code.lexer.Languages;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class TagFormatterTest {

    private static final FormatOptions OPTIONS = new FormatOptions(2, false);

    private static String format(Formatter formatter, String text) {
        String formatted = formatter.format(text, OPTIONS);
        assertSame(formatted, formatter.format(formatted, OPTIONS), "formatting twice is formatting once");
        return formatted;
    }

    private static String xml(String text) {
        return format(TagFormatter.xml(), text);
    }

    private static String html(String text) {
        return format(TagFormatter.html(), text);
    }

    @Test
    void putsEachElementOnALineOfItsOwn() {
        String formatted = "<?xml version=\"1.0\"?>\n<order id=\"7\">\n  <item>\n    <name>Tea</name>\n    <qty>2</qty>\n  </item>\n  <note/>\n</order>";
        assertEquals(formatted, xml("<?xml version=\"1.0\"?><order id=\"7\"><item><name>Tea</name><qty>2</qty></item><note/></order>"));
        assertEquals(formatted, xml("<?xml version=\"1.0\"?>\n   <order id=\"7\">\n<item><name>Tea</name>\n      <qty>2</qty>\n</item>   <note/>\n      </order>"),
                "however it was laid out before");
        assertEquals("<a>\r\n    <b/>\r\n</a>\r\n", TagFormatter.xml().format("<a><b/></a>\r\n", new FormatOptions(4, false, "\r\n")), "a line break at the end is kept");
        assertEquals("<a>\r\n\t<b/>\r\n</a>", TagFormatter.xml().format("<a><b/></a>", new FormatOptions(4, true, "\r\n")));
    }

    @Test
    void leavesAnElementWithTextAsItWasWritten() {
        assertEquals("<p>\n  <line>Hello <b>big</b> world</line>\n  <line> spaces  stay </line>\n</p>",
                xml("<p><line>Hello <b>big</b> world</line><line> spaces  stay </line></p>"));
        assertEquals("<a>\n  <b></b>\n  <c>  </c>\n  <d><![CDATA[<x>]]></d>\n</a>", xml("<a><b></b><c>  </c><d><![CDATA[<x>]]></d></a>"),
                "and one with nothing in it, or only white space, or a section of chars");
        String mixed = "text <a><b/></a>";
        assertSame(mixed, xml(mixed), "a text with text outside every element is not formatted");
    }

    @Test
    void keepsCommentsDeclarationsAndOneBlankLine() {
        assertEquals("<!DOCTYPE a [\n  <!ENTITY x \"y > z\">\n]>\n<!-- one\n     two -->\n<a b=\"x > y\">\n  <!-- in -->\n  <b/>\n\n  <c/>\n</a>",
                xml("<!DOCTYPE a [\n  <!ENTITY x \"y > z\">\n]><!-- one\n     two --><a b=\"x > y\"><!-- in --><b/>\n\n\n\n<c/></a>"));
    }

    @Test
    void leavesTextThatItCannotMakeSenseOfUnchanged() {
        for (String broken : new String[]{"<a><b></a>", "<a>", "</a>", "<a><b/>", "<a b=\"x>", "<!-- open <a/>", "<a></A>", ""}) {
            assertSame(broken, xml(broken));
        }
        // some lines from the middle of a document, as a selection gives them
        String lines = "<b/>\n</a>";
        assertSame(lines, xml(lines));
    }

    @Test
    void knowsTheElementsOfHtml() {
        assertEquals("<!DOCTYPE html>\n<html>\n  <head>\n    <meta charset=\"utf-8\">\n    <title>T</title>\n    <style>\na{b:c}\n</style>\n  </head>\n  <BODY>\n    <ul>\n      <li>one</li>\n      <li><b>two</b><i>three</i></li>\n    </ul>\n    <hr>\n    <pre>  <b>as\n   it is</b></pre>\n    <script>if (a<b) { c() }</script>\n  </Body>\n</html>",
                html("<!DOCTYPE html><html><head><meta charset=\"utf-8\"><title>T</title><style>\na{b:c}\n</style></head><BODY><ul><li>one</li><li><b>two</b><i>three</i></li></ul><hr><pre>  <b>as\n   it is</b></pre><script>if (a<b) { c() }</script></Body></html>"));
        String phrasing = "<div><span>a</span><span>b</span></div>";
        assertEquals("<section>\n  " + phrasing + "\n</section>", html("<section>" + phrasing + "</section>"),
                "a line break between two elements in a line of text would show as a space");
        String unclosed = "<ul><li>one<li>two</ul>";
        assertSame(unclosed, html(unclosed), "an element without its end tag is not for the formatter to close");
    }

    @Test
    void isTheFormatterOfXmlAndHtml() {
        assertEquals("<a>\n  <b/>\n</a>", Languages.byId("xml").orElseThrow().formatter().format("<a><b/></a>", OPTIONS));
        assertEquals("<div>\n  <hr>\n  <p>x</p>\n</div>", Languages.byId("html").orElseThrow().formatter().format("<div><hr><p>x</p></div>", OPTIONS));
    }
}
