package ru.mlc.kapellmeister.service;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ru.mlc.common.exception.ValidationException;
import ru.mlc.kapellmeister.api.KapellmeisterStorageService;
import ru.mlc.kapellmeister.api.OperationExecutor;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
public class KapellmeisterOperationExecutorsService {

    @Getter
    private final List<? extends OperationExecutor<?>> executors;
    private final boolean executingEnabled;
    private final KapellmeisterStorageService kapellmeisterStorageService;

    @PostConstruct
    private void validateConfiguration() {
        log.info("Доступные экзекуторы: \n {}", executors.stream()
                .map(executor -> executor.getName() + " - " + executor.getClass().getName())
                .collect(Collectors.joining(";\n")));
        Map<String, List<OperationExecutor<?>>> groupedExecutors = executors.stream()
                .collect(Collectors.groupingBy(OperationExecutor::getName));
        List<Map.Entry<String, List<OperationExecutor<?>>>> duplicated = groupedExecutors.entrySet().stream()
                .filter(entry -> entry.getValue().size() > 1)
                .toList();
        if (!duplicated.isEmpty()) {
            throw new ValidationException("Имена экзекуторов не уникальные:\n" + executors.stream()
                    .map(executor -> executor.getName() + " - " + executor.getClass().getName())
                    .collect(Collectors.joining(";\n"))
            );
        }
        Set<String> availableExecutorNames = groupedExecutors.keySet();
        Set<String> unavailableExecutorNames = kapellmeisterStorageService.getExecutorNamesWithUncompletedOperations().stream()
                .filter(executorName -> !availableExecutorNames.contains(executorName))
                .collect(Collectors.toSet());
        if (!unavailableExecutorNames.isEmpty()) {
            log.warn("Найдены не зарегистрированные экзекуторы " + String.join(", ", unavailableExecutorNames));
        }
        if (!executingEnabled) {
            log.warn("Выполнение операций отключено, executingEnabled = " + executingEnabled);
        }
    }

}
