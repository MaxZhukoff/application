package ru.mlc.kapellmeister.db.mapper;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import ru.mlc.kapellmeister.constants.OperationExecutionResult;
import ru.mlc.kapellmeister.constants.OperationImportanceType;
import ru.mlc.kapellmeister.constants.OperationStatus;
import ru.mlc.kapellmeister.constants.OperationType;
import ru.mlc.kapellmeister.constants.RollbackType;
import ru.mlc.kapellmeister.db.Operation;
import ru.mlc.kapellmeister.db.tables.Column;
import ru.mlc.kapellmeister.db.tables.OperationTable;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;

@RequiredArgsConstructor
public class OperationRowMapper implements RowMapper<Operation> {

    private final OperationTable table;

    @Override
    public Operation mapRow(ResultSet resultSet, int rowNum) throws SQLException {
        return mapRow(resultSet, "");
    }

    public Operation mapRow(ResultSet resultSet, String prefix) throws SQLException {
        Timestamp deadlineTimestamp = resultSet.getTimestamp(prefix + table.getDeadlineTimestamp().name());
        Timestamp lastExecutionTimeStamp = resultSet.getTimestamp(prefix + table.getLastExecutionTimeStamp().name());
        String executionResult = resultSet.getString(prefix + table.getExecutionResult().name());
        Object waitResponseTimeout = resultSet.getObject((prefix + table.getWaitResponseTimeout().name()));
        return Operation.builder()
                .id(getObject(resultSet, table.getId(), prefix))
                .groupId(getObject(resultSet, table.getGroupId(), prefix))
                .executorName(resultSet.getString(prefix + table.getExecutorName().name()))
                .type(OperationType.valueOf(resultSet.getString(prefix + table.getType().name())))
                .params(resultSet.getString(prefix + table.getParams().name()))
                .description(resultSet.getString(prefix + table.getDescription().name()))
                .relatedEntityId(getObject(resultSet, table.getRelatedEntityId(), prefix))
                .rollbackType(RollbackType.valueOf(resultSet.getString(prefix + table.getRollbackType().name())))
                .importanceType(OperationImportanceType.valueOf(resultSet.getString(prefix + table.getImportanceType().name())))
                .priority(resultSet.getInt(prefix + table.getPriority().name()))
                .attemptCount(resultSet.getInt(prefix + table.getAttemptCount().name()))
                .maxAttemptCount(resultSet.getInt(prefix + table.getMaxAttemptCount().name()))
                .retryDelay(resultSet.getLong((prefix + table.getRetryDelay().name())))
                .waitResponseTimeout(waitResponseTimeout == null ? null : resultSet.getLong((prefix + table.getWaitResponseTimeout().name())))
                .createTimestamp(resultSet.getTimestamp(prefix + table.getCreateTimestamp().name()).toInstant())
                .updateTimestamp(resultSet.getTimestamp(prefix + table.getUpdateTimestamp().name()).toInstant())
                .deadlineTimestamp(deadlineTimestamp == null ? null : deadlineTimestamp.toInstant())
                .lastExecutionTimeStamp(lastExecutionTimeStamp == null ? null : lastExecutionTimeStamp.toInstant())
                .status(OperationStatus.valueOf(resultSet.getString(prefix + table.getStatus().name())))
                .executionResult(executionResult == null ? null : OperationExecutionResult.valueOf(executionResult))
                .comment(resultSet.getString(prefix + table.getComment().name()))
                .version(resultSet.getInt(prefix + table.getVersion().name()))
                .build();
    }

    private <T> T getObject(ResultSet resultSet, Column<T> column, String prefix) throws SQLException {
        return resultSet.getObject(prefix + column.name(), column.type());
    }
}
