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
import ru.mlc.kapellmeister.constants.OperationImportanceType;
import ru.mlc.kapellmeister.constants.OperationPriority;
import ru.mlc.kapellmeister.constants.OperationStatus;
import ru.mlc.kapellmeister.constants.OperationType;
import ru.mlc.kapellmeister.constants.RollbackType;
import ru.mlc.kapellmeister.db.Operation;
import ru.mlc.kapellmeister.db.OperationGroup;
import ru.mlc.kapellmeister.db.repository.OperationJdbcRepository;
import ru.mlc.kapellmeister.exceptions.OperationConfigValidationException;
import ru.mlc.kapellmeister.service.KapellmeisterAfterCommitService;
import ru.mlc.kapellmeister.service.KapellmeisterTimeSynchronizationService;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Exchanger;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@KapellmeisterIntegrationTest(KapellmeisterTest.TestConfig.class)
class KapellmeisterTest {

    @Autowired
    private KapellmeisterEngine kapellmeisterEngine;
    @Autowired
    private Kapellmeister kapellmeister;
    @SpyBean(name = "testExecutor")
    private TestExecutor testExecutor;
    @SpyBean(name = "testRollbackExecutor")
    private TestRollbackExecutor testRollbackExecutor;
    @Autowired
    private KapellmeisterStorageService storageService;
    @Autowired
    private KapellmeisterOperationThreadPoolExecutor operationThreadPoolExecutor;
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
        testExecutor.getOrder().clear();
        when(timeService.calculateAfterCommitExecutionInterruptInstant()).thenReturn(Instant.now().plusSeconds(1000L));
    }

    @Test
    void addOneWhenConfigOptimized() {
        runOnTransaction(() -> {
                    kapellmeister.use(testExecutor)
                            .params("123")
                            .optimized()
                            .addToQueue();
                    kapellmeister.use(testExecutor)
                            .params("123")
                            .optimized()
                            .addToQueue();
                }
        );
        assertEquals(1, storageService.findAll().size());
    }

    @Test
    void addAllWhenConfigNotOptimized() {
        runOnTransaction(() -> {
                    kapellmeister.use(testExecutor)
                            .params("123")
                            .addToQueue();
                    kapellmeister.use(testExecutor)
                            .params("123")
                            .addToQueue();
                }
        );
        assertEquals(2, storageService.findAll().size());
    }

    @Test
    void throwWhenConfigConflictedForOptimized() {
        assertThrows(OperationConfigValidationException.class, () ->
                runOnTransaction(() -> {
                            kapellmeister.use(testExecutor)
                                    .importanceType(OperationImportanceType.REQUIRED)
                                    .params("123")
                                    .optimized()
                                    .addToQueue();
                            kapellmeister.use(testExecutor)
                                    .importanceType(OperationImportanceType.CRITICAL)
                                    .params("123")
                                    .optimized()
                                    .addToQueue();
                        }
                )
        );
    }

    @Test
    void syncOperationPositive() {
        OperationState id = runOnTransaction(() ->
                kapellmeister.use(testExecutor)
                        .params("123")
                        .executeAfterCommit()
        );
        waitableKapellmeisterTrigger.waitCompletePostProcess();
        operationThreadPoolExecutor.waitExecution();
        Operation operation = storageService.getOperation(id.getId());
        assertEquals(OperationStatus.SUCCESS, operation.getStatus());
        OperationGroup transaction = storageService.getGroup(operation.getGroupId());
        assertEquals(OperationGroupStatus.COMPLETED, transaction.getStatus());
    }

    @Test
    void ordered() {
        runOnTransaction(() -> {
                    OperationState id1 = kapellmeister.use(testExecutor)
                            .params("1")
                            .priority(OperationPriority.MIN)
                            .executeAfterCommit();
                    OperationState id2 = kapellmeister.use(testExecutor)
                            .params("2")
                            .priority(OperationPriority.MAX)
                            .after(id1.getId())
                            .executeAfterCommit();
                    OperationState id3 = kapellmeister.use(testExecutor)
                            .params("3")
                            .executeAfterCommit();
                    OperationState id4 = kapellmeister.use(testExecutor)
                            .params("4")
                            .priority(OperationPriority.MAX)
                            .executeAfterCommit();
                    OperationState id45 = kapellmeister.use(testExecutor)
                            .params("5")
                            .priority(OperationPriority.SIXTH)
                            .executeAfterCommit();
                }
        );
        waitableKapellmeisterTrigger.waitCompletePostProcess();
        operationThreadPoolExecutor.waitExecution();
        assertEquals(List.of("4", "3", "5", "1", "2"), testExecutor.getOrder());
    }

    @Test
    void failTransaction() {
        when(testExecutor.getExecutionResult(eq("1"))).thenReturn(OperationExecutionResult.FAIL);
        UUID transactionId = runOnTransaction(() -> {
                    OperationState id1 = kapellmeister.use(testExecutor)
                            .params("1")
                            .executeAfterCommit();
                    OperationState id2 = kapellmeister.use(testExecutor)
                            .params("2")
                            .executeAfterCommit();
                    return id1.getGroupId();
                }
        );
        waitableKapellmeisterTrigger.waitCompletePostProcess();

        assertEquals(OperationGroupStatus.FAILED, storageService.getGroup(transactionId).getStatus());
    }

    @Test
    void notFailTransactionIfFailOptionalOperation() {
        when(testExecutor.getExecutionResult("1")).thenReturn(OperationExecutionResult.FAIL);
        List<OperationState> id = runOnTransaction(() -> List.of(
                        kapellmeister.use(testExecutor)
                                .params("1")
                                .importanceType(OperationImportanceType.OPTIONAL)
                                .executeAfterCommit(),
                        kapellmeister.use(testExecutor)
                                .params("2")
                                .executeAfterCommit()
                )
        );
        waitableKapellmeisterTrigger.waitCompletePostProcess();

        assertEquals(OperationGroupStatus.COMPLETED, storageService.getGroup(id.get(0).getGroupId()).getStatus());
        assertEquals(OperationStatus.SKIPPED, storageService.getOperation(id.get(0).getId()).getStatus());
        assertEquals(OperationStatus.SUCCESS, storageService.getOperation(id.get(1).getId()).getStatus());
    }

    @Test
    void executeOneOfTwoOperationsInCreatedGroup() {
        List<OperationState> id = runOnTransaction(() -> {
                    OperationState id1 = kapellmeister.use(testExecutor)
                            .params("1")
                            .addToQueue();
                    OperationState id2 = kapellmeister.use(testExecutor)
                            .params("2")
                            .after(id1.getId())
                            .addToQueue();
                    return List.of(id1, id2);
                }
        );

        properties.setMaxCountOfOperationsForIteration(0);
        kapellmeisterEngine.executeAvailableOperationGroupsSync(jobDeadLine, properties.getMaxCountOfOperationsForIteration());
        assertEquals(OperationGroupStatus.IN_PROGRESS, storageService.getGroup(id.get(0).getGroupId()).getStatus());
        assertEquals(OperationStatus.CREATED, storageService.getOperation(id.get(0).getId()).getStatus());
        assertEquals(OperationStatus.CREATED, storageService.getOperation(id.get(1).getId()).getStatus());
        assertTrue(kapellmeisterEngine.getUncompletedGroupIds().contains(id.get(0).getGroupId()));


        properties.setMaxCountOfOperationsForIteration(1);
        kapellmeisterEngine.executeAvailableOperationGroupsSync(jobDeadLine, properties.getMaxCountOfOperationsForIteration());
        assertEquals(OperationGroupStatus.IN_PROGRESS, storageService.getGroup(id.get(0).getGroupId()).getStatus());
        assertEquals(OperationStatus.SUCCESS, storageService.getOperation(id.get(0).getId()).getStatus());
        assertEquals(OperationStatus.CREATED, storageService.getOperation(id.get(1).getId()).getStatus());
        assertTrue(kapellmeisterEngine.getUncompletedGroupIds().contains(id.get(0).getGroupId()));

        properties.setMaxCountOfOperationsForIteration(2);
        kapellmeisterEngine.executeAvailableOperationGroupsSync(jobDeadLine, properties.getMaxCountOfOperationsForIteration());
        assertEquals(OperationGroupStatus.COMPLETED, storageService.getGroup(id.get(0).getGroupId()).getStatus());
        assertEquals(OperationStatus.SUCCESS, storageService.getOperation(id.get(0).getId()).getStatus());
        assertEquals(OperationStatus.SUCCESS, storageService.getOperation(id.get(1).getId()).getStatus());
        assertFalse(kapellmeisterEngine.getUncompletedGroupIds().contains(id.get(0).getGroupId()));
    }


    @Test
    void failWhenAttemptsIsOther() {
        when(testExecutor.getExecutionResult("2")).thenReturn(OperationExecutionResult.FAIL);
        List<OperationState> id = runOnTransaction(() -> {
                    OperationState id1 = kapellmeister.use(testExecutor)
                            .params("1")
                            .addToQueue();
                    OperationState id2 = kapellmeister.use(testExecutor)
                            .params("2")
                            .after(id1.getId())
                            .addToQueue();
                    return List.of(id1, id2);
                }
        );

        kapellmeisterEngine.executeAvailableOperationGroupsSync(jobDeadLine, properties.getMaxCountOfOperationsForIteration());
        kapellmeisterEngine.executeAvailableOperationGroupsSync(jobDeadLine, properties.getMaxCountOfOperationsForIteration());

        assertEquals(OperationGroupStatus.FAILED, storageService.getGroup(id.get(0).getGroupId()).getStatus());
        assertEquals(OperationStatus.SUCCESS, storageService.getOperation(id.get(0).getId()).getStatus());
        assertEquals(OperationStatus.FAILED, storageService.getOperation(id.get(1).getId()).getStatus());
    }

    @Test
    void failExpiredOperation() {
        when(testExecutor.getExecutionResult(any())).thenReturn(OperationExecutionResult.ATTEMPT_FAILED);
        OperationState id = runOnTransaction(() ->
                kapellmeister.use(testExecutor)
                        .deadLine(Instant.now())
                        .params("1")
                        .addToQueue()
        );
        kapellmeisterEngine.executeAvailableOperationGroupsSync(jobDeadLine, properties.getMaxCountOfOperationsForIteration());
        assertEquals(OperationGroupStatus.FAILED, storageService.getGroup(id.getGroupId()).getStatus());
        assertEquals(OperationStatus.EXPIRED, storageService.getOperation(id.getId()).getStatus());
    }

    @Test
    void retryWithUpdateConfig() {

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
        public TestExecutor testExecutor() {
            return new TestExecutor();
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

    @Slf4j
    public static class TestExecutor implements OperationExecutor<String> {

        @Getter
        private final List<String> order = new CopyOnWriteArrayList<>();

        @Override
        public OperationType getOperationType() {
            return OperationType.SYNC_REQUEST;
        }

        @Override
        public String getName() {
            return "test-rollback";
        }

        @Override
        public OperationExecutionResult execute(String param) {
            order.add(param);
            return getExecutionResult(param);
        }

        public OperationExecutionResult getExecutionResult(String param) {
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
    public static class TestRollbackExecutor implements OperationExecutor<String> {

        @Override
        public RollbackType getRollbackType() {
            return RollbackType.ROLLBACK;
        }

        @Getter
        private final List<String> order = new CopyOnWriteArrayList<>();

        @Override
        public OperationType getOperationType() {
            return OperationType.SYNC_REQUEST;
        }

        @Override
        public String getName() {
            return "test";
        }

        @Override
        public OperationExecutionResult execute(String param) {
            order.add(param);
            return getExecutionResult(param);
        }

        @Override
        public OperationExecutionResult rollback(String param) {
            return OperationExecutionResult.ROLLBACK_SUCCESS;
        }

        public OperationExecutionResult getExecutionResult(String param) {
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
                    .anyMatch(operationEntity -> !operationEntity.getStatus().isCompleted() && !operationEntity.getStatus().isFailed())) {
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