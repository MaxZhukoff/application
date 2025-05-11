package ru.mlc.kapellmeister.api;

import ru.mlc.kapellmeister.constants.OperationExecutionResult;

public interface VerifiableOperationExecutor<T> extends OperationExecutor<T> {

    /**
     * Проверяет результат выполнения операции.
     * Используется для операций, результат которых можно оценить только через некоторое то время.
     */
    OperationExecutionResult verify(T param);
}
