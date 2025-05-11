package ru.mlc.kapellmeister.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.mlc.kapellmeister.api.Kapellmeister;
import ru.mlc.kapellmeister.api.KapellmeisterStorageService;
import ru.mlc.kapellmeister.api.OperationBuilder;
import ru.mlc.kapellmeister.api.OperationConfig;
import ru.mlc.kapellmeister.api.OperationExecutor;
import ru.mlc.kapellmeister.api.OperationState;
import ru.mlc.kapellmeister.api.SimpleOperationConfig;
import ru.mlc.kapellmeister.configuration.KapellmeisterConfigurationProperties;
import ru.mlc.kapellmeister.constants.OperationImportanceType;
import ru.mlc.kapellmeister.constants.OperationPriority;
import ru.mlc.kapellmeister.constants.OperationStatus;
import ru.mlc.kapellmeister.db.Operation;
import ru.mlc.kapellmeister.db.OperationGroup;
import ru.mlc.kapellmeister.exceptions.OperationConfigValidationException;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
public class KapellmeisterImpl implements Kapellmeister {

    private final KapellmeisterConfigurationProperties properties;
    private final KapellmeisterGroupContextHolder kapellmeisterGroupContextHolder;
    private final KapellmeisterStorageService kapellmeisterStorageService;
    private final OperationValidator operationValidator;
    private final KapellmeisterAfterCommitService kapellmeisterAfterCommitService;

    @Lazy
    @Autowired
    private Kapellmeister kapellmeister;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public <T> OperationState addToQueue(OperationConfig<T> config) {
        operationValidator.validate(config);
        String serializedParams = config.getExecutor().serializeParams(config.getParams());
        if (properties.getOptimizationEnabled() && config.getOptimized()) {
            return getContext().getOptimizedCache().find(config.getExecutor().getName(), serializedParams)
                    .map(operation -> {
                        if (isEqualConfig(operation, config)) {
                            log.info("В очереди выполнения уже есть операция {} с параметрами {}. Новая операция создана не будет.", config.getExecutor().getName(), serializedParams);
                            return operation;
                        } else {
                            throw new OperationConfigValidationException("Конфликт конфигурации при оптимизации операции для экзекутора " + operation.getExecutorName());
                        }
                    })
                    .orElseGet(() -> {
                        Operation operation = newOperation(config, serializedParams);
                        getContext().getOptimizedCache().put(config.getExecutor().getName(), serializedParams, operation);
                        return operation;
                    });
        } else {
            return newOperation(config, serializedParams);
        }
    }

    private <T> Operation newOperation(OperationConfig<T> config, String serializedParams) {
        OperationGroup currentOperationGroup = getCurrentOperationGroup();
        List<Operation> previousOperations = kapellmeisterStorageService.findAllRequired(config.getPreviousOperationIds());
        Operation operation = Operation.builder()
                .groupId(currentOperationGroup.getId())
                .executorName(config.getExecutor().getName())
                .type(config.getExecutor().getOperationType())
                .rollbackType(config.getExecutor().getRollbackType())
                .importanceType(config.getImportanceType())
                .params(serializedParams)
                .previous(previousOperations)
                .priority(config.getPriority())
                .retryDelay(config.getRetryDelay())
                .maxAttemptCount(config.getMaxAttemptCount())
                .attemptCount(0)
                .relatedEntityId(config.getRelatedEntityId())
                .description(config.getDescription())
                .deadlineTimestamp(config.getDeadlineTimestamp())
                .waitResponseTimeout(config.getWaitResponseTimeout())
                .status(OperationStatus.CREATED)
                .build();
        operation = kapellmeisterStorageService.save(operation);
        UUID operationId = operation.getId();
        currentOperationGroup.getOperations().add(operation);
        operationValidator.validateOrder(currentOperationGroup);
        if (Boolean.TRUE.equals(config.getExecuteAfterCommit())) {
            getContext().getOperationsForExecuteAfterCommit().add(operationId);
            triggerDeferredExecutor();
        }
        log.info("В очередь выполнения добавлена операция {}", operation);
        return operation;
    }

    /**
     * Инициирует выполнение операций после коммита транзакции
     */
    private void triggerDeferredExecutor() {
        synchronized (getContext().getTransactionId()) {
            if (!getContext().getExecutionAfterCommitWasTriggerred().get()) {
                KapellmeisterGroupContextHolder.Context context = getContext();
                context.getExecutionAfterCommitWasTriggerred().set(true);
                kapellmeisterAfterCommitService.some(context.getTransactionId(), context.getOperationsForExecuteAfterCommit());
            }
        }
    }

    private OperationGroup getCurrentOperationGroup() {
        KapellmeisterGroupContextHolder.Context context = getContext();
        return kapellmeisterStorageService.findGroup(context.getTransactionId())
                .orElseGet(() -> kapellmeisterStorageService.createGroup(
                        context.getTransactionId(),
                        context.getMethodName(),
                        context.getCurrentExecutionOperationId().orElse(null))
                );
    }

    private KapellmeisterGroupContextHolder.Context getContext() {
        return kapellmeisterGroupContextHolder.getContext();
    }

    @Override
    public <T> OperationBuilder<T> use(OperationExecutor<T> executor) {
        return OperationBuilder.<T>builder()
                .configBuilder(defaultConfig(executor))
                .kapellmeister(kapellmeister)
                .build();
    }

    @Override
    public void addDescription(String message) {
        OperationGroup group = getCurrentOperationGroup();
        String description = group.getDescription();
        group.setDescription(String.join(";\n", description, message));
    }

    @Override
    public Collection<? extends OperationState> findOperationInCurrentGroup(OperationExecutor<?> executor) {
        return kapellmeisterStorageService.findOperations(executor.getName(), getCurrentOperationGroup().getId());
    }

    @Override
    public Collection<? extends OperationState> findOperationInGroup(OperationExecutor<?> executor, UUID groupId) {
        return kapellmeisterStorageService.findOperations(executor.getName(), groupId);
    }

    private <T> SimpleOperationConfig.SimpleOperationConfigBuilder<T> defaultConfig(OperationExecutor<T> executor) {
        return SimpleOperationConfig.<T>builder()
                .importanceType(OperationImportanceType.CRITICAL)
                .maxAttemptCount(1)
                .priority(OperationPriority.DEFAULT.getPriority())
                .retryDelay(5000L)
                .executeAfterCommit(false)
                .executor(executor)
                .optimized(false);
    }

    public boolean isEqualConfig(Operation operation, OperationConfig<?> config) {
        return Objects.equals(operation.getImportanceType(), config.getImportanceType())
               && Objects.equals(operation.getPriority(), config.getPriority())
               && Objects.equals(operation.getMaxAttemptCount(), config.getMaxAttemptCount())
               && Objects.equals(operation.getRetryDelay(), config.getRetryDelay())
               && Objects.equals(operation.getWaitResponseTimeout(), config.getWaitResponseTimeout());
    }

}
