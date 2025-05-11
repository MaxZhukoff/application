package ru.mlc.kapellmeister.api;

import ru.mlc.kapellmeister.db.Operation;
import ru.mlc.kapellmeister.db.OperationGroup;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface KapellmeisterStorageService {

    List<Operation> findAll();

    List<Operation> findAllRequired(Set<UUID> operationIds);

    Collection<Operation> findOperations(String executorName, UUID groupId);

    Operation getOperation(UUID operationId);

    Operation setPreviousOperation(Operation operation);

    List<Operation> findNextOperations(Operation operation);

    Operation save(Operation operation);

    boolean update(Operation operation);

    Optional<OperationGroup> findGroup(UUID operationGroupId);

    OperationGroup getGroup(UUID operationGroupId);

    List<OperationGroup> getAvailableGroups(Instant groupCreateStartTime);

    Set<String> getExecutorNamesWithUncompletedOperations();

    List<OperationGroup> getUncompleted();

    OperationGroup createGroup(UUID operationGroupId, String description, UUID parentOperationId);

    OperationGroup update(OperationGroup operationGroup);
}
