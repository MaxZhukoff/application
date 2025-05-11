package ru.mlc.kapellmeister.service;

import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import ru.mlc.common.exception.NotFoundException;
import ru.mlc.kapellmeister.api.KapellmeisterStorageService;
import ru.mlc.kapellmeister.constants.OperationGroupStatus;
import ru.mlc.kapellmeister.constants.OperationStatus;
import ru.mlc.kapellmeister.db.Operation;
import ru.mlc.kapellmeister.db.OperationGroup;
import ru.mlc.kapellmeister.db.repository.OperationGroupJdbcRepository;
import ru.mlc.kapellmeister.db.repository.OperationJdbcRepository;
import ru.mlc.kapellmeister.exceptions.KapellmeisterException;
import ru.mlc.kapellmeister.exceptions.OperationGroupNotFoundException;
import ru.mlc.kapellmeister.exceptions.OperationNotFoundException;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public class KapellmeisterPostgresJdbcStorageService implements KapellmeisterStorageService {

    private final OperationJdbcRepository operationRepository;
    private final OperationGroupJdbcRepository operationGroupRepository;

    public List<Operation> findAll() {
        return operationRepository.findAll();
    }

    public List<Operation> findAllRequired(Set<UUID> operationIds) {
        List<Operation> allById = operationRepository.findAllByIds(operationIds);
        if (allById.size() < operationIds.size()) {
            Set<UUID> foundedIds = allById.stream()
                    .map(Operation::getId)
                    .collect(Collectors.toSet());
            Set<UUID> unFoundedIds = operationIds.stream()
                    .filter(id -> !foundedIds.contains(id))
                    .collect(Collectors.toSet());
            throw new NotFoundException("Не найдены операции " + unFoundedIds);
        }
        return allById;
    }

    public Collection<Operation> findOperations(String executorName, UUID groupId) {
        return operationRepository.findByExecutorNameAndGroupId(executorName, groupId);
    }

    public Operation getOperation(UUID operationId) {
        return operationRepository.findById(operationId)
                .orElseThrow(() -> new OperationNotFoundException(operationId));
    }

    @Override
    public Operation setPreviousOperation(Operation operation) {
        return operationRepository.findPrevious(operation);
    }

    public List<Operation> findNextOperations(Operation operation) {
        return operationRepository.findNext(operation);
    }

    public Set<String> getExecutorNamesWithUncompletedOperations() {
        Set<String> uncompletedOperationStatuses = Arrays.stream(OperationStatus.values())
                .filter(status -> !status.isCompleted())
                .map(Enum::name)
                .collect(Collectors.toSet());
        return operationRepository.findAllExecutorNames(uncompletedOperationStatuses);
    }

    @Transactional
    public Operation save(Operation operation) {
        if (operation.getId() != null) {
            throw new KapellmeisterException("Операция "+ operation.getId() +" уже сохранения");
        }
        operation.setId(UUID.randomUUID());
        operationRepository.save(operation);
        return operation;
    }

    @Override
    public boolean update(Operation operation) {
        return operationRepository.update(operation);
    }

    public Optional<OperationGroup> findGroup(UUID operationGroupId) {
        return operationGroupRepository.findById(operationGroupId);
    }

    public OperationGroup getGroup(UUID operationGroupId) {
        return findGroup(operationGroupId)
                .orElseThrow(() -> new OperationGroupNotFoundException(operationGroupId));
    }

    public List<OperationGroup> getAvailableGroups(Instant groupCreateStartTime) {
        return operationGroupRepository.findAvailableOperationGroupsCreatedBefore(groupCreateStartTime);
    }

    public List<OperationGroup> getUncompleted() {
        return operationGroupRepository.getUncompleted();
    }

    public OperationGroup createGroup(UUID operationGroupId, String description, UUID parentOperationId) {
        operationGroupRepository.save(operationGroupId, description, parentOperationId);
        return OperationGroup.builder()
                .id(operationGroupId)
                .description(description)
                .parentOperationId(parentOperationId)
                .createTimestamp(Instant.now())
                .updateTimestamp(Instant.now())
                .status(OperationGroupStatus.CREATED)
                .build();
    }

    @Transactional
    public OperationGroup update(OperationGroup operationGroup) {
        operationGroupRepository.update(operationGroup);
        return operationGroup;
    }
}
