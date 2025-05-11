package ru.mlc.kapellmeister.api;

import lombok.Builder;
import ru.mlc.kapellmeister.constants.OperationImportanceType;
import ru.mlc.kapellmeister.constants.OperationPriority;
import ru.mlc.kapellmeister.constants.RollbackType;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Builder
public class OperationBuilder<T> {

    private final SimpleOperationConfig.SimpleOperationConfigBuilder<T> configBuilder;
    private final Kapellmeister kapellmeister;

    public OperationBuilder<T> after(Set<UUID> previousOperationIds) {
        configBuilder.previousOperationIds(previousOperationIds);
        return this;
    }

    public OperationBuilder<T> after(UUID previousOperationId) {
        configBuilder.previousOperationId(previousOperationId);
        return this;
    }

    public OperationBuilder<T> params(T params) {
        configBuilder.params(params);
        return this;
    }

    public OperationBuilder<T> priority(OperationPriority priority) {
        configBuilder.priority(priority.getPriority());
        return this;
    }

    public OperationBuilder<T> retryDelay(long milliseconds) {
        configBuilder.retryDelay(milliseconds);
        return this;
    }

    public OperationBuilder<T> maxAttemptCount(int maxAttempts) {
        configBuilder.maxAttemptCount(maxAttempts);
        return this;
    }

    public OperationBuilder<T> description(String description) {
        configBuilder.description(description);
        return this;
    }

    public OperationBuilder<T> relatedEntityId(UUID relatedEntityId) {
        configBuilder.relatedEntityId(relatedEntityId);
        return this;
    }

    public OperationBuilder<T> waitResponseTimeout(long waitResponseTimeout) {
        configBuilder.waitResponseTimeout(waitResponseTimeout);
        return this;
    }

    public OperationBuilder<T> importanceType(OperationImportanceType importanceType) {
        configBuilder.importanceType(importanceType);
        return this;
    }

    public OperationBuilder<T> deadLine(Instant deadLine) {
        configBuilder.deadlineTimestamp(deadLine);
        return this;
    }

    public OperationBuilder<T> rollbackType(RollbackType rollbackType) {
        configBuilder.rollbackType(rollbackType);
        return this;
    }

    public OperationBuilder<T> optimized() {
        return optimized(true);
    }

    public OperationBuilder<T> optimized(boolean optimized) {
        configBuilder.optimized(optimized);
        return this;
    }

    public OperationState executeAfterCommit() {
        configBuilder.executeAfterCommit(true);
        return addToQueue();
    }

    public OperationState addToQueue() {
        return kapellmeister.addToQueue(configBuilder.build());
    }
}
