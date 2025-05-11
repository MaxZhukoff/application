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
import ru.mlc.kapellmeister.api.OperationState;
import ru.mlc.kapellmeister.api.VerifiableOperationExecutor;
import ru.mlc.kapellmeister.configuration.KapellmeisterIntegrationTest;
import ru.mlc.kapellmeister.constants.OperationExecutionResult;
import ru.mlc.kapellmeister.constants.OperationStatus;
import ru.mlc.kapellmeister.constants.OperationType;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static ru.mlc.kapellmeister.constants.OperationExecutionResult.ATTEMPT_FAILED;
import static ru.mlc.kapellmeister.constants.OperationStatus.CAN_RETRY;
import static ru.mlc.kapellmeister.constants.OperationStatus.CREATED;
import static ru.mlc.kapellmeister.constants.OperationStatus.FAILED;
import static ru.mlc.kapellmeister.constants.OperationStatus.SUCCESS;
import static ru.mlc.kapellmeister.constants.OperationStatus.VERIFICATION;

@KapellmeisterIntegrationTest(KapellmeisterVerifiableOperationTest.TestConfig.class)
class KapellmeisterVerifiableOperationTest {

    @Autowired
    private KapellmeisterEngine kapellmeisterEngine;
    @Autowired
    private Kapellmeister kapellmeister;
    @SpyBean
    private TestExecutor verifiableTestExecutor;
    @Autowired
    private KapellmeisterStorageService storageService;
    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    void positive() {
        OperationState operation = createOperation(1);
        assertEquals(CREATED, getOperationStatus(operation));

        executeOneOperation();
        assertEquals(VERIFICATION, getOperationStatus(operation));

        executeOneOperation();

        assertEquals(SUCCESS, getOperationStatus(operation));
    }

    @Test
    void retry() {
        OperationState operation = createOperation(2);
        assertEquals(CREATED, getOperationStatus(operation));

        executeOneOperation();
        assertEquals(VERIFICATION, getOperationStatus(operation));

        when(verifiableTestExecutor.verify(any())).thenReturn(ATTEMPT_FAILED);
        executeOneOperation();
        assertEquals(CAN_RETRY, getOperationStatus(operation));

        executeOneOperation();
        assertEquals(VERIFICATION, getOperationStatus(operation));

        when(verifiableTestExecutor.verify(any())).thenReturn(OperationExecutionResult.SUCCESS);
        executeOneOperation();
        assertEquals(SUCCESS, getOperationStatus(operation));
    }

    @Test
    void attemptsExpired() {
        OperationState operation = createOperation(2);
        assertEquals(CREATED, getOperationStatus(operation));

        executeOneOperation();
        assertEquals(VERIFICATION, getOperationStatus(operation));

        when(verifiableTestExecutor.verify(any())).thenReturn(ATTEMPT_FAILED);
        executeOneOperation();
        assertEquals(CAN_RETRY, getOperationStatus(operation));

        executeOneOperation();
        assertEquals(VERIFICATION, getOperationStatus(operation));

        executeOneOperation();
        assertEquals(FAILED, getOperationStatus(operation));
    }

    @Test
    void failWhenVerificationFailed() {
        OperationState operation = createOperation(2);
        assertEquals(CREATED, getOperationStatus(operation));

        executeOneOperation();
        assertEquals(VERIFICATION, getOperationStatus(operation));

        when(verifiableTestExecutor.verify(any())).thenReturn(OperationExecutionResult.FAIL);
        executeOneOperation();
        assertEquals(FAILED, getOperationStatus(operation));
    }

    @Test
    void retryAndFailWhenAttemptsExpiredWhenThrowFromVerification() {
        OperationState operation = createOperation(2);
        assertEquals(CREATED, getOperationStatus(operation));

        executeOneOperation();
        assertEquals(VERIFICATION, getOperationStatus(operation));

        when(verifiableTestExecutor.verify(any())).thenThrow(new RuntimeException());
        executeOneOperation();
        assertEquals(CAN_RETRY, getOperationStatus(operation));
        executeOneOperation();
        assertEquals(VERIFICATION, getOperationStatus(operation));

        executeOneOperation();
        assertEquals(FAILED, getOperationStatus(operation));
    }

    @SneakyThrows
    private void executeOneOperation() {
        kapellmeisterEngine.executeAvailableOperationGroupsSync(Instant.now().plusSeconds(100), 1);
        Thread.sleep(200);
    }

    private OperationState createOperation(int maxAttemptCount) {
        return transactionTemplate.execute(status ->
                kapellmeister.use(verifiableTestExecutor)
                        .params("123")
                        .maxAttemptCount(maxAttemptCount)
                        .retryDelay(100)
                        .addToQueue()
        );
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
    public static class TestExecutor implements VerifiableOperationExecutor<String> {

        @Override
        public OperationType getOperationType() {
            return OperationType.SYNC_REQUEST;
        }

        @Override
        public String getName() {
            return "test";
        }

        @Override
        public OperationExecutionResult verify(String param) {
            return OperationExecutionResult.SUCCESS;
        }

        @Override
        public OperationExecutionResult execute(String param) {
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
}
