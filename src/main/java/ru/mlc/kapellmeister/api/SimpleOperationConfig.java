package ru.mlc.kapellmeister.api;


import lombok.Builder;
import lombok.NonNull;
import lombok.Singular;
import lombok.Value;
import ru.mlc.kapellmeister.constants.OperationImportanceType;
import ru.mlc.kapellmeister.constants.RollbackType;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Value
@Builder
public class SimpleOperationConfig<T> implements OperationConfig<T> {

    @NonNull
    OperationExecutor<T> executor;

    OperationImportanceType importanceType;

    T params;

    UUID relatedEntityId;

    @Singular("previousOperationId")
    Set<UUID> previousOperationIds;

    @NonNull
    Integer priority;

    @NonNull
    Integer maxAttemptCount;

    @NonNull
    Long retryDelay;

    Long waitResponseTimeout;

    Instant deadlineTimestamp;

    Boolean executeAfterCommit;

    String description;

    RollbackType rollbackType;

    Boolean optimized;
}