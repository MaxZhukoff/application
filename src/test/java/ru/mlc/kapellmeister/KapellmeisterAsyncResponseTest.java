package ru.mlc.kapellmeister;

import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.transaction.support.TransactionTemplate;
import ru.mlc.kapellmeister.api.Kapellmeister;
import ru.mlc.kapellmeister.api.KapellmeisterEngine;
import ru.mlc.kapellmeister.api.KapellmeisterOperationThreadPoolExecutor;
import ru.mlc.kapellmeister.api.KapellmeisterStorageService;
import ru.mlc.kapellmeister.api.OperationExecutor;
import ru.mlc.kapellmeister.api.OperationState;
import ru.mlc.kapellmeister.configuration.KapellmeisterConfigurationProperties;
import ru.mlc.kapellmeister.configuration.KapellmeisterIntegrationTest;
import ru.mlc.kapellmeister.constants.OperationExecutionResult;
import ru.mlc.kapellmeister.constants.OperationGroupStatus;
import ru.mlc.kapellmeister.constants.OperationStatus;
import ru.mlc.kapellmeister.constants.OperationType;
import ru.mlc.kapellmeister.db.Operation;
import ru.mlc.kapellmeister.db.OperationGroup;
import ru.mlc.kapellmeister.db.repository.OperationJdbcRepository;
import ru.mlc.kapellmeister.service.KapellmeisterAfterCommitService;
import ru.mlc.kapellmeister.service.KapellmeisterTimeSynchronizationService;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Exchanger;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@KapellmeisterIntegrationTest(KapellmeisterAsyncResponseTest.TestConfig.class)
class KapellmeisterAsyncResponseTest {

    @Autowired
    private KapellmeisterEngine kapellmeisterEngine;
    @Autowired
    private Kapellmeister kapellmeister;
    // без явного указания имени спринг не может разобраться кого моккировать, потому что есть наследник
    @SpyBean
    private TestAsyncExecutor testAsyncExecutor;
    @Autowired
    private KapellmeisterStorageService storageService;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private WaitableKapellmeisterTrigger waitableKapellmeisterTrigger;
    @Autowired
    private KapellmeisterConfigurationProperties properties;
    @SpyBean
    private KapellmeisterTimeSynchronizationService timeService;

    private static final Instant jobDeadLine = Instant.now().plusSeconds(10000L);

    @BeforeEach
    void setUp() {
        testAsyncExecutor.getOrder().clear();
        when(timeService.calculateAfterCommitExecutionInterruptInstant()).thenReturn(Instant.now().plusSeconds(1000L));
    }

    @Test
    void asyncOperationPositive() {
        OperationState id = runOnTransaction(() ->
                kapellmeister.use(testAsyncExecutor)
                        .params("123")
                        .executeAfterCommit()
        );

        waitableKapellmeisterTrigger.waitCompletePostProcess();

        Operation operation = storageService.getOperation(id.getId());
        assertEquals(OperationStatus.WAIT_RESPONSE, operation.getStatus());
        OperationGroup transaction = storageService.getGroup(operation.getGroupId());
        assertEquals(OperationGroupStatus.IN_PROGRESS, transaction.getStatus());

        kapellmeisterEngine.saveResult(id.getId(), OperationExecutionResult.SUCCESS);

        assertEquals(OperationStatus.SUCCESS, storageService.getOperation(id.getId()).getStatus());
        assertEquals(OperationGroupStatus.COMPLETED, storageService.getGroup(operation.getGroupId()).getStatus());
    }

    @Test
    void retryAsyncOperation() {
        OperationState id = runOnTransaction(() ->
                kapellmeister.use(testAsyncExecutor)
                        .maxAttemptCount(2)
                        .params("1")
                        .executeAfterCommit()
        );
        waitableKapellmeisterTrigger.waitCompletePostProcess();
        assertEquals(OperationGroupStatus.IN_PROGRESS, storageService.getGroup(id.getGroupId()).getStatus());
        assertEquals(OperationStatus.WAIT_RESPONSE, storageService.getOperation(id.getId()).getStatus());

        kapellmeisterEngine.saveResult(id.getId(), OperationExecutionResult.ATTEMPT_FAILED);
        assertEquals(OperationGroupStatus.IN_PROGRESS, storageService.getGroup(id.getGroupId()).getStatus());
        assertEquals(OperationStatus.CAN_RETRY, storageService.getOperation(id.getId()).getStatus());

        runOnTransaction(() -> {
            Operation operation = storageService.getOperation(id.getId());
            operation.setRetryDelay(0L);
            storageService.update(operation);
        });

        kapellmeisterEngine.executeAvailableOperationGroupsSync(jobDeadLine, properties.getMaxCountOfOperationsForIteration());

        kapellmeisterEngine.saveResult(id.getId(), OperationExecutionResult.SUCCESS);

        assertEquals(OperationGroupStatus.COMPLETED, storageService.getGroup(id.getGroupId()).getStatus());
        assertEquals(OperationStatus.SUCCESS, storageService.getOperation(id.getId()).getStatus());
    }

    @Test
    void failWhenResponseWaitingTimoutReached() {
        OperationState id = runOnTransaction(() ->
                kapellmeister.use(testAsyncExecutor)
                        .waitResponseTimeout(0L)
                        .retryDelay(100000)
                        .params("1")
                        .addToQueue()
        );
        kapellmeisterEngine.executeAvailableOperationGroupsSync(jobDeadLine, properties.getMaxCountOfOperationsForIteration());
        assertEquals(OperationGroupStatus.IN_PROGRESS, storageService.getGroup(id.getGroupId()).getStatus());
        assertEquals(OperationStatus.WAIT_RESPONSE, storageService.getOperation(id.getId()).getStatus());
        kapellmeisterEngine.saveResult(id.getId(), OperationExecutionResult.SUCCESS);
        assertEquals(OperationStatus.FAILED, storageService.getOperation(id.getId()).getStatus());
        assertEquals(OperationGroupStatus.FAILED, storageService.getGroup(id.getGroupId()).getStatus());
    }

    @Test
    void failWhenResponseWaitingTimoutReachedWithRetry() {
        OperationState id = runOnTransaction(() ->
                kapellmeister.use(testAsyncExecutor)
                        .maxAttemptCount(2)
                        .params("1")
                        .executeAfterCommit()
        );

        waitableKapellmeisterTrigger.waitCompletePostProcess();
        assertEquals(OperationGroupStatus.IN_PROGRESS, storageService.getGroup(id.getGroupId()).getStatus());
        assertEquals(OperationStatus.WAIT_RESPONSE, storageService.getOperation(id.getId()).getStatus());

        kapellmeisterEngine.saveResult(id.getId(), OperationExecutionResult.ATTEMPT_FAILED);
        assertEquals(OperationGroupStatus.IN_PROGRESS, storageService.getGroup(id.getGroupId()).getStatus());
        assertEquals(OperationStatus.CAN_RETRY, storageService.getOperation(id.getId()).getStatus());

        runOnTransaction(() -> {
            Operation operation = storageService.getOperation(id.getId());
            operation.setRetryDelay(0L);
            storageService.update(operation);
        });

        kapellmeisterEngine.executeAvailableOperationGroupsSync(jobDeadLine, properties.getMaxCountOfOperationsForIteration());
        assertEquals(OperationGroupStatus.IN_PROGRESS, storageService.getGroup(id.getGroupId()).getStatus());
        assertEquals(OperationStatus.WAIT_RESPONSE, storageService.getOperation(id.getId()).getStatus());

        runOnTransaction(() -> {
            Operation operation = storageService.getOperation(id.getId());
            operation.setWaitResponseTimeout(0L);
            storageService.update(operation);
        });
        kapellmeisterEngine.saveResult(id.getId(), OperationExecutionResult.SUCCESS);
        assertEquals(OperationGroupStatus.FAILED, storageService.getGroup(id.getGroupId()).getStatus());
        assertEquals(OperationStatus.FAILED, storageService.getOperation(id.getId()).getStatus());
    }

    @Test
    void saveResultForRolledBackOperation() {
        OperationState id = runOnTransaction(() ->
                kapellmeister.use(testAsyncExecutor)
                        .maxAttemptCount(2)
                        .params("fail")
                        .addToQueue()
        );
        kapellmeisterEngine.executeAvailableOperationGroupsSync(jobDeadLine, properties.getMaxCountOfOperationsForIteration());
        Operation operation = storageService.getOperation(id.getId());
        assertEquals(OperationExecutionResult.ATTEMPT_FAILED, operation.getExecutionResult());
    }

    private <T> T runOnTransaction(Supplier<T> function) {
        return transactionTemplate.execute(status -> function.get());
    }

    private void runOnTransaction(Runnable function) {
        transactionTemplate.executeWithoutResult(status -> function.run());
    }

    @EnableAsync
    @TestConfiguration
    public static class TestConfig {

        @Bean
        public TestAsyncExecutor testAsyncExecutor() {
            return new TestAsyncExecutor();
        }

        @Bean
        public WaitableKapellmeisterTrigger waitableKapellmeisterTrigger(KapellmeisterEngine kapellmeisterEngine,
                                                                         KapellmeisterTimeSynchronizationService timeService,
                                                                         KapellmeisterConfigurationProperties properties,
                                                                         KapellmeisterOperationThreadPoolExecutor threadPoolExecutor,
                                                                         OperationJdbcRepository operationRepository) {
            return new WaitableKapellmeisterTrigger(kapellmeisterEngine, timeService, properties, threadPoolExecutor, operationRepository);
        }
    }

    @Getter
    public static class TestAsyncExecutor implements OperationExecutor<String> {

        @Override
        public String getName() {
            return "Async";
        }

        @Override
        public OperationType getOperationType() {
            return OperationType.ASYNC_REQUEST;
        }

        @Getter
        private final List<String> order = new CopyOnWriteArrayList<>();

        @Override
        public OperationExecutionResult execute(String param) {
            order.add(param);
            return getExecutionResult(param);
        }

        public OperationExecutionResult getExecutionResult(String param) {
            if (param.equals("fail")) {
                throw new RuntimeException("fail");
            }
            return OperationExecutionResult.SUCCESS;
        }

        @Override
        public String deserializeParams(String params) {
            return params;
        }

        @Override
        public String serializeParams(String params) {
            return params;
        }
    }

    @Slf4j
    public static class WaitableKapellmeisterTrigger extends KapellmeisterAfterCommitService.Trigger {

        private final Exchanger<String> exchanger = new Exchanger<>();
        private final KapellmeisterEngine kapellmeisterEngine;
        private final KapellmeisterOperationThreadPoolExecutor threadPoolExecutor;
        private final OperationJdbcRepository operationRepository;

        public WaitableKapellmeisterTrigger(KapellmeisterEngine kapellmeisterEngine, KapellmeisterTimeSynchronizationService timeService, KapellmeisterConfigurationProperties properties, KapellmeisterOperationThreadPoolExecutor operationThreadPoolExecutor, OperationJdbcRepository operationRepository) {
            super(kapellmeisterEngine, timeService, properties);
            this.kapellmeisterEngine = kapellmeisterEngine;
            this.threadPoolExecutor = operationThreadPoolExecutor;
            this.operationRepository = operationRepository;
        }

        @Override
        @SneakyThrows
        public void execute(KapellmeisterAfterCommitService.Event event) {
            super.execute(event);
            threadPoolExecutor.waitExecution();
            while (operationRepository.findAll().stream()
                    .anyMatch(operationEntity -> !operationEntity.getStatus().isCompleted() && !operationEntity.getStatus().isFailed() && !operationEntity.getStatus().isWaitResponse())) {
                kapellmeisterEngine.executeAvailableOperationGroupsSync(jobDeadLine);
            }
            kapellmeisterEngine.executeAvailableOperationGroupsSync(jobDeadLine);
            try {
                exchanger.exchange("notification", 500000L, TimeUnit.MILLISECONDS);
            } catch (InterruptedException | TimeoutException e) {
                log.error(e.getMessage(), e);
            }
        }

        @SneakyThrows
        public void waitCompletePostProcess() {
            exchanger.exchange("waiter", 500000L, TimeUnit.MILLISECONDS);
        }
    }
}