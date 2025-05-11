package ru.mlc.kapellmeister.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.mlc.kapellmeister.api.KapellmeisterEngine;
import ru.mlc.kapellmeister.api.KapellmeisterOperationThreadPoolExecutor;
import ru.mlc.kapellmeister.api.KapellmeisterStorageService;
import ru.mlc.kapellmeister.api.OperationState;
import ru.mlc.kapellmeister.constants.OperationExecutionResult;
import ru.mlc.kapellmeister.constants.OperationGroupStatus;
import ru.mlc.kapellmeister.constants.OperationImportanceType;
import ru.mlc.kapellmeister.constants.OperationStatus;
import ru.mlc.kapellmeister.constants.RollbackType;
import ru.mlc.kapellmeister.db.Operation;
import ru.mlc.kapellmeister.db.OperationGroup;
import ru.mlc.kapellmeister.exceptions.UnavailableUpdateOperationStateException;

import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@RequiredArgsConstructor
public class KapellmeisterEngineImpl implements KapellmeisterEngine {

    public static final Integer UNLIMITED = -1;

    private final boolean executingEnabled;
    private final KapellmeisterStorageService kapellmeisterStorageService;
    private final KapellmeisterTimeSynchronizationService timeService;
    private final KapellmeisterOperationThreadPoolExecutor kapellmeisterOperationThreadPoolExecutor;

    @Lazy
    @Autowired
    private KapellmeisterEngine self;

    @Override
    @Transactional
    public void saveResult(UUID operationId, OperationExecutionResult executionResult) {
        Operation operation = kapellmeisterStorageService.getOperation(operationId);
        if (!operation.getType().isAsync() || operation.getStatus() != OperationStatus.WAIT_RESPONSE) {
            throw new UnavailableUpdateOperationStateException("Не допускается обновлять состояние операции с типом " + operation.getType() + " и статусом " + operation.getStatus());
        } else {
            kapellmeisterOperationThreadPoolExecutor.saveResult(operationId, executionResult);
            updateGroupStatus(kapellmeisterStorageService.getGroup(operation.getGroupId()));
        }
    }

    /**
     * Выполняет все доступные группы операций
     *
     * @param jobDeadLine          максимальное время до которой может длиться итерация
     * @param maxCountOfOperations максимальное количество операций, которые необходимо выполнить, если не ограничиваем - ожидается значение -1
     * @return
     */
    @Override
    @Transactional
    public Set<UUID> executeAvailableOperationGroups(Instant jobDeadLine, Integer maxCountOfOperations) {
        return executeAllAvailableGroups(jobDeadLine, maxCountOfOperations);
    }

    public Set<UUID> executeAvailableOperationGroupsSync(Instant jobDeadLine, Integer maxCountOfOperations) {
        Set<UUID> operations = self.executeAvailableOperationGroups(jobDeadLine, maxCountOfOperations);
        kapellmeisterOperationThreadPoolExecutor.waitExecution();
        self.executeAvailableOperationGroups(jobDeadLine, 0);
        kapellmeisterOperationThreadPoolExecutor.waitExecution();

        return operations;
    }

    @Override
    @Transactional
    public Set<UUID> executeAvailableOperationGroups(Instant jobDeadLine) {
        return executeAllAvailableGroups(jobDeadLine, 10);
    }

    @Override
    public Set<UUID> executeAvailableOperationGroupsSync(Instant jobDeadLine) {
        Set<UUID> operations = self.executeAvailableOperationGroups(jobDeadLine);
        kapellmeisterOperationThreadPoolExecutor.waitExecution();
        self.executeAvailableOperationGroups(jobDeadLine, 0);
        kapellmeisterOperationThreadPoolExecutor.waitExecution();

        return operations;
    }

    private void updateGroupStatus(OperationGroup operationGroup) {
        Set<OperationStatus> operationStatuses = operationGroup.getOperations().stream()
                .map(Operation::getStatus)
                .collect(Collectors.toSet());
        List<Operation> criticFails = operationGroup.getOperations().stream()
                .filter(operation -> operation.getImportanceType() == OperationImportanceType.CRITICAL)
                .filter(operation -> operation.getStatus().isFailed())
                .collect(Collectors.toList());
        if (!criticFails.isEmpty() && operationGroup.getStatus() != OperationGroupStatus.ROLLBACK_IN_PROGRESS) {
            failGroup(operationGroup, criticFails);
        } else if (operationGroup.getStatus() == OperationGroupStatus.ROLLBACK_IN_PROGRESS &&
                   operationGroup.getOperations().stream()
                           .map(Operation::getStatus)
                           .noneMatch(s -> s == OperationStatus.SUCCESS || s.isFailed())) {
            changeOperationStatus(operationGroup, OperationGroupStatus.ROLLBACK_COMPLETED);
        } else if (operationGroup.getStatus() == OperationGroupStatus.ROLLBACK_IN_PROGRESS &&
                   operationGroup.getOperations().stream()
                           .map(Operation::getStatus)
                           .anyMatch(s -> s == OperationStatus.ROLLBACK_FAILED)) {
            changeOperationStatus(operationGroup, OperationGroupStatus.ERROR);
        } else if (operationGroup.getOperations().stream()
                .map(Operation::getStatus)
                .allMatch(OperationStatus::isCompleted)) {
            changeOperationStatus(operationGroup, OperationGroupStatus.COMPLETED);
        } else if (operationGroup.getOperations().stream()
                           .filter(party -> party.getImportanceType() == OperationImportanceType.CRITICAL)
                           .map(Operation::getStatus)
                           .allMatch(OperationStatus::isCompleted)
                   && operationGroup.getOperations().stream()
                           .map(Operation::getStatus)
                           .allMatch(partyStatus -> partyStatus.isCompleted() || partyStatus.isSuspended())) {
            changeOperationStatus(operationGroup, OperationGroupStatus.CONDITIONALLY_COMPLETED);
        } else if (operationGroup.getStatus() != OperationGroupStatus.IN_PROGRESS && OperationStatus.IN_PROGRESS.stream().anyMatch(operationStatuses::contains)) {
            changeOperationStatus(operationGroup, OperationGroupStatus.IN_PROGRESS);
        } else if (operationStatuses.contains(OperationStatus.WAIT_RESPONSE)) {
            log.info("Для группы операций {} ожидается выполнение асинхронной операции", operationGroup.getId());
        } else {
            log.error("Для группы операций {} сформирован недопустимый набор статусов операций {}", operationGroup.getId(),
                    operationGroup.getOperations().stream()
                            .collect(Collectors.toMap(Operation::getId, Operation::getStatus)));
        }

        OperationGroup savedOperationGroup = kapellmeisterStorageService.update(operationGroup);
        log.info("Статус группы {}  обновлен {}", savedOperationGroup.getId(), savedOperationGroup.getStatus());
    }

    private void failGroup(OperationGroup operationGroup, Collection<Operation> criticFails) {
        changeOperationStatus(operationGroup, OperationGroupStatus.FAILED);
        String cause = criticFails.stream().map(Operation::getId)
                .map(UUID::toString)
                .collect(Collectors.joining(", ", "Провалены обязательные операции: ", ""));
        log.error("Группа операций {} помечена как проваленная, cause: провалены обязательные операции {}", operationGroup.getId(), cause);
        operationGroup.setComment(cause);
        trySetRetryGroup(operationGroup);
        kapellmeisterStorageService.update(operationGroup);
    }

    private void trySetRetryGroup(OperationGroup operationGroup) {
        boolean canRetry = operationGroup.getOperations().stream()
                .filter(operation -> operation.getStatus().isCompleted() || operation.getStatus().isFailed())
                .noneMatch(operation -> operation.getRollbackType() == RollbackType.UNSUPPORTED);
        if (canRetry) {
            changeOperationStatus(operationGroup, OperationGroupStatus.ROLLBACK_IN_PROGRESS);
        }
    }

    private static void changeOperationStatus(OperationGroup group, OperationGroupStatus targetStatus) {
        log.info("Изменение статуса группы операций {} {} -> {}", group.getId(), group.getStatus(), targetStatus);
        group.setStatus(targetStatus);
    }

    private Set<UUID> executeAllAvailableGroups(Instant jobDeadLine, Integer maxCountOfOperations) {
        Instant operationGroupCreateStartTime = timeService.calculateOperationExecutionBlockedForInstant();
        List<OperationGroup> availableGroups = kapellmeisterStorageService.getAvailableGroups(operationGroupCreateStartTime);
        availableGroups.forEach(this::updateGroupStatus);

        Set<UUID> executedOperations;
        int executionProcessingCount = 0;
        executedOperations = executeAvailableOperationGroups(availableGroups,
                jobDeadLine,
                resolveAvailableForExecutionOperationsCount(maxCountOfOperations, executionProcessingCount));
        if (countOfOperationsIsExceeded(maxCountOfOperations, executionProcessingCount)) {
            log.warn("Достигнуто максимально допустимое количество операций " + executedOperations.size());
        }
        log.info("Обработаны все доступные группы операций");
        return executedOperations;
    }

    private Set<UUID> executeAvailableOperationGroups(List<OperationGroup> availableGroups, Instant jobDeadLine, Integer maxCountOfOperations) {
        return availableGroups.stream()
                .map(OperationGroup::getId)
                .flatMap(operationGroupId -> {
                    if (jobIsInterrupted(jobDeadLine)) {
                        log.warn("Инициировано прерывание работы планировщика задач, группа {} не будет выполнена в текущей итерации", operationGroupId);
                        return Stream.of();
                    } else {
                        try {
                            return processOperationsOfGroup(operationGroupId, jobDeadLine, operationId -> true, maxCountOfOperations).stream();
                        } catch (Exception e) {
                            log.error("При выполнении группы операций {} произошла ошибка", operationGroupId, e);
                            return Stream.of();
                        }
                    }

                })
                .collect(Collectors.toSet());
    }

    private boolean jobIsInterrupted(Instant jobDeadLine) {
        return Thread.currentThread().isInterrupted() || timeService.isIterationPeriodLimitReached(jobDeadLine);
    }

    /**
     * Определяет возможность выполнения операций в зависимости от количества уже выполненных операций и параметра maxCountOfOperationsForIteration
     *
     * @param maxCountOfOperations
     * @param executedOperations
     * @return true в случае, если количество операций превышено, false - если не превышено или не ограничено
     */
    private boolean countOfOperationsIsExceeded(Integer maxCountOfOperations, Integer executedOperations) {
        return !UNLIMITED.equals(maxCountOfOperations) && executedOperations >= maxCountOfOperations;
    }

    private int resolveAvailableForExecutionOperationsCount(Integer maxCountOfOperations, Integer executedOperations) {
        return UNLIMITED.equals(maxCountOfOperations) ? UNLIMITED : Math.max(maxCountOfOperations - executedOperations, 0);
    }

    @Override
    @Transactional(propagation = Propagation.NEVER)
    public Set<UUID> processOperationsOfGroup(UUID operationGroupId, Instant jobDeadLine, Predicate<OperationState> filter, Integer maxCountOfOperations) {
        Set<UUID> executedOperations = new HashSet<>();
        if (!executingEnabled) {
            log.warn("Группа операций не может быть выполнена, параметр executingEnabled = false");
            return executedOperations;
        }
        boolean needProcessNextOperation;
        do {
            if (jobIsInterrupted(jobDeadLine)) {
                log.warn("Инициировано прерывание работы планировщика задач, выполнение операций из группы {} остановлено", operationGroupId);
                return executedOperations;
            }
            if (countOfOperationsIsExceeded(maxCountOfOperations, executedOperations.size())) {
                log.warn("Достигнуто максимально допустимое количество операций " + executedOperations.size());
                return executedOperations;
            }
            Optional<UUID> resolveOperationForExecuting = resolveOperationForExecuting(filter, operationGroupId);
            needProcessNextOperation = resolveOperationForExecuting
                    .filter(operationId -> !executedOperations.contains(operationId))
                    .map(operationId -> {
                        kapellmeisterOperationThreadPoolExecutor.submitOperation(operationId);
                        executedOperations.add(operationId);
                        return true;
                    }).orElse(false);
            if (resolveOperationForExecuting.isPresent()) {
                filter = filter.and(operation -> !operation.getId().equals(resolveOperationForExecuting.get()));
            }
        } while (needProcessNextOperation);
        log.info("Обработаны все доступные операции из группы {}", operationGroupId);
        return executedOperations;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UUID> resolveOperationForExecuting(Predicate<OperationState> operationFilter, UUID operationGroupId) {
        OperationGroup operationGroup = kapellmeisterStorageService.getGroup(operationGroupId);
        if (!operationGroup.getStatus().isAvailableForProcess()) {
            log.warn("Группа операций {} не доступна для выполнения, текущий статус {}", operationGroup.getId(), operationGroup.getStatus());
            return Optional.empty();
        }
        log.info("Группа {} содержит операции: {}", operationGroup.getId(), operationGroup.getOperations().stream().map(Operation::getId).collect(Collectors.toList()));

        List<Operation> criticalOperations = operationGroup.getOperations().stream()
                .filter(operation -> operation.getImportanceType() == OperationImportanceType.CRITICAL)
                .filter(operation -> !OperationStatus.COMPLETED.contains(operation.getStatus()) || operationGroup.getStatus() == OperationGroupStatus.ROLLBACK_IN_PROGRESS)
                .map(kapellmeisterStorageService::setPreviousOperation)
                .collect(Collectors.toList());

        Optional<Operation> readyOperation = Optional.of(criticalOperations)
                .filter(operationEntities -> !operationEntities.isEmpty())
                .orElse(operationGroup.getOperations()).stream()
                .filter(operationFilter)
                .filter(this::isReady)
                .min(Comparator.comparingInt(Operation::getPriority));
        readyOperation.ifPresentOrElse(
                operation -> log.info("Из группы {} выбрана операция для выполнения {}", operationGroup.getId(), operation),
                () -> log.info("В группе {} не найдено доступных для выполнения операций", operationGroup.getId())
        );
        return readyOperation.map(Operation::getId);
    }

    @Override
    public Set<UUID> getUncompletedGroupIds() {
        return kapellmeisterStorageService.getUncompleted().stream()
                .map(OperationGroup::getId)
                .collect(Collectors.toSet());
    }

    private boolean isReady(Operation operation) {
        return ((isFirstExecution(operation)
                 || isRetryAvailable(operation)
                 || isVerificationAvailable(operation)
                 || isAsyncRetryAvailable(operation)
                ) && allPreviousOperationsCompleted(operation))
               || (isRollbackAvailable(operation) && isNextOperationsRollback(operation));
    }

    private boolean isFirstExecution(Operation operation) {
        return operation.getAttemptCount() == 0 && operation.getStatus() == OperationStatus.CREATED;
    }

    private boolean isRetryAvailable(Operation operation) {
        return operation.getStatus().isExecutable()
               && isRetryPeriodPassed(operation);
    }

    private boolean isRetryPeriodPassed(Operation operation) {
        return timeService.isRetryPeriodPassed(operation.getLastExecutionTimeStamp(), operation.getRetryDelay());
    }

    private boolean isVerificationAvailable(Operation operation) {
        return operation.getStatus().isVerifiable()
               && timeService.isWaitingPeriodBeforeVerificationPeriodPassed(operation.getLastExecutionTimeStamp(), operation.getRetryDelay());
    }

    private boolean isAsyncRetryAvailable(Operation operation) {
        return operation.getStatus().isWaitResponse()
               && timeService.isResponseWaitingExpired(operation.getLastExecutionTimeStamp(), Optional.ofNullable(operation.getWaitResponseTimeout()).orElse(60000L))
               && isRetryPeriodPassed(operation);
    }

    private boolean isRollbackAvailable(Operation operation) {
        return (operation.getStatus().isFailed() || operation.getStatus() == OperationStatus.SUCCESS)
               && operation.getRollbackType() != RollbackType.UNSUPPORTED;
    }

    private boolean isNextOperationsRollback(Operation operation) {
        return kapellmeisterStorageService.findNextOperations(operation).stream()
                .allMatch(nextOperation -> nextOperation.getStatus() == OperationStatus.ROLLBACK_SUCCESS
                                           || nextOperation.getStatus() == OperationStatus.CREATED);
    }

    private boolean allPreviousOperationsCompleted(Operation operation) {
        return operation.getPrevious().stream()
                .allMatch(previousOperation -> previousOperation.getStatus().isCompleted() || previousOperation.getStatus().isSuspended());
    }
}
