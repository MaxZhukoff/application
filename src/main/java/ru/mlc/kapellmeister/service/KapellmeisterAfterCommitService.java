package ru.mlc.kapellmeister.service;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.event.TransactionalEventListener;
import ru.mlc.kapellmeister.api.KapellmeisterEngine;
import ru.mlc.kapellmeister.configuration.KapellmeisterConfigurationProperties;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;


@RequiredArgsConstructor
public class KapellmeisterAfterCommitService {

    private final ApplicationEventPublisher publisher;
    private final KapellmeisterConfigurationProperties properties;

    public void some(UUID groupId, Set<UUID> operationIds) {
        publisher.publishEvent(new Event(this, groupId, operationIds));
    }

    @Getter
    public static class Event extends ApplicationEvent {

        private final UUID groupId;
        private final Set<UUID> operationIds;
        private UUID id;

        public Event(Object source, UUID groupId, Set<UUID> operationIds) {
            super(source);
            this.groupId = groupId;
            this.operationIds = operationIds;
            id = UUID.randomUUID();
        }
    }

    @Slf4j
    @RequiredArgsConstructor
    public static class Trigger {

        private final KapellmeisterEngine kapellmeisterEngine;
        private final KapellmeisterTimeSynchronizationService timeService;
        private final KapellmeisterConfigurationProperties properties;

        @Async
        @TransactionalEventListener
        public void execute(Event event) {
            Set<UUID> operationIds = event.getOperationIds();
            Instant jobDeadLine = timeService.calculateAfterCommitExecutionInterruptInstant();
            Integer maxCount = properties.getMaxCountOfOperationsForIteration();
            log.info("Инициировано выполнение операций {} ", operationIds);
            kapellmeisterEngine.processOperationsOfGroup(event.getGroupId(), jobDeadLine, o -> operationIds.contains(o.getId()), maxCount);
        }
    }
}
