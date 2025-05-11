package ru.mlc.kapellmeister.service;

import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.Value;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.mlc.common.exception.InternalServerException;
import ru.mlc.kapellmeister.db.Operation;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@RequiredArgsConstructor
public class KapellmeisterGroupContextHolder {

    private final Context context;

    @Transactional(propagation = Propagation.MANDATORY)
    public Context getContext() {
        return context;
    }

    @Getter
    @Builder
    public static class Context {

        private final UUID transactionId;
        private final String methodName;
        @Setter
        private UUID currentExecutionOperationId;
        private final AtomicBoolean executionAfterCommitWasTriggerred = new AtomicBoolean(false);
        private final Set<UUID> operationsForExecuteAfterCommit = Collections.newSetFromMap(new ConcurrentHashMap<>());
        private final OptimizedCache optimizedCache = new OptimizedCache();

        public Optional<UUID> getCurrentExecutionOperationId() {
            return Optional.ofNullable(currentExecutionOperationId);
        }

        public static class OptimizedCache {

            private final Map<Key, Operation> optimizedCache = new ConcurrentHashMap<>();

            public void put(String executorName, String params, Operation operation) {
                optimizedCache.compute(new Key(executorName, params),
                        (key, value) -> {
                            if (value != null) {
                                throw new InternalServerException("Operation with key " + key + " already exists in cache");
                            } else {
                                return operation;
                            }
                        });
            }

            public Optional<Operation> find(String executorName, String params) {
                return Optional.ofNullable(optimizedCache.get(new Key(executorName, params)));
            }

            @Value
            private static class Key {
                String executorName;
                String params;
            }
        }
    }
}
