package ru.mlc.kapellmeister.db.tables;

import lombok.Value;

import java.util.UUID;

@Value
public class OperationOrderBindingTable implements Table {
    String tableName = "kapellmeister_operation_order_binding";
    Column<UUID> operationId = new Column<>(1, "operation_id", UUID.class);
    Column<UUID> previousOperationId = new Column<>(2, "previous_operation_id", UUID.class);

    @Override
    public Column<?>[] allColumns() {
        return new Column[]{
                operationId,
                previousOperationId
        };
    }
}
