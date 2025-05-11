package ru.mlc.kapellmeister.db.tables;

import java.util.Arrays;
import java.util.stream.Collectors;

public interface Table {

    String getTableName();

    Column<?>[] allColumns();

    default int getColumnsCount() {
        return allColumns().length;
    }

    default String[] allColumnNames() {
        return Arrays.stream(allColumns()).map(Column::name).toArray(String[]::new);
    }

    default String allColumnsName() {
        return Arrays.stream(allColumnNames())
                .map(column -> buildColumnNameWithAlias(column, ""))
                .collect(Collectors.joining(","));
    }

    default String allColumnsNameWithAlias(String alias) {
        return Arrays.stream(allColumnNames())
                .map(column -> buildColumnNameWithAlias(column, alias))
                .collect(Collectors.joining(","));
    }

    default String buildColumnNameWithAlias(String columnName, String alias) {
        if (alias.isBlank()) {
            return columnName;
        } else {
            return alias + "." + columnName + " AS " + alias + "_" + columnName;
        }
    }
}
