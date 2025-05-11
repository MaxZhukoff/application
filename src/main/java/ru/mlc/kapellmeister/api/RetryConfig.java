package ru.mlc.kapellmeister.api;

import java.time.Instant;

public interface RetryConfig {

    /**
     * Приоритет выполнения операции, определяет очередность выполнения.
     * Чем меньше ниже число, тем выше приоритет.
     * Допустимые значения от 0 до 10
     */
    Integer getPriority();

    /**
     * Максимальное количество попыток выполнить операцию
     */
    Integer getMaxAttemptCount();

    /**
     * Период в миллисекундах между попытками выполнения операции
     */
    Long getRetryDelay();

    /**
     * Время ожидания ответа в миллисекундах для асинхронных операций
     */
    Long getWaitResponseTimeout();

    /**
     * Дэдлайн выполнения операции, моментдо которого необзодимо выполнить операцию
     */
    Instant getDeadlineTimestamp();

    default SimpleRetryConfig.SimpleRetryConfigBuilder toRetryConfigBuilder() {
        return SimpleRetryConfig.builder()
                .priority(this.getPriority())
                .maxAttemptCount(this.getMaxAttemptCount())
                .retryDelay(this.getRetryDelay())
                .waitResponseTimeout(this.getWaitResponseTimeout())
                .deadlineTimestamp(this.getDeadlineTimestamp());
    }
}
