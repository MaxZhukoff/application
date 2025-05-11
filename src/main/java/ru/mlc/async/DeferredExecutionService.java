package ru.mlc.async;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.event.TransactionalEventListener;

@RequiredArgsConstructor
public class DeferredExecutionService {

    private final ApplicationEventPublisher publisher;

    /**
     * Инициирует выполнение переданной функции после успешного коммита транзакции
     *
     * В функции нельзя использовать бины со scope отличным от singleton, так как она выполняется в отдельном потоке,
     * разрешение бинов будет невозможным либо не корректным, так как определение контекста напрямую зависит от потока исполнения.
     * Все необходимые значения нужно сохранить в локальные переменные, и использовать напрямую (без обращения к ссылке на бин)
     * @param operation функция
     */
    public void executeAfterCommit(Runnable operation) {
        publisher.publishEvent(new AfterCommitTransactionOperation(this, operation));
    }

    @Async
    @TransactionalEventListener
    public void execute(AfterCommitTransactionOperation event) {
        event.execute();
    }
}
