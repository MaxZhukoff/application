package ru.mlc.kapellmeister.service;

import lombok.RequiredArgsConstructor;
import ru.mlc.kapellmeister.api.KapellmeisterStorageService;
import ru.mlc.kapellmeister.api.OperationConfig;
import ru.mlc.kapellmeister.constants.OperationImportanceType;
import ru.mlc.kapellmeister.db.Operation;
import ru.mlc.kapellmeister.db.OperationGroup;
import ru.mlc.kapellmeister.exceptions.OperationConfigValidationException;
import ru.mlc.kapellmeister.exceptions.OperationCycleOrderValidationException;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public class OperationValidator {

    private final KapellmeisterStorageService kapellmeisterStorageService;

    public void validateOrder(OperationGroup operationGroup) {
        validateCycleBinding(operationGroup);
        operationGroup.getOperations().forEach(this::validatePreviousForCritical);
    }

    public void validatePreviousForCritical(Operation operation) {
        if (operation.getImportanceType() == OperationImportanceType.CRITICAL) {
            getAllPrevious(operation, new ArrayList<>()).stream()
                    .filter(o -> o.getImportanceType() != OperationImportanceType.CRITICAL)
                    .findFirst()
                    .ifPresent(o -> {
                        throw new OperationConfigValidationException("Критической операции " + operation + " предшествует не критическая " + o);
                    });
        }
    }

    private List<Operation> getAllPrevious(Operation operation, List<Operation> list) {
        kapellmeisterStorageService.setPreviousOperation(operation);
        for (Operation previous : operation.getPrevious()) {
            list.add(previous);
            getAllPrevious(previous, list);
        }
        return list;
    }

    public void validateCycleBinding(OperationGroup operationGroup) {
        operationGroup.getOperations().forEach(this::validateCycleOrder);
    }

    private void validateCycleOrder(Operation operation) {
        LinkedList<Operation> chain = new LinkedList<>();
        findCycle(operation, chain);
    }

    private void findCycle(Operation operation, LinkedList<Operation> chain) {
        if (chain.contains(operation)) {
            chain.add(operation);
            throw new OperationCycleOrderValidationException(chain);
        }
        chain.add(operation);

        kapellmeisterStorageService.setPreviousOperation(operation);
        for (Operation previous : operation.getPrevious()) {
            findCycle(previous, chain);
        }
        chain.removeLast();
    }

    public void validateOperationsRelation(OperationGroup operationGroup, List<Operation> operations) {
        UUID operationGroupId = operationGroup.getId();
        String incorrectPreviousOperations = operations.stream()
                .filter(operation -> !operation.getGroupId().equals(operationGroupId))
                .map(Operation::getId)
                .map(UUID::toString)
                .collect(Collectors.joining(", "));
        if (!incorrectPreviousOperations.isEmpty()) {
            throw new OperationConfigValidationException("Переданные операции " + incorrectPreviousOperations + " не относятся к группе " + operationGroupId);
        }
    }

    public <T> void validate(OperationConfig<T> config) {
        if (config.getExecutor() == null) {
            throw new OperationConfigValidationException("Executor is required");
        }
        if (config.getImportanceType() == null) {
            throw new OperationConfigValidationException("Importance is required");
        }
        if (config.getPriority() == null || config.getPriority() < 0 || config.getPriority() > 10) {
            throw new OperationConfigValidationException("Не допустимое значение priority = " + config.getPriority() + " (допустимые значение от 0 до 10");
        }
        if (config.getMaxAttemptCount() == null || config.getMaxAttemptCount() <= 0) {
            throw new OperationConfigValidationException("Не допустимое значение maxAttemptCount = " + config.getMaxAttemptCount() + " (Значение должно быть больше 0)");
        }
        if (config.getRetryDelay() == null || config.getRetryDelay() < 0) {
            throw new OperationConfigValidationException("Не допустимое значение retryDelay = " + config.getRetryDelay() + " (Значение должно быть больше либо равно 0)");
        }
        if (config.getWaitResponseTimeout() != null && config.getWaitResponseTimeout() < 0) {
            throw new OperationConfigValidationException("Не допустимое значение waitResponseTimeout = " + config.getWaitResponseTimeout() + " (Значение должно быть больше либо равно 0)");
        }
        if (config.getExecuteAfterCommit() == null) {
            throw new OperationConfigValidationException("executeAfterCommit is required");
        }
        if (config.getOptimized() == null) {
            throw new OperationConfigValidationException("optimized is required");
        }
    }
}
