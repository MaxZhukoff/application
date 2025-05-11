package ru.mlc.kapellmeister.constants;

import java.util.Set;

public enum OperationStatus {

    /**
     * Создана, еще не бралась в работу
     */
    CREATED,
    /**
     * Ожидает получения ответа (актуально для {@link OperationType#ASYNC_REQUEST}
     */
    WAIT_RESPONSE,
    /**
     * Последняя попытка была провалена, возможен ретрая
     */
    CAN_RETRY,
    /**
     * Требуется проверка результат выполнения задачи
     */
    VERIFICATION,
    /**
     * Выполнено успешно
     */
    SUCCESS,
    /**
     * Выполнение провалено, но не является причиной для провала все группы (актуально для {@link OperationImportanceType#OPTIONAL}
     */
    SKIPPED,
    /**
     * Операция отменена
     */
    REJECTED,
    /**
     * Операция не исполнена, но не является критичной в моменте, не является причиной фэилить группу, но блокирует выполнение следующих операций в очереди
     */
    RESERVED,
    /**
     * Операция провалена, и это критично для группы
     */
    FAILED,
    /**
     * Операция не выполнена за отведенное время
     */
    EXPIRED,
    /**
     * Операция в процессе выполнения
     */
    IN_WORK,
    /**
     * Операция с проверкой результата в процессе выполнения
     */
    VERIFICATION_IN_WORK,
    /**
     * Операция в процессе отката
     */
    ROLLBACK_IN_WORK,
    /**
     * Операция в процессе отката
     */
    ROLLBACK_SUCCESS,
    /**
     * В процессе отката операции произошла ошибка
     */
    ROLLBACK_FAILED;

    public static final Set<OperationStatus> COMPLETED = Set.of(SUCCESS, SKIPPED, REJECTED);
    public static final Set<OperationStatus> FATAL = Set.of(FAILED, EXPIRED);
    public static final Set<OperationStatus> SUSPENDED = Set.of(RESERVED);
    public static final Set<OperationStatus> IN_PROGRESS = Set.of(CAN_RETRY, CREATED, WAIT_RESPONSE, VERIFICATION);
    public static final Set<OperationStatus> WAIT_ASYNC_OPERATION = Set.of(WAIT_RESPONSE);
    public static final Set<OperationStatus> EXECUTABLE = Set.of(CAN_RETRY, CREATED);

    public boolean isFailed() {
        return FATAL.contains(this);
    }

    public boolean isCompleted() {
        return COMPLETED.contains(this);
    }
    public boolean isSuspended() {
        return SUSPENDED.contains(this);
    }

    public boolean isExecutable() {
        return EXECUTABLE.contains(this);
    }

    public boolean isVerifiable() {
        return VERIFICATION == this || VERIFICATION_IN_WORK == this;
    }

    public boolean isWaitResponse() {
        return WAIT_ASYNC_OPERATION.contains(this);
    }
}
