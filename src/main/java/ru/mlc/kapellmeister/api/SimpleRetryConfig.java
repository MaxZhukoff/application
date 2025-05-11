package ru.mlc.kapellmeister.api;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;

@Value
@Builder
public class SimpleRetryConfig implements RetryConfig {

    Integer priority;

    Integer maxAttemptCount;

    Long retryDelay;

    Long waitResponseTimeout;

    Instant deadlineTimestamp;
}
