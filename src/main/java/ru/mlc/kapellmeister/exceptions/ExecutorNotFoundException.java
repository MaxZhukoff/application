package ru.mlc.kapellmeister.exceptions;

public class ExecutorNotFoundException extends KapellmeisterException {

    public ExecutorNotFoundException(String executorName) {
        super("Не найден executor с именем " + executorName);
    }
}
