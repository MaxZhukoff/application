package ru.mlc.kapellmeister.api;

import ru.mlc.kapellmeister.constants.OperationImportanceType;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public interface OperationConfig<T> {

    OperationExecutor<T> getExecutor();

    OperationImportanceType getImportanceType();

    T getParams();

    UUID getRelatedEntityId();

    Set<UUID> getPreviousOperationIds();

    Integer getPriority();

    Integer getMaxAttemptCount();

    Long getRetryDelay();

    Long getWaitResponseTimeout();

    Instant getDeadlineTimestamp();

    Boolean getExecuteAfterCommit();

    String getDescription();

    Boolean getOptimized();
}
