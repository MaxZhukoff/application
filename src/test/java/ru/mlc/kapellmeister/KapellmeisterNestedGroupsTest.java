package ru.mlc.kapellmeister;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.transaction.support.TransactionTemplate;
import ru.mlc.kapellmeister.api.Kapellmeister;
import ru.mlc.kapellmeister.api.KapellmeisterEngine;
import ru.mlc.kapellmeister.api.KapellmeisterStorageService;
import ru.mlc.kapellmeister.api.OperationExecutor;
import ru.mlc.kapellmeister.configuration.KapellmeisterIntegrationTest;
import ru.mlc.kapellmeister.constants.OperationExecutionResult;
import ru.mlc.kapellmeister.constants.OperationType;
import ru.mlc.kapellmeister.db.Operation;
import ru.mlc.kapellmeister.db.OperationGroup;
import ru.mlc.kapellmeister.db.repository.OperationGroupJdbcRepository;
import ru.mlc.kapellmeister.db.repository.OperationJdbcRepository;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@KapellmeisterIntegrationTest(KapellmeisterNestedGroupsTest.TestConfig.class)
class KapellmeisterNestedGroupsTest {

    @Autowired
    private Kapellmeister kapellmeister;
    @Autowired
    private KapellmeisterEngine kapellmeisterEngine;
    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private TestParentGroupExecutor testParentGroupExecutor;
    @Autowired
    private TestFirstChildGroupExecutor testFirstChildGroupExecutor;

    @Autowired
    private OperationGroupJdbcRepository operationGroupRepository;
    @Autowired
    private OperationJdbcRepository operationRepository;
    @Autowired
    private KapellmeisterStorageService storageService;

    @Test
    void executeNestedGroupsAfterCommit() {
        transactionTemplate.execute(
                transactionStatus -> kapellmeister.use(testParentGroupExecutor)
                        .params(UUID.randomUUID())
                        .addToQueue()
        );

        kapellmeisterEngine.executeAvailableOperationGroupsSync(Instant.MAX);
        kapellmeisterEngine.executeAvailableOperationGroupsSync(Instant.MAX);
        kapellmeisterEngine.executeAvailableOperationGroupsSync(Instant.MAX);

        assertAll("проверка что все задачи выполнены успешно",
                () -> assertEquals(3, operationGroupRepository.findAll().size()),
                () -> assertEquals(3, operationRepository.findAll().size()),
                () -> assertTrue(storageService.getUncompleted().isEmpty()),
                () -> assertTrue(storageService.getExecutorNamesWithUncompletedOperations().isEmpty())
        );
        List<OperationGroup> groups = operationGroupRepository.findAll().stream()
                .sorted(Comparator.comparing(OperationGroup::getCreateTimestamp))
                .toList();

        assertAll("проверка правильности ссылок на родительскую операцию",
                () -> assertNull(groups.get(0).getParentOperationId()),
                () -> assertEquals(testParentGroupExecutor.getName(), getOperation(groups.get(1).getParentOperationId()).getExecutorName()),
                () -> assertEquals(testFirstChildGroupExecutor.getName(), getOperation(groups.get(2).getParentOperationId()).getExecutorName())
        );
    }

    private Operation getOperation(UUID operationId) {
        return storageService.getOperation(operationId);
    }

    @EnableAsync
    @TestConfiguration
    public static class TestConfig {

        @Bean
        public TestParentGroupExecutor testParentGroupExecutor(TestFirstChildGroupExecutor testFirstChildGroupExecutor,
                                                               Kapellmeister kapellmeister) {
            return new TestParentGroupExecutor(testFirstChildGroupExecutor, kapellmeister);
        }

        @Bean
        public TestFirstChildGroupExecutor testFirstChildGroupExecutor(TestSecondChildGroupExecutor testSecondChildGroupExecutor,
                                                                       Kapellmeister kapellmeister) {
            return new TestFirstChildGroupExecutor(testSecondChildGroupExecutor, kapellmeister);
        }

        @Bean
        public TestSecondChildGroupExecutor testSecondChildGroupExecutor() {
            return new TestSecondChildGroupExecutor();
        }
    }

    public abstract static class TestBaseUUIDOperationExecutor implements OperationExecutor<UUID> {

        @Override
        public OperationType getOperationType() {
            return OperationType.SYNC_REQUEST;
        }

        @Override
        public UUID deserializeParams(String params) {
            return UUID.fromString(params);
        }

        @Override
        public String serializeParams(UUID params) {
            return params.toString();
        }
    }

    @Slf4j
    @RequiredArgsConstructor
    public static class TestFirstChildGroupExecutor extends TestBaseUUIDOperationExecutor {

        private final TestSecondChildGroupExecutor testSecondChildGroupExecutor;
        private final Kapellmeister kapellmeister;

        @Override
        public String getName() {
            return "TEST_FIRST_CHILD_GROUP_EXECUTOR";
        }

        @Override
        public OperationExecutionResult execute(UUID param) {
            kapellmeister.use(testSecondChildGroupExecutor)
                    .params(UUID.randomUUID())
                    .optimized()
                    .executeAfterCommit();
            return OperationExecutionResult.SUCCESS;
        }
    }

    @Slf4j
    @RequiredArgsConstructor
    public static class TestParentGroupExecutor extends TestBaseUUIDOperationExecutor {

        private final TestFirstChildGroupExecutor testFirstChildGroupExecutor;
        private final Kapellmeister kapellmeister;

        @Override
        public String getName() {
            return "TEST_PARENT_GROUP_EXECUTOR";
        }

        @Override
        public OperationExecutionResult execute(UUID param) {
            kapellmeister.use(testFirstChildGroupExecutor)
                    .params(UUID.randomUUID())
                    .optimized()
                    .executeAfterCommit();
            return OperationExecutionResult.SUCCESS;
        }
    }

    @Slf4j
    @RequiredArgsConstructor
    public static class TestSecondChildGroupExecutor extends TestBaseUUIDOperationExecutor {

        @Override
        public String getName() {
            return "TEST_SECOND_CHILD_GROUP_EXECUTOR";
        }

        @Override
        public OperationExecutionResult execute(UUID param) {
            return OperationExecutionResult.SUCCESS;
        }
    }
}
