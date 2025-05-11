package ru.mlc.kapellmeister.service;

import lombok.RequiredArgsConstructor;
import ru.mlc.kapellmeister.configuration.KapellmeisterConfigurationProperties;

import java.time.Instant;

@RequiredArgsConstructor
public class KapellmeisterTimeSynchronizationService {

    private final KapellmeisterConfigurationProperties properties;

    public Instant now() {
        return Instant.now();
    }

    public boolean isRetryPeriodPassed(Instant lastExecutionInstant, Long retryDelayMillis) {
        return lastExecutionInstant == null || now().isAfter(lastExecutionInstant.plusMillis(retryDelayMillis));
    }

    public boolean isWaitingPeriodBeforeVerificationPeriodPassed(Instant lastExecutionInstant, Long waitingPeriodBeforeVerification) {
        return lastExecutionInstant == null || now().isAfter(lastExecutionInstant.plusMillis(waitingPeriodBeforeVerification));
    }

    public boolean isResponseWaitingExpired(Instant lastExecutionInstant, Long maxWaitMillis) {
        return maxWaitMillis != null && now().isAfter(lastExecutionInstant.plusMillis(maxWaitMillis));
    }

    public boolean isIterationPeriodLimitReached(Instant jodDeadline) {
        return now().isAfter(jodDeadline);
    }

    public boolean isOperationDeadlineReached(Instant operationDeadline) {
        return now().isAfter(operationDeadline);
    }

    public Instant calculateAfterCommitExecutionInterruptInstant() {
        return now().plusMillis(properties.getOperationGroupBlockedForRetryAfterCreate());
    }

    public Instant calculateOperationExecutionBlockedForInstant() {
        return now().minusMillis(properties.getOperationGroupBlockedForRetryAfterCreate());
    }
}
