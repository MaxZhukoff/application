package ru.mlc.kapellmeister.db.tables;

public record Column<T>(
        int index,
        String name,
        Class<T> type
) {
}
