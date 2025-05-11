package ru.mlc.kapellmeister.api;

import ru.mlc.kapellmeister.constants.OperationExecutionResult;

import java.util.UUID;

public interface KapellmeisterOperationProcessor {

    /**
     * Выполнить одну операцию
     *
     * @param operationId идентифкатор операции
     */
    OperationExecutionResult processOperation(UUID operationId);

    /**
     * Сохранение результата выполнения асинхронной операции
     *
     * @param operationId     идентификатор операции
     * @param executionResult результат выполнения
     */
    void saveResult(UUID operationId, OperationExecutionResult executionResult);
}
