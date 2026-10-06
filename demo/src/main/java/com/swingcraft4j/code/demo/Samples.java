package com.swingcraft4j.code.demo;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** The sample sources bundled with the demos, one per language id. */
final class Samples {

    private Samples() {
    }

    static String read(String languageId) throws IOException {
        String resource = "samples/" + languageId + ".txt";
        try (InputStream in = Samples.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IOException("There is no sample for this language: " + resource);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** Java source of about the given number of lines, for trying out large documents. */
    static CharSequence generateJava(int lines) {
        StringBuilder text = new StringBuilder(lines * 40);
        text.append("package generated;\n\n/**\n * A generated class used to test large files.\n */\npublic class Generated {\n");
        int written = 6;
        for (int i = 0; written < lines - 1; i++) {
            text.append("\n    /** Returns a value derived from {@code input} for case ").append(i).append(". */\n")
                    .append("    public static int compute").append(i).append("(int input) {\n")
                    .append("        String label = \"case #").append(i).append("\";\n")
                    .append("        return input * ").append(i).append(" + label.length(); // inline comment\n")
                    .append("    }\n");
            written += 6;
        }
        text.append("}\n");
        return text;
    }
}
