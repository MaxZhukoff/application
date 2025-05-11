package ru.mlc.kapellmeister.db.tables;

import lombok.Value;

import java.sql.Timestamp;
import java.util.UUID;

@Value
public class OperationGroupTable implements Table {
    String tableName = "kapellmeister_operation_group";
    Column<UUID> id = new Column<>(1, "id", UUID.class);
    Column<String> status = new Column<>(2, "status", String.class);
    Column<String> description = new Column<>(3, "description", String.class);
    Column<Timestamp> createTimestamp = new Column<>(4, "create_timestamp", Timestamp.class);
    Column<Timestamp> updateTimestamp = new Column<>(5, "update_timestamp", Timestamp.class);
    Column<String> comment = new Column<>(6, "comment", String.class);
    Column<UUID> parentOperationId = new Column<>(7, "parent_operation_id", UUID.class);

    @Override
    public Column<?>[] allColumns() {
        return new Column[]{id,
                status,
                description,
                createTimestamp,
                updateTimestamp,
                comment,
                parentOperationId};
    }
}
