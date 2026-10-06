package com.swingcraft4j.code.autocomplete;

import java.util.List;

import static com.swingcraft4j.code.autocomplete.Completion.snippet;

/** The snippets that come with the library, by language id. */
public final class Snippets {

    private static final List<Completion> JAVA = List.of(
            snippet("for", "for (int ${i} = 0; ${i} < ${count}; ${i}++) {\n\t$0\n}"),
            snippet("foreach", "for (${Type} ${item} : ${items}) {\n\t$0\n}"),
            snippet("while", "while (${condition}) {\n\t$0\n}"),
            snippet("if", "if (${condition}) {\n\t$0\n}"),
            snippet("ifelse", "if (${condition}) {\n\t$0\n} else {\n\t\n}"),
            snippet("try", "try {\n\t$0\n} catch (${Exception} ${e}) {\n\t\n}"),
            snippet("switch", "switch (${value}) {\n\tcase ${first} -> $0;\n\tdefault -> {\n\t}\n}"),
            snippet("sout", "System.out.println(${message});"),
            snippet("main", "public static void main(String[] args) {\n\t$0\n}"),
            snippet("class", "public class ${Name} {\n\t$0\n}"),
            snippet("method", "public ${void} ${name}(${parameters}) {\n\t$0\n}"));

    private static final List<Completion> JAVASCRIPT = List.of(
            snippet("for", "for (let ${i} = 0; ${i} < ${count}; ${i}++) {\n\t$0\n}"),
            snippet("forof", "for (const ${item} of ${items}) {\n\t$0\n}"),
            snippet("while", "while (${condition}) {\n\t$0\n}"),
            snippet("if", "if (${condition}) {\n\t$0\n}"),
            snippet("ifelse", "if (${condition}) {\n\t$0\n} else {\n\t\n}"),
            snippet("try", "try {\n\t$0\n} catch (${error}) {\n\t\n}"),
            snippet("function", "function ${name}(${parameters}) {\n\t$0\n}"),
            snippet("arrow", "const ${name} = (${parameters}) => {\n\t$0\n};"),
            snippet("class", "class ${Name} {\n\tconstructor(${parameters}) {\n\t\t$0\n\t}\n}"),
            snippet("log", "console.log(${message});"));

    private static final List<Completion> PYTHON = List.of(
            snippet("for", "for ${item} in ${items}:\n\t$0"),
            snippet("while", "while ${condition}:\n\t$0"),
            snippet("if", "if ${condition}:\n\t$0"),
            snippet("ifelse", "if ${condition}:\n\t$0\nelse:\n\tpass"),
            snippet("try", "try:\n\t$0\nexcept ${Exception} as ${error}:\n\tpass"),
            snippet("def", "def ${name}(${parameters}):\n\t$0"),
            snippet("class", "class ${Name}:\n\tdef __init__(self, ${parameters}):\n\t\t$0"),
            snippet("with", "with ${expression} as ${name}:\n\t$0"),
            snippet("main", "if __name__ == '__main__':\n\t$0"));

    private static final List<Completion> SQL = List.of(
            snippet("select", "SELECT ${columns}\nFROM ${table}\nWHERE ${condition};"),
            snippet("insert", "INSERT INTO ${table} (${columns})\nVALUES (${values});"),
            snippet("update", "UPDATE ${table}\nSET ${column} = ${value}\nWHERE ${condition};"),
            snippet("delete", "DELETE FROM ${table}\nWHERE ${condition};"),
            snippet("create", "CREATE TABLE ${name} (\n\t${id} INT PRIMARY KEY,\n\t$0\n);"),
            snippet("join", "INNER JOIN ${table} ON ${left} = ${right}"));

    private Snippets() {
    }

    /** The snippets for a language id such as {@code "java"}; none for a language without any. */
    public static List<Completion> forLanguage(String languageId) {
        return switch (languageId) {
            case "java" -> JAVA;
            case "javascript", "typescript" -> JAVASCRIPT;
            case "python" -> PYTHON;
            case "sql" -> SQL;
            default -> List.of();
        };
    }
}
