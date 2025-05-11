package ru.mlc.async;

import org.springframework.context.ApplicationEvent;

public class AfterCommitTransactionOperation extends ApplicationEvent {

    private final Runnable operation;
    /**
     * Create a new {@code ApplicationEvent}.
     *
     * @param source the object on which the event initially occurred or with
     *               which the event is associated (never {@code null})
     * @param operation
     */
    public AfterCommitTransactionOperation(Object source, Runnable operation) {
        super(source);
        this.operation = operation;
    }

    public void execute() {
        operation.run();
    }
}
