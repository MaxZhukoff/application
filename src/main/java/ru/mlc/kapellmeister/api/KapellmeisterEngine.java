package ru.mlc.kapellmeister.api;

import ru.mlc.kapellmeister.constants.OperationExecutionResult;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Движок для выполнения операций
 */
public interface KapellmeisterEngine {

    /**
     * Сохранение результата выполнения асинхронной операции
     *
     * @param operationId     идентификатор операции
     * @param executionResult результат выполнения
     */
    void saveResult(UUID operationId, OperationExecutionResult executionResult);

    /**
     * Выполнить доступные для выполнения группы операций (каждая операция в отдельной транзакции)
     * Итерация длится либо до окончания jobDeadLine либо до выполнения всех доступных операций
     *
     * @param jobDeadLine максимальное время до которой может длиться итерация
     * @param maxCountOfOperations максимальное количество операций, которые можно выполнить в одной итерации
     *
     * @return идентификаторы выполненных операций
     */
    Set<UUID> executeAvailableOperationGroups(Instant jobDeadLine, Integer maxCountOfOperations);

    Set<UUID> executeAvailableOperationGroupsSync(Instant jobDeadLine, Integer maxCountOfOperations);

    /**
     * Выполнить доступные для выполнения группы операций (каждая операция в отдельной транзакции)
     * Итерация длится либо до окончания jobDeadLine либо до выполнения всех доступных операций
     *
     * @param jobDeadLine максимальное время до которой может длиться итерация
     *
     * @return идентификаторы выполненных операций
     */
    Set<UUID> executeAvailableOperationGroups(Instant jobDeadLine);

    /**
     * Добавляет для выполнения доступные для выполнения группы операций (каждая операция в отдельной транзакции)
     *
     * @param jobDeadLine максимальное время до которой может длиться итерация
     *
     * @return идентификаторы выполняемых операций
     */
    Set<UUID> executeAvailableOperationGroupsSync(Instant jobDeadLine);

    /**
     * Выполнить операции из группы
     *
     * @param operationGroupId идентификатор группы
     * @param jobDeadLine      момент времени в который инициируется прперывание выполнения группы операций
     * @param filter           фильтр операций, которые нужно выполнить
     * @param maxCountOfOperations максимальное количество операций, которые могут быть выполнены

     * @return идентификаторы выполненных операци
     */
    Set<UUID> processOperationsOfGroup(UUID operationGroupId, Instant jobDeadLine, Predicate<OperationState> filter, Integer maxCountOfOperations);

    Set<UUID> getUncompletedGroupIds();

    /**
     * Определяет следующую операцию для выполнения в группе
     *
     * @param operationFilter фильтр отбирающий операции для выполнения
     * @param operationGroup  идентификатор группы
     * @return идентификатор операции для выполнения
     */
    Optional<UUID> resolveOperationForExecuting(Predicate<OperationState> operationFilter, UUID operationGroup);
}
