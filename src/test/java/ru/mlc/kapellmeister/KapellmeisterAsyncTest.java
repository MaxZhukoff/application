package ru.mlc.kapellmeister;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
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
import ru.mlc.kapellmeister.configuration.KapellmeisterIntegrationTest;
import ru.mlc.kapellmeister.constants.OperationExecutionResult;
import ru.mlc.kapellmeister.constants.OperationStatus;
import ru.mlc.kapellmeister.constants.OperationType;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static ru.mlc.kapellmeister.constants.OperationStatus.CREATED;
import static ru.mlc.kapellmeister.constants.OperationStatus.SUCCESS;

@Slf4j
@KapellmeisterIntegrationTest(KapellmeisterAsyncTest.TestConfig.class)
class KapellmeisterAsyncTest {

    @Autowired
    private KapellmeisterEngine kapellmeisterEngine;
    @Autowired
    private Kapellmeister kapellmeister;
    @SpyBean
    private TestExecutor testExecutor;
    @Autowired
    private KapellmeisterStorageService storageService;
    @SpyBean
    private KapellmeisterOperationThreadPoolExecutor kapellmeisterOperationThreadPoolExecutor;
    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    void positive() {
        List<OperationState> operationStateList = new ArrayList<>();
        transactionTemplate.execute(status -> {
            var o1 = createOperation(1, "1");
            operationStateList.add(o1);
            operationStateList.add(createOperation(1, "2", o1.getId()));
            operationStateList.add(createOperation(1, "3", o1.getId()));
            operationStateList.add(createOperation(1, "4", o1.getId()));
            var o5 = createOperation(1, "5", o1.getId());
            operationStateList.add(o5);
            operationStateList.add(createOperation(1, "6", o5.getId()));
            operationStateList.add(createOperation(1, "7", o5.getId()));
            operationStateList.add(createOperation(1, "8", o5.getId()));
            operationStateList.add(createOperation(1, "9", o5.getId()));
            operationStateList.add(createOperation(1, "10", o5.getId()));
            assertEquals(10, operationStateList.stream().filter(x -> x.getStatus() == CREATED).count());
            assertEquals(10, operationStateList.stream().filter(x -> x.getGroupId().equals(o1.getGroupId())).count());
            return "a";
        });


        executeOperations();
        assertEquals(10, storageService.findAll().stream().filter(x -> x.getStatus() == SUCCESS).count());
    }

    @Test
    void positive_withParallelOperations() {
        CountDownLatch countDownLatch = new CountDownLatch(5);
        doAnswer(invocationOnMock -> {
            countDownLatch.countDown();
            log.warn("поток встал в ожидание");
            try {
                countDownLatch.await();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            return invocationOnMock.callRealMethod();
        }).when(kapellmeisterOperationThreadPoolExecutor).processOperation(any(UUID.class));

        testExecutor.counter.set(0);
        List<OperationState> operationStateList = new ArrayList<>();
        transactionTemplate.execute(status -> {
            operationStateList.add(createOperation(1, "1"));
            operationStateList.add(createOperation(1, "2"));
            operationStateList.add(createOperation(1, "3"));
            operationStateList.add(createOperation(1, "4"));
            operationStateList.add(createOperation(1, "5"));
            operationStateList.add(createOperation(1, "6"));
            operationStateList.add(createOperation(1, "7"));
            operationStateList.add(createOperation(1, "8"));
            operationStateList.add(createOperation(1, "9"));
            operationStateList.add(createOperation(1, "10"));
            assertEquals(10, operationStateList.stream().filter(x -> x.getStatus() == CREATED).count());
            return "a";
        });

        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 4; j++) {
                kapellmeisterOperationThreadPoolExecutor.submitOperation(operationStateList.get(i).getId());
            }
        }

        executeOperations();
        assertEquals(10, storageService.findAll().stream().filter(x -> x.getStatus() == SUCCESS).count());
        assertEquals(10, testExecutor.counter.get());

    }


    @SneakyThrows
    private void executeOperations() {
        while (storageService.findAll().stream().anyMatch(x -> x.getStatus() == CREATED)) {
            kapellmeisterEngine.executeAvailableOperationGroupsSync(Instant.now().plusSeconds(100));
        }
    }

    private OperationState createOperation(int maxAttemptCount) {
        return
                kapellmeister.use(testExecutor)
                        .params("123")
                        .maxAttemptCount(maxAttemptCount)
                        .retryDelay(100)
                        .addToQueue();

    }

    private OperationState createOperation(int maxAttemptCount, String param) {
        return
                kapellmeister.use(testExecutor)
                        .params(param)
                        .maxAttemptCount(maxAttemptCount)
                        .retryDelay(100)
                        .addToQueue();

    }

    private OperationState createOperation(int maxAttemptCount, String param, UUID previous) {
        return
                kapellmeister.use(testExecutor)
                        .after(previous)
                        .params(param)
                        .maxAttemptCount(maxAttemptCount)
                        .retryDelay(100)
                        .addToQueue();

    }

    private OperationStatus getOperationStatus(OperationState operation) {
        return storageService.getOperation(operation.getId()).getStatus();
    }

    @EnableAsync
    @TestConfiguration
    public static class TestConfig {

        @Bean
        public TestExecutor testExecutor() {
            return new TestExecutor();
        }
    }

    @Slf4j
    public static class TestExecutor implements OperationExecutor<String> {

        @Override
        public OperationType getOperationType() {
            return OperationType.SYNC_REQUEST;
        }

        private AtomicInteger counter = new AtomicInteger(0);

        @Override
        public String getName() {
            return "test";
        }

        @Override
        @SneakyThrows
        public OperationExecutionResult execute(String param) {
            log.error("execute param: " + param);
            counter.incrementAndGet();
            log.error("counter: " + counter);
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
}
