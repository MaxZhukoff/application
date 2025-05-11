package ru.mlc.kapellmeister.constants;

public enum OperationExecutionResult {

    /**
     * Операция выполнена успешно
     */
    SUCCESS,

    /**
     * Попытка провалилась, но возможен ретрай
     */
    ATTEMPT_FAILED,

    /**
     * Произошла ошибка, ретрай невозможен
     */
    FAIL,

    /**
     * Откат операции выполнен успешно
     */
    ROLLBACK_SUCCESS,

    /**
     * Произошла ошибка при откате
     */
    ROLLBACK_FAIL,
}
