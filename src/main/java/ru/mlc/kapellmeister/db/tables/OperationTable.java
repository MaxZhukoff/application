package ru.mlc.kapellmeister.db.tables;

import lombok.Value;

import java.sql.Timestamp;
import java.util.UUID;

@Value
public class OperationTable implements Table {
    String tableName = "kapellmeister_operation";
    Column<UUID> id = new Column<>(1, "id", java.util.UUID.class);
    Column<UUID> groupId = new Column<>(2, "group_id", java.util.UUID.class);
    Column<String> executorName = new Column<>(3, "executor_name", String.class);
    Column<String> type = new Column<>(4, "type", String.class);
    Column<String> params = new Column<>(5, "params", String.class);
    Column<String> description = new Column<>(6, "description", String.class);
    Column<UUID> relatedEntityId = new Column<>(7, "related_entity_id", java.util.UUID.class);
    Column<String> rollbackType = new Column<>(8, "rollback_type", String.class);
    Column<String> importanceType = new Column<>(9, "importance_type", String.class);
    Column<Integer> priority = new Column<>(10, "priority", Integer.class);
    Column<Integer> attemptCount = new Column<>(11, "attempt_count", Integer.class);
    Column<Integer> maxAttemptCount = new Column<>(12, "max_attempt_count", Integer.class);
    Column<Long> retryDelay = new Column<>(13, "retry_delay", Long.class);
    Column<Timestamp> waitResponseTimeout = new Column<>(14, "wait_response_timeout", Timestamp.class);
    Column<Timestamp> createTimestamp = new Column<>(15, "create_timestamp", Timestamp.class);
    Column<Timestamp> updateTimestamp = new Column<>(16, "update_timestamp", Timestamp.class);
    Column<Timestamp> deadlineTimestamp = new Column<>(17, "deadline_timestamp", Timestamp.class);
    Column<Timestamp> lastExecutionTimeStamp = new Column<>(18, "last_execution_time_stamp", Timestamp.class);
    Column<String> status = new Column<>(19, "status", String.class);
    Column<String> executionResult = new Column<>(20, "execution_result", String.class);
    Column<String> comment = new Column<>(21, "comment", String.class);
    Column<Long> version = new Column<>(22, "version", Long.class);

    @Override
    public Column<?>[] allColumns() {
        return new Column[]{id,
                groupId,
                executorName,
                type,
                params,
                description,
                relatedEntityId,
                rollbackType,
                importanceType,
                priority,
                attemptCount,
                maxAttemptCount,
                retryDelay,
                waitResponseTimeout,
                createTimestamp,
                updateTimestamp,
                deadlineTimestamp,
                lastExecutionTimeStamp,
                status,
                executionResult,
                comment,
                version};
    }
}
