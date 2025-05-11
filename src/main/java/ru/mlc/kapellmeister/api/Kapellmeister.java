package ru.mlc.kapellmeister.api;

import java.util.Collection;
import java.util.UUID;

/**
 * Инетрфейс для создания задач
 */
public interface Kapellmeister {

    /**
     * Добавляет операцию в очередь выполнения
     *
     * @param config параметры операции
     * @param <T>    тип параметров выполнения операции
     * @return информация о созданной операции
     */
    <T> OperationState addToQueue(OperationConfig<T> config);

    /**
     * Метод для предоставления апи формирования задачи в стиле билдера
     *
     * @param executor экзэкутор, для выполнения операции
     * @param <T>      тип параметров выполнения операции
     * @return конфигуратор операции
     */
    <T> OperationBuilder<T> use(OperationExecutor<T> executor);

    /**
     * Добавляет в описание текущей группы операций строку
     */
    void addDescription(String message);

    Collection<? extends OperationState> findOperationInCurrentGroup(OperationExecutor<?> executor);

    Collection<? extends OperationState> findOperationInGroup(OperationExecutor<?> executor, UUID groupId);
}
