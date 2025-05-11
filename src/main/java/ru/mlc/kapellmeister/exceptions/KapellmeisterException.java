package ru.mlc.kapellmeister.exceptions;

import ru.mlc.common.exception.InternalServerException;

public class KapellmeisterException extends InternalServerException {

    public KapellmeisterException(String message) {
        super(message);
    }

    public KapellmeisterException(String message, Throwable cause) {
        super(message, cause);
    }
}
