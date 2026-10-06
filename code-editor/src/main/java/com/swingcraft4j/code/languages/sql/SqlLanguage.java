package com.swingcraft4j.code.languages.sql;

import com.swingcraft4j.code.lexer.RuleLanguage;
import com.swingcraft4j.code.lexer.TokenType;

public final class SqlLanguage extends RuleLanguage {

    public SqlLanguage() {
        super(builder("sql", "SQL")
                .extensions("sql", "ddl", "dml")
                .ignoreCase()
                .lineComment("--")
                .blockComment("/*", "*/")
                // a quote is escaped by doubling it, which reads as two adjacent strings
                .region(TokenType.STRING, "'", "'", NO_ESCAPE, true)
                .region(TokenType.IDENTIFIER, "\"", "\"", NO_ESCAPE, false)
                .region(TokenType.IDENTIFIER, "`", "`", NO_ESCAPE, false)
                .pattern(TokenType.CONSTANT, "[:@$][A-Za-z_]\\w*")
                .keywords("add", "all", "alter", "and", "any", "as", "asc", "begin", "between", "by", "cascade",
                        "case", "check", "column", "commit", "constraint", "create", "cross", "database", "declare",
                        "default", "delete", "desc", "distinct", "drop", "else", "end", "except", "exists", "fetch",
                        "for", "foreign", "from", "full", "function", "grant", "group", "having", "if", "in",
                        "index", "inner", "insert", "intersect", "into", "is", "join", "key", "left", "like",
                        "limit", "not", "offset", "on", "or", "order", "outer", "over", "partition", "primary",
                        "procedure", "references", "return", "returns", "revoke", "right", "rollback", "row",
                        "rows", "schema", "select", "set", "table", "then", "to", "top", "trigger", "truncate",
                        "union", "unique", "update", "use", "using", "values", "view", "when", "where", "with")
                .literals("true", "false", "null", "unknown")
                .types("bigint", "binary", "bit", "blob", "boolean", "char", "clob", "date", "datetime", "decimal",
                        "double", "float", "int", "integer", "json", "nchar", "numeric", "nvarchar", "real",
                        "serial", "smallint", "text", "time", "timestamp", "tinyint", "uuid", "varbinary", "varchar")
                .detectFunctions());
    }
}
