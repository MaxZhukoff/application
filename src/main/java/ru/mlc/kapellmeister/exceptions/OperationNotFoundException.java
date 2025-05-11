package ru.mlc.kapellmeister.exceptions;

import java.util.UUID;

public class OperationNotFoundException extends KapellmeisterException {

    public OperationNotFoundException(UUID operationId) {
        super("Не найдена операция " + operationId);
    }
}
