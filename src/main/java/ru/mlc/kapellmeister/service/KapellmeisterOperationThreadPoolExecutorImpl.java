package ru.mlc.kapellmeister.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import ru.mlc.kapellmeister.api.KapellmeisterOperationProcessor;
import ru.mlc.kapellmeister.api.KapellmeisterOperationThreadPoolExecutor;
import ru.mlc.kapellmeister.api.KapellmeisterStorageService;
import ru.mlc.kapellmeister.constants.OperationExecutionResult;
import ru.mlc.kapellmeister.constants.OperationStatus;
import ru.mlc.kapellmeister.constants.RollbackType;
import ru.mlc.kapellmeister.db.Operation;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;

import static ru.mlc.kapellmeister.constants.OperationStatus.CAN_RETRY;
import static ru.mlc.kapellmeister.constants.OperationStatus.CREATED;
import static ru.mlc.kapellmeister.constants.OperationStatus.SUCCESS;
import static ru.mlc.kapellmeister.constants.OperationStatus.VERIFICATION;

@Slf4j
@RequiredArgsConstructor
public class KapellmeisterOperationThreadPoolExecutorImpl implements KapellmeisterOperationThreadPoolExecutor {

    private final ThreadPoolExecutor threadPoolExecutor;
    private final TransactionTemplate transactionTemplate;
    private final KapellmeisterStorageService storageService;
    private final KapellmeisterOperationProcessor kapellmeisterOperationProcessor;

    @Override
    public void waitExecution() {
        while (threadPoolExecutor.getActiveCount() > 0) {
            Thread.yield();
        }
    }

    @Override
    @Transactional
    public void saveResult(UUID operationId, OperationExecutionResult executionResult) {
        kapellmeisterOperationProcessor.saveResult(operationId, executionResult);
    }

    @Override
    public void submitOperation(UUID operationId) {
        try {
            CompletableFuture.runAsync(() ->
                    processOperation(operationId), threadPoolExecutor);
            log.info("Операция добавлена в выполнение: {}", operationId);
        } catch (RejectedExecutionException e) {
            log.warn("Операция id:{} отклонена, очередь операций заполнена", operationId);
        }
    }

    @Override
    public void processOperation(UUID operationId) {
        if (tryOptimisticLock(operationId)) {
            kapellmeisterOperationProcessor.processOperation(operationId);
        } else {
            log.warn("Операция: {} уже в процессе обработки", operationId);
        }
    }

    private boolean tryOptimisticLock(UUID operationId) {
        try {
            return Boolean.TRUE.equals(transactionTemplate.execute(x -> {
                Operation operation = storageService.getOperation(operationId);
                if (operation.getStatus() == CREATED || operation.getStatus() == CAN_RETRY) {
                    changeOperationStatus(operation, OperationStatus.IN_WORK);
                    return storageService.update(operation);
                } else if (operation.getStatus() == VERIFICATION) {
                    changeOperationStatus(operation, OperationStatus.VERIFICATION_IN_WORK);
                    return storageService.update(operation);
                } else if ((operation.getStatus() == SUCCESS || operation.getStatus().isFailed()) && operation.getRollbackType() == RollbackType.ROLLBACK) {
                    changeOperationStatus(operation, OperationStatus.ROLLBACK_IN_WORK);
                    return storageService.update(operation);
                }
                return false;
            }));
        } catch (ObjectOptimisticLockingFailureException e) {
            return false;
        }
    }

    private void changeOperationStatus(Operation operation, OperationStatus targetStatus) {
        log.info("Изменение статуса операции {} {} -> {}", operation.getId(), operation.getStatus(), targetStatus);
        operation.setStatus(targetStatus);
    }
}
