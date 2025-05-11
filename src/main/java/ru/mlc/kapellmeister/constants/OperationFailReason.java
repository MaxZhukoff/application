package ru.mlc.kapellmeister.constants;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum OperationFailReason {

    ATTEMPTS_LIMIT_REACHED("Достигнут лимит попыток выполнить операцию"),
    DEADLINE_REACHED("Достигнут лимит времени выполнения операции"),
    RESPONSE_WAIT_TIMEOUT_REACHED("Ответ не получен вовремя"),
    FAILED_EXECUTION("Негативный результат выполнения операции"),
    PREVIOUS_RESERVED("Предыдущая операция выполнена условно"),
    EXECUTION_ERROR("Во время выполнения операции произошла ошибка"),
    EXECUTOR_NOT_FOUND("Экзекутор не найден"),
    EXECUTOR_NOT_SUPPORTS_VERIFICATION("Экзекутор не поддерживает проверку результата операции"),
    VERIFICATION_FAILED("Провалена проверка успешности выполнения операции"),
    VERIFICATION_ERROR("Ошибка при проверке успешности выполнения операции");

    private final String description;

    public String toMessage() {
        return name() + ": " + getDescription();
    }
}
