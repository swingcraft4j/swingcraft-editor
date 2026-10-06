package com.swingcraft4j.code.text;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ArrayTextModelTest {

    private static String line(TextModel model, int line) {
        return model.getText(model.lineStart(line), model.lineEnd(line));
    }

    @Test
    void emptyTextIsOneEmptyLine() {
        TextModel model = ArrayTextModel.of("");
        assertEquals(1, model.lineCount());
        assertEquals(0, model.lineLength(0));
    }

    @Test
    void splitsOnEveryKindOfTerminator() {
        TextModel model = ArrayTextModel.of("one\ntwo\r\nthree\rfour\n");
        assertEquals(5, model.lineCount());
        assertEquals("one", line(model, 0));
        assertEquals("two", line(model, 1));
        assertEquals("three", line(model, 2));
        assertEquals("four", line(model, 3));
        assertEquals("", line(model, 4));
    }

    @Test
    void keepsEmptyLinesBetweenTerminators() {
        TextModel model = ArrayTextModel.of("a\n\n\r\nb");
        assertEquals(4, model.lineCount());
        assertEquals("", line(model, 1));
        assertEquals("", line(model, 2));
        assertEquals("b", line(model, 3));
    }

    @Test
    void findsTheLineOfAnOffset() {
        TextModel model = ArrayTextModel.of("ab\r\ncd\nef");
        assertEquals(0, model.lineOfOffset(0));
        assertEquals(0, model.lineOfOffset(3)); // inside the \r\n terminator
        assertEquals(1, model.lineOfOffset(4));
        assertEquals(2, model.lineOfOffset(model.length()));
    }

    @Test
    void storesCharsOutsideLatin1() {
        String text = "café\n你好 😀";
        TextModel model = ArrayTextModel.of(text);
        assertEquals(text, model.getText(0, model.length()));
        assertEquals("café", ArrayTextModel.of("café").getText(0, 4));
        assertEquals('好', model.charAt(6));
    }
}
