package ru.mlc.kapellmeister.configuration;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import ru.mlc.data.scope.TransactionScope;
import ru.mlc.kapellmeister.api.Kapellmeister;
import ru.mlc.kapellmeister.api.KapellmeisterEngine;
import ru.mlc.kapellmeister.api.KapellmeisterOperationProcessor;
import ru.mlc.kapellmeister.api.KapellmeisterOperationThreadPoolExecutor;
import ru.mlc.kapellmeister.api.KapellmeisterStorageService;
import ru.mlc.kapellmeister.api.OperationExecutor;
import ru.mlc.kapellmeister.db.mapper.OperationGroupRowMapper;
import ru.mlc.kapellmeister.db.mapper.OperationRowMapper;
import ru.mlc.kapellmeister.db.repository.OperationGroupJdbcRepository;
import ru.mlc.kapellmeister.db.repository.OperationJdbcRepository;
import ru.mlc.kapellmeister.db.tables.OperationGroupTable;
import ru.mlc.kapellmeister.db.tables.OperationOrderBindingTable;
import ru.mlc.kapellmeister.db.tables.OperationTable;
import ru.mlc.kapellmeister.service.KapellmeisterAfterCommitService;
import ru.mlc.kapellmeister.service.KapellmeisterEngineImpl;
import ru.mlc.kapellmeister.service.KapellmeisterGroupContextHolder;
import ru.mlc.kapellmeister.service.KapellmeisterImpl;
import ru.mlc.kapellmeister.service.KapellmeisterOperationExecutorsService;
import ru.mlc.kapellmeister.service.KapellmeisterOperationProcessorImpl;
import ru.mlc.kapellmeister.service.KapellmeisterOperationThreadPoolExecutorImpl;
import ru.mlc.kapellmeister.service.KapellmeisterPostgresJdbcStorageService;
import ru.mlc.kapellmeister.service.KapellmeisterTimeSynchronizationService;
import ru.mlc.kapellmeister.service.OperationValidator;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@EnableConfigurationProperties(KapellmeisterConfigurationProperties.class)
@RequiredArgsConstructor
public class KapellmeisterConfiguration {

    private final KapellmeisterConfigurationProperties properties;

    @Bean
    public Kapellmeister kapellmeister(KapellmeisterGroupContextHolder kapellmeisterGroupContextHolder,
                                       KapellmeisterStorageService kapellmeisterStorageService,
                                       OperationValidator operationValidator,
                                       KapellmeisterAfterCommitService kapellmeisterAfterCommitService) {
        return new KapellmeisterImpl(
                properties,
                kapellmeisterGroupContextHolder,
                kapellmeisterStorageService,
                operationValidator,
                kapellmeisterAfterCommitService
        );
    }

    @Bean
    public OperationTable operationTable() {
        return new OperationTable();
    }

    @Bean
    public OperationGroupTable operationGroupTable() {
        return new OperationGroupTable();
    }

    @Bean
    public OperationOrderBindingTable operationOrderBindingTable() {
        return new OperationOrderBindingTable();
    }

    @Bean
    public OperationRowMapper operationRowMapper(OperationTable operationTable) {
        return new OperationRowMapper(operationTable);
    }

    @Bean
    public OperationGroupRowMapper operationGroupRowMapper(OperationRowMapper operationRowMapper) {
        return new OperationGroupRowMapper(operationRowMapper);
    }

    @Bean
    public OperationJdbcRepository operationRepository(OperationRowMapper operationRowMapper,
                                                       JdbcTemplate jdbcTemplate,
                                                       NamedParameterJdbcTemplate namedParameterJdbcTemplate,
                                                       OperationTable operationTable,
                                                       OperationOrderBindingTable operationOrderBindingTable) {
        return new OperationJdbcRepository(operationRowMapper, jdbcTemplate, namedParameterJdbcTemplate, operationTable, operationOrderBindingTable);
    }

    @Bean
    public OperationGroupJdbcRepository operationGroupRepository(OperationGroupRowMapper operationGroupRowMapper,
                                                                 JdbcTemplate jdbcTemplate,
                                                                 NamedParameterJdbcTemplate namedParameterJdbcTemplate,
                                                                 OperationGroupTable operationGroupTable,
                                                                 OperationTable operationTable) {
        return new OperationGroupJdbcRepository(operationGroupRowMapper, jdbcTemplate, namedParameterJdbcTemplate, operationGroupTable, operationTable);
    }

    @Bean
    public KapellmeisterStorageService kapellmeisterStorageService(OperationJdbcRepository operationRepository,
                                                                   OperationGroupJdbcRepository operationGroupRepository) {
        return new KapellmeisterPostgresJdbcStorageService(operationRepository, operationGroupRepository);
    }

    @Bean
    public KapellmeisterOperationExecutorsService kapellmeisterOperationExecutorsService(List<OperationExecutor<?>> executors,
                                                                                         KapellmeisterStorageService kapellmeisterStorageService) {
        return new KapellmeisterOperationExecutorsService(executors, properties.getExecutingEnabled(), kapellmeisterStorageService);
    }

    @Bean
    public KapellmeisterEngine kapellmeisterEngine(KapellmeisterStorageService kapellmeisterStorageService,
                                                   KapellmeisterTimeSynchronizationService timeService,
                                                   KapellmeisterOperationThreadPoolExecutor kapellmeisterOperationThreadPoolExecutor) {
        return new KapellmeisterEngineImpl(properties.getExecutingEnabled(), kapellmeisterStorageService, timeService, kapellmeisterOperationThreadPoolExecutor);
    }

    @Bean
    public KapellmeisterAfterCommitService kapellmeisterAfterCommitService(ApplicationEventPublisher publisher, KapellmeisterConfigurationProperties properties) {
        return new KapellmeisterAfterCommitService(publisher, properties);
    }

    @Bean
    @ConditionalOnProperty(name = "kapellmeister.afterCommitExecutionEnabled", havingValue = "true", matchIfMissing = true)
    public KapellmeisterAfterCommitService.Trigger kapellmeisterAfterCommitServiceTrigger(KapellmeisterEngine kapellmeisterEngine,
                                                                                          KapellmeisterTimeSynchronizationService timeService) {
        return new KapellmeisterAfterCommitService.Trigger(kapellmeisterEngine, timeService, properties);
    }

    @Bean
    public KapellmeisterTimeSynchronizationService timeSynchronizationService() {
        return new KapellmeisterTimeSynchronizationService(properties);
    }

    @Bean
    public OperationValidator operationValidator(KapellmeisterStorageService kapellmeisterStorageService) {
        return new OperationValidator(kapellmeisterStorageService);
    }

    @Bean
    public KapellmeisterGroupContextHolder transactionContextHolder(KapellmeisterGroupContextHolder.Context context) {
        return new KapellmeisterGroupContextHolder(context);
    }

    @Bean
    @TransactionScope
    public KapellmeisterGroupContextHolder.Context transactionContext() {
        return KapellmeisterGroupContextHolder.Context.builder()
                .transactionId(UUID.randomUUID())
                .methodName(TransactionSynchronizationManager.getCurrentTransactionName())
                .build();
    }

    @Bean
    public KapellmeisterOperationProcessor kapellmeisterThreadPoolExecutor(KapellmeisterOperationExecutorsService operationExecutorsService,
                                                                           KapellmeisterStorageService kapellmeisterStorageService,
                                                                           KapellmeisterTimeSynchronizationService timeService,
                                                                           KapellmeisterGroupContextHolder kapellmeisterGroupContextHolder) {
        return new KapellmeisterOperationProcessorImpl(
                operationExecutorsService,
                kapellmeisterStorageService,
                timeService,
                kapellmeisterGroupContextHolder
        );
    }

    @Bean
    public KapellmeisterOperationThreadPoolExecutor kapellmeisterOperationThreadPoolExecutor(TransactionTemplate transactionTemplate,
                                                                                             KapellmeisterStorageService kapellmeisterStorageService,
                                                                                             KapellmeisterOperationProcessor kapellmeisterOperationProcessor) {
        return new KapellmeisterOperationThreadPoolExecutorImpl(
                new ThreadPoolExecutor(properties.getThreadsCount(), properties.getThreadsCount(),
                        0L, TimeUnit.MILLISECONDS,
                        new LinkedBlockingQueue<>(properties.getQueueSize())
                ),
                transactionTemplate,
                kapellmeisterStorageService,
                kapellmeisterOperationProcessor
        );
    }
}
