package ru.mlc.kapellmeister.exceptions;

import lombok.Getter;

import java.util.UUID;

/**
 * Транзакция в рамках которой выполняется операция подлежит откату
 */
public class OperationExecutionRollbackTransactionException extends KapellmeisterException {

    @Getter
    private final UUID operationId;

    public OperationExecutionRollbackTransactionException(UUID operationId, Throwable cause) {
        super("Ошибка при выполнении операции " + operationId, cause);
        this.operationId = operationId;
    }
}
