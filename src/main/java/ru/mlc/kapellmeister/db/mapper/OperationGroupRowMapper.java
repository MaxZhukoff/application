package ru.mlc.kapellmeister.db.mapper;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ResultSetExtractor;
import ru.mlc.kapellmeister.constants.OperationGroupStatus;
import ru.mlc.kapellmeister.db.Operation;
import ru.mlc.kapellmeister.db.OperationGroup;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RequiredArgsConstructor
public class OperationGroupRowMapper implements ResultSetExtractor<List<OperationGroup>> {

    private final OperationRowMapper operationRowMapper;

    @Override
    public List<OperationGroup> extractData(ResultSet rs) throws SQLException, DataAccessException {
        Map<UUID, OperationGroup> groupMap = new HashMap<>();

        while (rs.next()) {
            UUID groupId = rs.getObject("grp_id", UUID.class);
            OperationGroup group = groupMap.get(groupId);

            if (group == null) {
                group = getOperationGroupEntityData(rs, groupId, "grp_");
                groupMap.put(groupId, group);
            }

            UUID operationId = rs.getObject("operation_id", UUID.class);
            if (operationId != null) {
                Operation operation = operationRowMapper.mapRow(rs, "operation_");
                group.getOperations().add(operation);
            }
        }

        return new ArrayList<>(groupMap.values());
    }

    public OperationGroup getOperationGroupEntityData(ResultSet rs, UUID groupId, String prefix) throws SQLException {
        return OperationGroup.builder()
                .id(groupId)
                .status(OperationGroupStatus.valueOf(rs.getString(prefix + "status")))
                .createTimestamp(rs.getTimestamp(prefix + "create_timestamp").toInstant())
                .updateTimestamp(rs.getTimestamp(prefix + "update_timestamp").toInstant())
                .comment(rs.getString(prefix + "comment"))
                .parentOperationId(rs.getObject(prefix + "parent_operation_id", UUID.class))
                .operations(new ArrayList<>())
                .build();
    }
}
