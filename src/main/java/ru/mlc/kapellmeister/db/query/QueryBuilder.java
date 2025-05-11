package ru.mlc.kapellmeister.db.query;

import ru.mlc.kapellmeister.db.tables.Column;
import ru.mlc.kapellmeister.db.tables.OperationTable;

import java.util.Arrays;
import java.util.Collections;
import java.util.stream.Collectors;

public class QueryBuilder {

    private final StringBuilder query;

    public QueryBuilder() {
        query = new StringBuilder();
    }

    public QueryBuilder select(String fields) {
        query.append(String.format("SELECT %s", fields));
        return this;
    }

    public QueryBuilder selectDistinct(String fields) {
        query.append(String.format("SELECT DISTINCT %s", fields));
        return this;
    }

    public QueryBuilder update(String tableName) {
        query.append(String.format("UPDATE %s SET ", tableName));
        return this;
    }

    public QueryBuilder paramsForUpdate(Column<?>... columns) {
        query.append(Arrays.stream(columns)
                .map(column -> String.format("%s = ?", column.name()))
                .collect(Collectors.joining(",")));
        return this;
    }

    public QueryBuilder insert(String tableName, Column<?>... columns) {
        String insertColumns = Arrays.stream(columns).map(Column::name).collect(Collectors.joining(","));
        String questionMarks = String.join(",", Collections.nCopies(columns.length, "?"));
        query.append(String.format("INSERT INTO %s (%s) VALUES (%s)", tableName, insertColumns, questionMarks));
        return this;
    }

    public QueryBuilder from(String alias, String table) {
        query.append(String.format(" FROM %s %s", table, alias));
        return this;
    }

    public QueryBuilder from(String table) {
        query.append(String.format(" FROM %s", table));
        return this;
    }

    public QueryBuilder where() {
        query.append(" WHERE ");
        return this;
    }

    public QueryBuilder and() {
        query.append(" AND ");
        return this;
    }

    public QueryBuilder or() {
        query.append(" OR ");
        return this;
    }

    public QueryBuilder inNamed(String alias, Column<?> column, String paramName) {
        query.append(String.format("%s IN (:%s)", joinAliasAndColumn(alias, column), paramName));
        return this;
    }

    public QueryBuilder inNamed(Column<?> column, String paramName) {
        query.append(String.format("%s IN (:%s)", column.name(), paramName));
        return this;
    }

    public QueryBuilder whereParam(String alias, Column<?> column, String operator) {
        query.append(String.format("%s %s ?", joinAliasAndColumn(alias, column), operator));
        return this;
    }

    public QueryBuilder whereNamedParam(String alias, Column<?> column, String operator, String paramName) {
        query.append(String.format("%s %s (:%s)", joinAliasAndColumn(alias, column), operator, paramName));
        return this;
    }

    public QueryBuilder whereParam(Column<?> column, String operator) {
        query.append(String.format("%s %s ?", column.name(), operator));
        return this;
    }

    public QueryBuilder orderBy(String alias, Column<?> column) {
        query.append(String.format(" ORDER BY %s", joinAliasAndColumn(alias, column)));
        return this;
    }

    public QueryBuilder addField(String field) {
        query.append(field);
        return this;
    }

    public QueryBuilder leftJoin(String aliasForJoin, String tableNameForJoin, Column<?> columnForJoin, String alias, Column<?> column) {
        query.append(String.format(" JOIN %s %s ON %s = %s",
                tableNameForJoin, aliasForJoin, joinAliasAndColumn(alias, column), joinAliasAndColumn(aliasForJoin, columnForJoin)));
        return this;
    }

    public QueryBuilder leftJoin(String tableNameForJoin, Column<?> columnForJoin, Column<?> column) {
        query.append(String.format(" JOIN %s ON %s = %s",
                tableNameForJoin, column.name(), columnForJoin.name()));
        return this;
    }

    public String getQuery() {
        return query.toString();
    }

    private String joinAliasAndColumn(String alias, Column<?> column) {
        if (alias == null || alias.isBlank()) {
            return column.name();
        } else {
            return alias + "." + column.name();
        }
    }
}
