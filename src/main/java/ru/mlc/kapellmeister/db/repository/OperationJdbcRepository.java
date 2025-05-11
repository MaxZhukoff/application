package ru.mlc.kapellmeister.db.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import ru.mlc.kapellmeister.db.Operation;
import ru.mlc.kapellmeister.db.mapper.OperationRowMapper;
import ru.mlc.kapellmeister.db.query.QueryBuilder;
import ru.mlc.kapellmeister.db.tables.Column;
import ru.mlc.kapellmeister.db.tables.OperationOrderBindingTable;
import ru.mlc.kapellmeister.db.tables.OperationTable;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

@RequiredArgsConstructor
public class OperationJdbcRepository {

    private final OperationRowMapper operationRowMapper;
    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedJdbcTemplate;

    private final OperationTable operationTable;
    private final OperationOrderBindingTable operationOrderBindingTable;

    public List<Operation> findAll() {
        return jdbcTemplate.query(new QueryBuilder()
                .select(operationTable.allColumnsName())
                .from(operationTable.getTableName())
                .getQuery(), operationRowMapper);
    }

    public List<Operation> findAllByIds(Set<UUID> operationIds) {
        if (operationIds.isEmpty()) {
            return Collections.emptyList();
        }
        SqlParameterSource parameters = new MapSqlParameterSource(operationTable.getId().name(), operationIds);
        return namedJdbcTemplate.query(new QueryBuilder()
                .select(operationTable.allColumnsName())
                .from(operationTable.getTableName())
                .where()
                .inNamed(operationTable.getId(), operationTable.getId().name())
                .getQuery(), parameters, operationRowMapper);
    }

    public Set<String> findAllExecutorNames(Set<String> statuses) {
        SqlParameterSource parameters = new MapSqlParameterSource(operationTable.getStatus().name(), statuses);
        return new HashSet<>(namedJdbcTemplate.queryForList(new QueryBuilder()
                .selectDistinct(operationTable.getStatus().name())
                .from(operationTable.getTableName())
                .where()
                .inNamed(operationTable.getStatus(), operationTable.getStatus().name())
                .getQuery(), parameters, String.class));
    }

    public Optional<Operation> findById(UUID id) {
        return Optional.ofNullable(jdbcTemplate.queryForObject(new QueryBuilder()
                .select(operationTable.allColumnsName())
                .from(operationTable.getTableName())
                .where()
                .whereParam(operationTable.getId(), "=")
                .getQuery(), operationRowMapper, id));
    }

    public List<Operation> findByExecutorNameAndGroupId(String executorName, UUID groupId) {
        return jdbcTemplate.query(new QueryBuilder()
                        .select(operationTable.allColumnsName())
                        .from(operationTable.getTableName())
                        .where()
                        .whereParam(operationTable.getExecutorName(), "=")
                        .and()
                        .whereParam(operationTable.getGroupId(), "=")
                        .getQuery(),
                operationRowMapper, executorName, groupId);
    }

    public Optional<Operation> findByIdWithPrevious(UUID id) {
        Optional<Operation> operationOpt = findById(id);
        return operationOpt.map(this::findPrevious);
    }

    public Operation findPrevious(Operation operation) {
        String previousSql = new QueryBuilder()
                .select(operationTable.allColumnsName())
                .from("o", operationTable.getTableName())
                .leftJoin("oob", operationOrderBindingTable.getTableName(),
                        operationOrderBindingTable.getPreviousOperationId(),
                        "o", operationTable.getId())
                .where()
                .whereParam("oob", operationOrderBindingTable.getOperationId(), "=")
                .getQuery();
        List<Operation> previousOperations = jdbcTemplate.query(previousSql, operationRowMapper, operation.getId());

        operation.setPrevious(previousOperations);
        return operation;
    }

    public List<Operation> findNext(Operation operation) {
        String nextSql = new QueryBuilder()
                .select(operationTable.allColumnsName())
                .from("o", operationTable.getTableName())
                .leftJoin("oob", operationOrderBindingTable.getTableName(),
                        operationOrderBindingTable.getOperationId(),
                        "o", operationTable.getId())
                .where()
                .whereParam("oob", operationOrderBindingTable.getPreviousOperationId(), "=")
                .getQuery();
        return jdbcTemplate.query(nextSql, operationRowMapper, operation.getId());
    }

    public boolean updateStatus(Operation operation) {
        String updateSql = new QueryBuilder()
                .update(operationTable.getTableName())
                .paramsForUpdate(Stream.of(operationTable.getUpdateTimestamp(),
                                operationTable.getStatus(),
                                operationTable.getVersion())
                        .toArray(Column[]::new))
                .where()
                .whereParam(operationTable.getId(), "=")
                .and()
                .whereParam(operationTable.getVersion(), "=")
                .getQuery();
        int columnsInserted = jdbcTemplate.update(updateSql,
                Timestamp.from(Instant.now()),
                operation.getStatus(),
                operation.getVersion(),
                operation.getId(), operation.getVersion() + 1);
        return columnsInserted == 1;
    }

    public boolean update(Operation operation) {
        String updateSql = new QueryBuilder()
                .update(operationTable.getTableName())
                .paramsForUpdate(Arrays.stream(operationTable.allColumns())
                        .filter(column -> !operationTable.getId().equals(column))
                        .filter(column -> !operationTable.getCreateTimestamp().equals(column))
                        .toArray(Column[]::new))
                .where()
                .whereParam(operationTable.getId(), "=")
                .and()
                .whereParam(operationTable.getVersion(), "=")
                .getQuery();
        int columnsUpdated = jdbcTemplate.update(updateSql,
                operation.getGroupId(),
                operation.getExecutorName(),
                operation.getType().name(),
                operation.getParams(),
                operation.getDescription(),
                operation.getRelatedEntityId(),
                operation.getRollbackType().name(),
                operation.getImportanceType().name(),
                operation.getPriority(),
                operation.getAttemptCount(),
                operation.getMaxAttemptCount(),
                operation.getRetryDelay(),
                operation.getWaitResponseTimeout(),
                Timestamp.from(Instant.now()),
                operation.getDeadlineTimestamp() == null ? null : Timestamp.from(operation.getDeadlineTimestamp()),
                operation.getLastExecutionTimeStamp() == null ? null : Timestamp.from(operation.getLastExecutionTimeStamp()),
                operation.getStatus().name(),
                operation.getExecutionResult() == null ? null : operation.getExecutionResult().name(),
                operation.getComment(),
                operation.getVersion() + 1,
                operation.getId(), operation.getVersion());
        return columnsUpdated == 1;
    }

    public boolean save(Operation operation) {
        String insertSql = new QueryBuilder()
                .insert(operationTable.getTableName(), operationTable.allColumns())
                .getQuery();
        int columnsInserted = jdbcTemplate.update(insertSql,
                operation.getId(),
                operation.getGroupId(),
                operation.getExecutorName(),
                operation.getType().name(),
                operation.getParams(),
                operation.getDescription(),
                operation.getRelatedEntityId(),
                operation.getRollbackType().name(),
                operation.getImportanceType().name(),
                operation.getPriority(),
                operation.getAttemptCount(),
                operation.getMaxAttemptCount(),
                operation.getRetryDelay(),
                operation.getWaitResponseTimeout(),
                Timestamp.from(Instant.now()),
                Timestamp.from(Instant.now()),
                operation.getDeadlineTimestamp() == null ? null : Timestamp.from(operation.getDeadlineTimestamp()),
                operation.getLastExecutionTimeStamp() == null ? null : Timestamp.from(operation.getLastExecutionTimeStamp()),
                operation.getStatus().name(),
                operation.getExecutionResult() == null ? null : operation.getExecutionResult().name(),
                operation.getComment(),
                0);
        if (columnsInserted == 1) {
            for (Operation previousOperation : operation.getPrevious()) {
                jdbcTemplate.update(new QueryBuilder()
                        .insert(operationOrderBindingTable.getTableName(), operationOrderBindingTable.allColumns())
                        .getQuery(), operation.getId(), previousOperation.getId());
            }
            return true;
        }
        return false;
    }
}
