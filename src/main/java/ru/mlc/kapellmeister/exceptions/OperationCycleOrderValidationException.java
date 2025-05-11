package ru.mlc.kapellmeister.exceptions;

import lombok.Getter;
import ru.mlc.kapellmeister.db.Operation;

import java.util.List;
import java.util.stream.Collectors;

public class OperationCycleOrderValidationException extends KapellmeisterException {

    @Getter
    private final List<Operation> cycledChain;

    public OperationCycleOrderValidationException(List<Operation> cycledChain) {
        super("Обнаружена циклическая зависимость в очередности выполнения задач: " + toString(cycledChain));
        this.cycledChain = cycledChain;
    }

    private static String toString(List<Operation> cycledChain) {
        return cycledChain.stream()
                .map(Operation::toString)
                .collect(Collectors.joining("\n  ", "\n  ", ""));
    }
}
