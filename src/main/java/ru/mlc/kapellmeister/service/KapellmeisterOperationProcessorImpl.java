package ru.mlc.kapellmeister.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.transaction.annotation.Transactional;
import ru.mlc.kapellmeister.api.KapellmeisterOperationProcessor;
import ru.mlc.kapellmeister.api.KapellmeisterStorageService;
import ru.mlc.kapellmeister.api.OperationExecutor;
import ru.mlc.kapellmeister.api.VerifiableOperationExecutor;
import ru.mlc.kapellmeister.constants.OperationExecutionResult;
import ru.mlc.kapellmeister.constants.OperationFailReason;
import ru.mlc.kapellmeister.constants.OperationImportanceType;
import ru.mlc.kapellmeister.constants.OperationStatus;
import ru.mlc.kapellmeister.db.Operation;
import ru.mlc.kapellmeister.exceptions.ExecutorNotFoundException;
import ru.mlc.kapellmeister.exceptions.KapellmeisterUnsupportedOperationException;
import ru.mlc.kapellmeister.exceptions.UnavailableUpdateOperationStateException;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.UUID;

import static ru.mlc.kapellmeister.constants.OperationFailReason.EXECUTION_ERROR;
import static ru.mlc.kapellmeister.constants.OperationFailReason.EXECUTOR_NOT_SUPPORTS_VERIFICATION;
import static ru.mlc.kapellmeister.constants.OperationFailReason.VERIFICATION_ERROR;
import static ru.mlc.kapellmeister.constants.OperationFailReason.VERIFICATION_FAILED;
import static ru.mlc.kapellmeister.constants.OperationStatus.EXPIRED;
import static ru.mlc.kapellmeister.constants.OperationStatus.FAILED;
import static ru.mlc.kapellmeister.constants.OperationStatus.ROLLBACK_FAILED;
import static ru.mlc.kapellmeister.constants.OperationStatus.ROLLBACK_IN_WORK;
import static ru.mlc.kapellmeister.constants.OperationStatus.SUCCESS;
import static ru.mlc.kapellmeister.constants.OperationStatus.VERIFICATION;

@Slf4j
@RequiredArgsConstructor
public class KapellmeisterOperationProcessorImpl implements KapellmeisterOperationProcessor {

    private final KapellmeisterOperationExecutorsService operationExecutorsService;
    private final KapellmeisterStorageService storageService;
    private final KapellmeisterTimeSynchronizationService timeService;
    private final KapellmeisterGroupContextHolder kapellmeisterGroupContextHolder;

    @Transactional
    public OperationExecutionResult processOperation(UUID operationId) {
        try {
            Operation operation = storageService.getOperation(operationId);
            kapellmeisterGroupContextHolder.getContext().setCurrentExecutionOperationId(operationId);
            return processExecutorTask(operation);
        } catch (Exception e) {
            handleExecutionException(operationId, e);
            return OperationExecutionResult.FAIL;
        }
    }

    @Override
    @Transactional
    public void saveResult(UUID operationId, OperationExecutionResult executionResult) {
        Operation operation = storageService.getOperation(operationId);
        if (!operation.getType().isAsync() || operation.getStatus() != OperationStatus.WAIT_RESPONSE) {
            throw new UnavailableUpdateOperationStateException("Не допускается обновлять состояние операции с типом " + operation.getType() + " и статусом " + operation.getStatus());
        } else {
            OperationExecutor<?> executor = getExecutor(operation.getExecutorName());
            if (isWaitingResponseTimeoutReached(operation)) {
                failOperationExecutionAttempt(operation, executor, OperationFailReason.RESPONSE_WAIT_TIMEOUT_REACHED, null);
            } else if (executionResult == OperationExecutionResult.SUCCESS) {
                markAsExecuted(operation);
            } else if (executionResult == OperationExecutionResult.ATTEMPT_FAILED) {
                failOperationExecutionAttempt(operation, executor, OperationFailReason.FAILED_EXECUTION, null);
            } else if (executionResult == OperationExecutionResult.FAIL) {
                failOperation(operation, OperationFailReason.FAILED_EXECUTION, null);
            } else {
                throw new KapellmeisterUnsupportedOperationException("Unsupported " + OperationExecutionResult.class.getSimpleName() + " " + executionResult);
            }
        }
    }

    private OperationExecutionResult processExecutorTask(Operation operation) {
        OperationExecutor<?> executor = getExecutor(operation.getExecutorName());
        log.info("Операция {} взята в работу", operation);
        if (operation.getStatus() == ROLLBACK_IN_WORK) {
            processRollback(operation, executor);
        } else if (operation.getDeadlineTimestamp() != null && timeService.isOperationDeadlineReached(operation.getDeadlineTimestamp())) {
            failOperation(operation, OperationFailReason.DEADLINE_REACHED, null);
        } else if (isWaitingResponseTimeoutReached(operation)) {
            failOperationExecutionAttempt(operation, executor, OperationFailReason.RESPONSE_WAIT_TIMEOUT_REACHED, null);
            storageService.update(operation);
        } else if (operation.getPrevious().stream().anyMatch(previous -> previous.getStatus().isSuspended())) {
            failOperation(operation, OperationFailReason.PREVIOUS_RESERVED, null);
        } else {
            if (operation.getStatus().isVerifiable()) {
                processVerification(operation, executor);
            } else {
                processAttempt(operation, executor);
            }
        }

        log.info("Результат выполнения операции {} : {}", operation.getId(), operation.getExecutionResult());
        return operation.getExecutionResult();
    }

    private <T> OperationExecutionResult processRollback(Operation operation, OperationExecutor<T> executor) {
        T params = executor.deserializeParams(operation.getParams());
        OperationExecutionResult result = executor.rollback(params);
        log.info("Операция отката {} ({}) выполнена штатно с результатом {}",
                operation.getId(), operation.getDescription(), result);
        if (result == OperationExecutionResult.ROLLBACK_FAIL) {
            failOperation(operation, OperationFailReason.FAILED_EXECUTION, null);
        } else {
            throw new KapellmeisterUnsupportedOperationException("Unsupported " + OperationExecutionResult.class.getSimpleName() + " " + result);
        }
        return result;
    }

    public void handleExecutionException(UUID operationId, Exception operationExecutionException) {
        log.warn("При выполнении операции {} произошла ошибка. Попытка провалена.", operationId, operationExecutionException);
        Operation operation = storageService.getOperation(operationId);
        if (operation.getStatus().isExecutable()) {
            operation.setAttemptCount(operation.getAttemptCount() + 1);
        }
        findExecutor(operation.getExecutorName())
                .ifPresentOrElse(executor -> {
                    OperationFailReason reason = operation.getStatus().isExecutable() ? EXECUTION_ERROR : VERIFICATION_ERROR;
                    if (executor.canRetry(operationExecutionException)) {
                        failOperationExecutionAttempt(operation, executor, reason, operationExecutionException);
                    } else {
                        failOperation(operation, reason, operationExecutionException);
                    }
                }, () -> failOperation(operation, OperationFailReason.EXECUTOR_NOT_FOUND, operationExecutionException));
    }

    private <T> void processVerification(Operation operation, OperationExecutor<T> executor) {
        log.info("Начата проверка результата выполнения задачи {}", operation.getId());
        resolveVerifier(executor)
                .ifPresentOrElse(verifier -> {
                            T params = verifier.deserializeParams(operation.getParams());
                            OperationExecutionResult result = verifier.verify(params);
                            log.info("Проверка выполнения задачи {} произведена штатно с результатом {}", operation.getId(), result);
                            if (result == OperationExecutionResult.SUCCESS) {
                                markAsVerificationSuccess(operation);
                            } else if (result == OperationExecutionResult.ATTEMPT_FAILED) {
                                failOperationExecutionAttempt(operation, verifier, VERIFICATION_FAILED, null);
                            } else if (result == OperationExecutionResult.FAIL) {
                                failOperation(operation, VERIFICATION_FAILED, null);
                            } else {
                                throw new KapellmeisterUnsupportedOperationException("Unsupported " + OperationExecutionResult.class.getSimpleName() + " " + result);
                            }
                        }, () -> failOperation(operation, EXECUTOR_NOT_SUPPORTS_VERIFICATION, null)
                );
    }

    private <T> void processAttempt(Operation operation, OperationExecutor<T> executor) {
        log.info("Инициировано выполнение работы по задаче {}", operation.getId());
        if (operation.getAttemptCount() < operation.getMaxAttemptCount()) {
            operation.setAttemptCount(operation.getAttemptCount() + 1);
            operation.setLastExecutionTimeStamp(timeService.now());
            OperationExecutionResult executionResult = execute(executor, operation);
            log.info("Операция {} ({}) выполнена штатно с результатом {}",
                    operation.getId(), operation.getDescription(), executionResult);
            if (executionResult == OperationExecutionResult.SUCCESS) {
                if (operation.getType().isAsync()) {
                    markAsWaitingResponse(operation);
                } else {
                    resolveVerifier(executor)
                            .ifPresentOrElse(
                                    verifier -> markAsOnVerification(operation),
                                    () -> markAsExecuted(operation)
                            );
                }
            } else if (executionResult == OperationExecutionResult.ATTEMPT_FAILED) {
                failOperationExecutionAttempt(operation, executor, OperationFailReason.FAILED_EXECUTION, null);
            } else if (executionResult == OperationExecutionResult.FAIL) {
                failOperation(operation, OperationFailReason.FAILED_EXECUTION, null);
            } else {
                throw new KapellmeisterUnsupportedOperationException("Unsupported " + OperationExecutionResult.class.getSimpleName() + " " + executionResult);
            }
        } else {
            processAttemptIsOver(operation, OperationFailReason.FAILED_EXECUTION, null);
        }
    }

    private <T> Optional<VerifiableOperationExecutor<T>> resolveVerifier(OperationExecutor<T> executor) {
        if (executor instanceof VerifiableOperationExecutor) {
            return Optional.of((VerifiableOperationExecutor<T>) executor);
        } else {
            return Optional.empty();
        }
    }

    private <T> OperationExecutionResult execute(OperationExecutor<T> executor, Operation operation) {
        T params = executor.deserializeParams(operation.getParams());
        OperationExecutionResult preconditionResult = executor.checkPrecondition(params).orElse(OperationExecutionResult.ATTEMPT_FAILED);
        if (preconditionResult == OperationExecutionResult.SUCCESS) {
            log.warn("Операция {} ({}) ранее выполнена успешно.", operation.getId(), operation.getDescription());
            return preconditionResult;
        } else if (preconditionResult == OperationExecutionResult.FAIL) {
            log.warn("Операция {} ({}) ранее выполнена с ошибкой.", operation.getId(), operation.getDescription());
            return preconditionResult;
        } else if (preconditionResult == OperationExecutionResult.ATTEMPT_FAILED) {
            log.info("Все предусловия по задаче {} проверены, инициируется вызов executor {} с параметрами {}", operation.getId(), executor.getName(), params);
            return executor.execute(params);
        } else {
            throw new KapellmeisterUnsupportedOperationException("Unsupported " + OperationExecutionResult.class.getSimpleName() + " " + preconditionResult);
        }
    }

    private void failOperationExecutionAttempt(Operation operation,
                                               OperationExecutor<?> executor,
                                               OperationFailReason reason,
                                               @Nullable Exception exception) {
        executor.getNewRetryConfig(operation.toRetryConfigBuilder(), operation).ifPresent(retryConfig -> {
            log.info("Для операции {} ({}) изменены настройки переотправки {}", operation.getId(), operation.getDescription(), retryConfig);
            operation.apply(retryConfig);
        });

        if (operation.getMaxAttemptCount() <= operation.getAttemptCount()) {
            processAttemptIsOver(operation, reason, exception);
        } else {
            log.warn("Провалилась попытка выполнить операцию {}, cause: {}", operation.getId(), reason.getDescription());
            changeOperationStatus(operation, OperationStatus.CAN_RETRY);
            operation.setExecutionResult(OperationExecutionResult.ATTEMPT_FAILED);
            operation.setComment(getFailComment("Провалена попытка выполнения операции.", reason, exception));
            storageService.update(operation);
        }
    }

    private void markAsOnVerification(Operation operation) {
        log.info("Работа по задаче {} выполнена, инициирована отложенная проверки результат", operation.getId());
        operation.setExecutionResult(OperationExecutionResult.SUCCESS);
        changeOperationStatus(operation, VERIFICATION);
        storageService.update(operation);
    }

    private boolean isWaitingResponseTimeoutReached(Operation operation) {
        return operation.getStatus().isWaitResponse()
               && timeService.isResponseWaitingExpired(operation.getLastExecutionTimeStamp(), operation.getWaitResponseTimeout());
    }

    private void processAttemptIsOver(Operation operation, OperationFailReason reason, @Nullable Exception exception) {
        failOperation(operation, reason, exception);
    }

    private void markAsVerificationSuccess(Operation operation) {
        changeOperationStatus(operation, SUCCESS);
        storageService.update(operation);
    }

    private void markAsExecuted(Operation operation) {
        operation.setExecutionResult(OperationExecutionResult.SUCCESS);
        changeOperationStatus(operation, SUCCESS);
        storageService.update(operation);
    }

    private void markAsWaitingResponse(Operation operation) {
        changeOperationStatus(operation, OperationStatus.WAIT_RESPONSE);
        operation.setExecutionResult(OperationExecutionResult.SUCCESS);
        storageService.update(operation);
    }

    private String getFailComment(String message, OperationFailReason reason, Exception exception) {
        return message + String.format(" Причина: %s\n Ошибка: %s",
                reason.getDescription(),
                Optional.ofNullable(exception).map(ExceptionUtils::getStackTrace).orElse(null));
    }

    private void failOperation(Operation operation, OperationFailReason reason, @Nullable Exception e) {
        log.error("Не удалось выполнить операцию {}, cause: {} {}", operation.getId(), reason.name(), reason.getDescription(), e);
        operation.setComment(getFailComment("Не удалось выполнить операцию.", reason, e));
        if (operation.getStatus() == ROLLBACK_IN_WORK) {
            changeOperationStatus(operation, ROLLBACK_FAILED);
        } else if (reason == OperationFailReason.RESPONSE_WAIT_TIMEOUT_REACHED) {
            operation.setExecutionResult(OperationExecutionResult.FAIL);
        }
        if (operation.getImportanceType() == OperationImportanceType.CRITICAL) {
            if (reason == OperationFailReason.DEADLINE_REACHED) {
                changeOperationStatus(operation, EXPIRED);
            } else {
                changeOperationStatus(operation, FAILED);
            }
        } else if (operation.getImportanceType() == OperationImportanceType.REQUIRED) {
            changeOperationStatus(operation, OperationStatus.RESERVED);
        } else if (operation.getImportanceType() == OperationImportanceType.OPTIONAL) {
            changeOperationStatus(operation, OperationStatus.SKIPPED);
        } else {
            throw new KapellmeisterUnsupportedOperationException("Unsupported " + OperationImportanceType.class.getSimpleName() + " " + operation.getImportanceType());
        }
        storageService.update(operation);
    }

    private static void changeOperationStatus(Operation operation, OperationStatus targetStatus) {
        log.info("Изменение статуса операции {} {} -> {}", operation.getId(), operation.getStatus(), targetStatus);
        operation.setStatus(targetStatus);
    }

    private OperationExecutor<?> getExecutor(String executorName) {
        return findExecutor(executorName)
                .orElseThrow(() -> new ExecutorNotFoundException(executorName));
    }

    private Optional<? extends OperationExecutor<?>> findExecutor(String executorName) {
        return operationExecutorsService.getExecutors().stream()
                .filter(executor -> executor.getName().equals(executorName))
                .findFirst();
    }
}
