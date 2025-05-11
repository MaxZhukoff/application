package ru.mlc.kapellmeister.db;

import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.Accessors;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import ru.mlc.kapellmeister.api.OperationState;
import ru.mlc.kapellmeister.api.RetryConfig;
import ru.mlc.kapellmeister.constants.OperationExecutionResult;
import ru.mlc.kapellmeister.constants.OperationImportanceType;
import ru.mlc.kapellmeister.constants.OperationStatus;
import ru.mlc.kapellmeister.constants.OperationType;
import ru.mlc.kapellmeister.constants.RollbackType;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Accessors(chain = true)
public class Operation implements OperationState {

    private UUID id;

    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private UUID groupId;

    private UUID relatedEntityId;

    private String description;

    @Enumerated(EnumType.STRING)
    private OperationExecutionResult executionResult;

    @Enumerated(EnumType.STRING)
    private OperationStatus status;

    @Enumerated(EnumType.STRING)
    private OperationImportanceType importanceType;

    @Enumerated(EnumType.STRING)
    private RollbackType rollbackType;

    @Enumerated(EnumType.STRING)
    private OperationType type;

    private String executorName;

    private String params;

    private Integer priority;

    @Builder.Default
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<Operation> previous = new ArrayList<>();

    private Integer maxAttemptCount;

    private Integer attemptCount;

    private Long retryDelay;

    private Long waitResponseTimeout;

    @CreationTimestamp
    private Instant createTimestamp;

    @UpdateTimestamp
    private Instant updateTimestamp;

    private Instant deadlineTimestamp;

    private Instant lastExecutionTimeStamp;

    private String comment;

    @Version
    private Integer version;

    public Operation addPrevious(Operation operation) {
        getPrevious().add(operation);
        return this;
    }

    public void apply(RetryConfig retryConfig) {
        setPriority(retryConfig.getPriority());
        setMaxAttemptCount(retryConfig.getMaxAttemptCount());
        setRetryDelay(retryConfig.getRetryDelay());
        setDeadlineTimestamp(retryConfig.getDeadlineTimestamp());
        setWaitResponseTimeout(retryConfig.getWaitResponseTimeout());
    }
}
