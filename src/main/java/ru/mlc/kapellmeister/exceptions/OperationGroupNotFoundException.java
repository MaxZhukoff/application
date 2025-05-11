package ru.mlc.kapellmeister.exceptions;

import java.util.UUID;

public class OperationGroupNotFoundException extends KapellmeisterException {

    public OperationGroupNotFoundException(UUID operationGroupId) {
        super("Не найдена группа операций " + operationGroupId);
    }
}
