package ru.mlc.kapellmeister.api;

import ru.mlc.kapellmeister.constants.OperationExecutionResult;
import ru.mlc.kapellmeister.constants.OperationStatus;
import ru.mlc.kapellmeister.constants.OperationType;

import java.time.Instant;
import java.util.UUID;

public interface OperationState extends RetryConfig {

    UUID getId();

    UUID getGroupId();

    String getExecutorName();

    String getParams();

    OperationType getType();

    String getDescription();

    UUID getRelatedEntityId();

    OperationExecutionResult getExecutionResult();

    OperationStatus getStatus();

    Integer getPriority();

    Integer getMaxAttemptCount();

    Integer getAttemptCount();

    Long getRetryDelay();

    Instant getCreateTimestamp();

    Instant getUpdateTimestamp();
}
