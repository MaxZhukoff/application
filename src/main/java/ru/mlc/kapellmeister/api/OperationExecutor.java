package ru.mlc.kapellmeister.api;

import ru.mlc.kapellmeister.constants.OperationExecutionResult;
import ru.mlc.kapellmeister.constants.OperationType;
import ru.mlc.kapellmeister.constants.RollbackType;

import java.util.Optional;

public interface OperationExecutor<T> {

    /**
     * @return Тип роллбэка для негативного сценария выполнения операции
     */
    default RollbackType getRollbackType() {
        return RollbackType.UNSUPPORTED;
    }

    /**
     * @return Тип операции
     */
    OperationType getOperationType();

    /**
     * Имя по которому определяется экзэкутор для выполнения операции
     */
    String getName();

    /**
     * Бизнес-логика.
     * Должна возвращать семантически правильное значение {@link OperationExecutionResult} для корректного управления жизненным циклом операции
     *
     * @param param Параметры для которых необходимо выполнить бизнеслогику
     * @return результат попытки выполнения операции
     */
    OperationExecutionResult execute(T param);

    /**
     * Бизнес-логика отката
     */
    default OperationExecutionResult rollback(T param) {
        return OperationExecutionResult.ROLLBACK_FAIL;
    }

    /**
     * Десериализует параметры полученные из БД для выполнения логики
     */
    T deserializeParams(String params);

    /**
     * сериализует параметры для хранения в БД
     */
    String serializeParams(T params);

    /**
     * Прекондишен выполнения операции.
     * Должна возвращать семантически правильное значение {@link OperationExecutionResult} для корректного управления жизненным циклом операции
     *
     * @param params параметры для выполнения операции
     * @return результат выполнения операции, {@link Optional#empty()} если прекондишен не выполнялся
     */
    default Optional<OperationExecutionResult> checkPrecondition(T params) {
        return Optional.empty();
    }

    /**
     * Точка расширения функциональности.
     * Позволяет изменить конфигурацию операции после не удачно попытки выполнения
     *
     * @param retryConfigBuilder билдер для формирования конфигурации
     * @param operationState     состояние операции на момент провала попытки ее выполнения
     * @return новая конфигурация выполнения задачи, {@link Optional#empty()} если не требуется переопределение настроек
     */
    default Optional<RetryConfig> getNewRetryConfig(SimpleRetryConfig.SimpleRetryConfigBuilder retryConfigBuilder, OperationState operationState) {
        return Optional.empty();
    }

    /**
     * Определяет критичность возникшего нештатного исключения в ходе выполнения операции
     *
     * @param exception нештатное исключение
     * @return true если возможно повторное выполнение операции, false если ошибка критичная и перевыполнение операции невозможно
     */
    default boolean canRetry(Exception exception) {
        return true;
    }
}
