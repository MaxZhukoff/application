package ru.mlc.kapellmeister.db.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import ru.mlc.kapellmeister.constants.OperationGroupStatus;
import ru.mlc.kapellmeister.db.OperationGroup;
import ru.mlc.kapellmeister.db.mapper.OperationGroupRowMapper;
import ru.mlc.kapellmeister.db.query.QueryBuilder;
import ru.mlc.kapellmeister.db.tables.Column;
import ru.mlc.kapellmeister.db.tables.OperationGroupTable;
import ru.mlc.kapellmeister.db.tables.OperationTable;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static ru.mlc.kapellmeister.constants.OperationGroupStatus.AVAILABLE_FOR_PROCESS;
import static ru.mlc.kapellmeister.constants.OperationGroupStatus.UNCOMPLETED;

@RequiredArgsConstructor
public class OperationGroupJdbcRepository {

    private final OperationGroupRowMapper operationGroupRowMapper;
    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedJdbcTemplate;

    private final OperationGroupTable operationGroupTable;
    private final OperationTable operationTable;

    private static final String GROUP_ALIAS = "grp";
    private static final String OPERATION_ALIAS = "operation";

    private QueryBuilder selectQueryWithOperations() {
        return new QueryBuilder()
                .select(operationGroupTable.allColumnsNameWithAlias(GROUP_ALIAS) + "," +
                        operationTable.allColumnsNameWithAlias(OPERATION_ALIAS))
                .from(GROUP_ALIAS, operationGroupTable.getTableName())
                .leftJoin(OPERATION_ALIAS, operationTable.getTableName(), operationTable.getGroupId(),
                        GROUP_ALIAS, operationGroupTable.getId());
    }

    public List<OperationGroup> findAll() {
        return jdbcTemplate.query(selectQueryWithOperations()
                .getQuery(), operationGroupRowMapper);
    }

    public Optional<OperationGroup> findById(UUID id) {
        List<OperationGroup> operationGroupList = jdbcTemplate.query(selectQueryWithOperations()
                .where()
                .whereParam(GROUP_ALIAS, operationGroupTable.getId(), "=")
                .getQuery(), operationGroupRowMapper, id);
        if (operationGroupList != null && operationGroupList.size() == 1) {
            return Optional.of(operationGroupList.get(0));
        } else {
            return Optional.empty();
        }
    }

    public List<OperationGroup> findAvailableOperationGroupsCreatedBefore(Instant groupCreateStartTime) {
        SqlParameterSource parameters = new MapSqlParameterSource(Map.of(
                operationGroupTable.getStatus().name(), AVAILABLE_FOR_PROCESS.stream().map(Enum::name).toList(),
                operationGroupTable.getCreateTimestamp().name(), Timestamp.from(groupCreateStartTime)));
        return namedJdbcTemplate.query(selectQueryWithOperations()
                .where()
                .inNamed(GROUP_ALIAS, operationGroupTable.getStatus(), operationGroupTable.getStatus().name())
                .and()
                .whereNamedParam(GROUP_ALIAS, operationGroupTable.getCreateTimestamp(), "<", "create_timestamp")
                .orderBy(GROUP_ALIAS, operationGroupTable.getUpdateTimestamp())
                .getQuery(), parameters, operationGroupRowMapper);
    }

    public List<OperationGroup> getUncompleted() {
        SqlParameterSource parameters = new MapSqlParameterSource(operationGroupTable.getStatus().name(), UNCOMPLETED.stream().map(Enum::name).toList());
        return namedJdbcTemplate.query(selectQueryWithOperations()
                .where()
                .inNamed(GROUP_ALIAS, operationGroupTable.getStatus(), operationGroupTable.getStatus().name())
                .orderBy(GROUP_ALIAS, operationGroupTable.getUpdateTimestamp())
                .getQuery(), parameters, operationGroupRowMapper);
    }

    public boolean update(OperationGroup operationGroup) {
        String updateSql = new QueryBuilder()
                .update(operationGroupTable.getTableName())
                .paramsForUpdate(Arrays.stream(operationGroupTable.allColumns())
                        .filter(column -> !operationGroupTable.getId().equals(column))
                        .filter(column -> !operationGroupTable.getCreateTimestamp().equals(column))
                        .filter(column -> !operationGroupTable.getParentOperationId().equals(column))
                        .toArray(Column[]::new))
                .where()
                .whereParam(operationTable.getId(), "=")
                .getQuery();
        int columnsUpdated = jdbcTemplate.update(updateSql,
                operationGroup.getStatus().name(),
                operationGroup.getDescription(),
                Timestamp.from(Instant.now()),
                operationGroup.getComment(),
                operationGroup.getId());
        return columnsUpdated == 1;
    }

    public boolean save(UUID operationGroupId, String description, UUID parentOperationId) {
        String insertSql = new QueryBuilder()
                .insert(operationGroupTable.getTableName(), operationGroupTable.allColumns())
                .getQuery();
        int columns = jdbcTemplate.update(insertSql,
                operationGroupId,
                OperationGroupStatus.CREATED.name(),
                description,
                Timestamp.from(Instant.now()),
                Timestamp.from(Instant.now()),
                null,
                parentOperationId);
        return columns == 1;
    }
}
