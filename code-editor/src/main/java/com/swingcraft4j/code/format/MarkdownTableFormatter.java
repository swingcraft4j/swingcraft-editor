package com.swingcraft4j.code.format;

import com.swingcraft4j.code.layout.Cells;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Aligns the columns of the tables in Markdown, and leaves the rest of the text as it is. Each
 * column gets the width of its widest cell, measured in cells of a monospaced font, so that a
 * table with Chinese or emoji in it lines up too. The row under the header keeps what it says
 * about alignment, and the cells of a column are padded on the side that goes with it.
 * <p>
 * A table is a line with a pipe in it followed by a row of dashes with as many cells, and the
 * lines after those up to the first one that is blank or has no pipe. A pipe after a backslash
 * or inside a code span is not the end of a cell. Tables inside fenced code blocks are not
 * touched.
 */
public final class MarkdownTableFormatter implements Formatter {

    private static final Pattern FENCE = Pattern.compile("[ \\t]*(`{3,}|~{3,})(.*)");
    private static final Pattern DELIMITER_CELL = Pattern.compile(":?-+:?");
    private static final int MIN_WIDTH = 3;

    private enum Align {
        NONE, LEFT, CENTER, RIGHT
    }

    @Override
    public String format(String text, FormatOptions options) {
        List<String> lines = new ArrayList<>();
        List<String> terminators = new ArrayList<>();
        TextLines.split(text, lines, terminators);

        StringBuilder out = new StringBuilder(text.length() + 64);
        String fence = null; // the run of chars that opened the fenced block the line is in
        int i = 0;
        while (i < lines.size()) {
            String line = lines.get(i);
            Matcher matcher = FENCE.matcher(line);
            if (fence != null) {
                if (matcher.matches() && matcher.group(1).startsWith(fence) && matcher.group(2).isBlank()) {
                    fence = null;
                }
            } else if (matcher.matches() && !(matcher.group(1).charAt(0) == '`' && matcher.group(2).contains("`"))) {
                fence = matcher.group(1);
            } else if (i + 1 < lines.size() && startsTable(line, lines.get(i + 1))) {
                int end = i + 2;
                while (end < lines.size() && isRow(lines.get(end))) {
                    end++;
                }
                List<String> table = table(lines.subList(i, end), options.tabSize());
                for (int row = 0; row < table.size(); row++) {
                    out.append(table.get(row)).append(terminators.get(i + row));
                }
                i = end;
                continue;
            }
            out.append(line).append(terminators.get(i));
            i++;
        }
        String formatted = out.toString();
        return formatted.equals(text) ? text : formatted;
    }

    private static boolean startsTable(String header, String delimiter) {
        List<String> names = cells(header);
        List<String> dashes = cells(delimiter);
        if (names == null || dashes == null || names.size() != dashes.size()) {
            return false;
        }
        return dashes.stream().allMatch(cell -> DELIMITER_CELL.matcher(cell).matches());
    }

    private static boolean isRow(String line) {
        return !line.isBlank() && !FENCE.matcher(line).matches() && cells(line) != null;
    }

    /**
     * The cells of a row, without the white space around each, or null for a line that has no
     * pipe to divide it. A pipe at the very start or end of the row is its border, not a cell.
     */
    private static List<String> cells(String line) {
        String row = line.strip();
        List<String> cells = new ArrayList<>();
        int start = 0;
        int i = 0;
        while (i < row.length()) {
            char c = row.charAt(i);
            if (c == '\\' && i + 1 < row.length()) {
                i += 2; // an escaped char, a pipe among them, is text
            } else if (c == '`') {
                int run = backticks(row, i);
                int close = closingBackticks(row, i + run, run);
                i = close < 0 ? i + run : close + run; // a code span, with whatever pipes are inside it
            } else {
                if (c == '|') {
                    cells.add(row.substring(start, i).strip());
                    start = i + 1;
                }
                i++;
            }
        }
        if (cells.isEmpty()) {
            return null;
        }
        if (start < row.length()) {
            cells.add(row.substring(start).strip()); // no border at the end
        }
        if (row.charAt(0) == '|') {
            cells.remove(0);
        }
        return cells.isEmpty() ? null : cells;
    }

    private static int backticks(String row, int start) {
        int end = start;
        while (end < row.length() && row.charAt(end) == '`') {
            end++;
        }
        return end - start;
    }

    /** The index of the next run of exactly {@code length} backticks, or -1 if there is none. */
    private static int closingBackticks(String row, int from, int length) {
        int i = from;
        while (i < row.length()) {
            if (row.charAt(i) == '`') {
                int run = backticks(row, i);
                if (run == length) {
                    return i;
                }
                i += run;
            } else {
                i++;
            }
        }
        return -1;
    }

    /** The lines of a table, the header first and the row of dashes second, with the columns aligned. */
    private static List<String> table(List<String> lines, int tabSize) {
        String header = lines.get(0);
        String indent = header.substring(0, header.length() - header.stripLeading().length());
        List<List<String>> rows = new ArrayList<>();
        for (String line : lines) {
            rows.add(cells(line));
        }
        int columns = rows.get(0).size();
        Align[] aligns = new Align[columns];
        int[] widths = new int[columns];
        for (int column = 0; column < columns; column++) {
            String dashes = rows.get(1).get(column);
            boolean left = dashes.startsWith(":");
            boolean right = dashes.endsWith(":");
            aligns[column] = left && right ? Align.CENTER : left ? Align.LEFT : right ? Align.RIGHT : Align.NONE;
            widths[column] = MIN_WIDTH;
        }
        for (int row = 0; row < rows.size(); row++) {
            List<String> cells = rows.get(row);
            for (int column = 0; row != 1 && column < Math.min(columns, cells.size()); column++) {
                widths[column] = Math.max(widths[column], width(cells.get(column), tabSize));
            }
        }

        List<String> table = new ArrayList<>();
        for (int row = 0; row < rows.size(); row++) {
            List<String> cells = rows.get(row);
            StringBuilder line = new StringBuilder(indent).append('|');
            // a row with cells missing gets them, empty; one with too many keeps them, unpadded
            for (int column = 0; column < Math.max(columns, cells.size()); column++) {
                String cell = column < cells.size() ? cells.get(column) : "";
                line.append(' ');
                if (column >= columns) {
                    line.append(cell);
                } else if (row == 1) {
                    line.append(dashes(aligns[column], widths[column]));
                } else {
                    int room = widths[column] - width(cell, tabSize);
                    int before = aligns[column] == Align.RIGHT ? room : aligns[column] == Align.CENTER ? room / 2 : 0;
                    line.append(" ".repeat(before)).append(cell).append(" ".repeat(room - before));
                }
                line.append(" |");
            }
            table.add(line.toString());
        }
        return table;
    }

    private static String dashes(Align align, int width) {
        switch (align) {
            case LEFT:
                return ":" + "-".repeat(width - 1);
            case RIGHT:
                return "-".repeat(width - 1) + ":";
            case CENTER:
                return ":" + "-".repeat(width - 2) + ":";
            default:
                return "-".repeat(width);
        }
    }

    private static int width(String cell, int tabSize) {
        return Cells.count(cell.toCharArray(), 0, cell.length(), tabSize);
    }
}
