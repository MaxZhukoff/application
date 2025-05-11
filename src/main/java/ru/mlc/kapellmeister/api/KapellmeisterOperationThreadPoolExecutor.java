package ru.mlc.kapellmeister.api;

import ru.mlc.kapellmeister.constants.OperationExecutionResult;

import java.util.UUID;

public interface KapellmeisterOperationThreadPoolExecutor {

    /**
     * Добавить в пул потоков одну операцию в выполнение
     *
     * @param operationId идентификатор операции
     */
    void submitOperation(UUID operationId);

    void waitExecution();

    /**
     * Синхронно выполнить одну операцию
     *
     * @param operationId идентификатор операции
     */
    void processOperation(UUID operationId);

    /**
     * Сохранение результата выполнения асинхронной операции
     *
     * @param operationId     идентификатор операции
     * @param executionResult результат выполнения
     */
    void saveResult(UUID operationId, OperationExecutionResult executionResult);
}
