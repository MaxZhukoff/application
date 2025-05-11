package ru.mlc.kapellmeister.constants;

import java.util.Arrays;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

public enum OperationGroupStatus {

    /**
     * Еще не было попыток выполнить операции из группы
     */
    CREATED,
    /**
     * Не удалось выполнить критически важные операции
     */
    FAILED,
    /**
     * Группа операций успешно завершена
     */
    COMPLETED,
    /**
     * Все критичные операции исполнены, но есть не завершенные обязательные
     */
    CONDITIONALLY_COMPLETED,
    /**
     * Группа операций находится в процессе выполнения
     */
    IN_PROGRESS,
    /**
     * Группа операций находится в процессе отката
     */
    ROLLBACK_IN_PROGRESS,
    /**
     * Группа операций находится в процессе отката
     */
    ROLLBACK_COMPLETED,
    /**
     * Произошла нештатная техническая ошибка
     */
    ERROR;

    public static final Set<OperationGroupStatus> AVAILABLE_FOR_PROCESS = Set.of(CREATED, IN_PROGRESS, ROLLBACK_IN_PROGRESS);
    public static final Set<OperationGroupStatus> UNCOMPLETED = exclude(Set.of(COMPLETED));

    public boolean isAvailableForProcess() {
        return AVAILABLE_FOR_PROCESS.contains(this);
    }

    private static Set<OperationGroupStatus> exclude(Collection<OperationGroupStatus> exclusion) {
        return exclude(Arrays.asList(values()), exclusion);
    }

    private static <T> Set<T> exclude(Collection<T> source, Collection<T> exclusion) {
        return source.stream()
                .filter(element -> !exclusion.contains(element))
                .collect(Collectors.toSet());
    }
}
