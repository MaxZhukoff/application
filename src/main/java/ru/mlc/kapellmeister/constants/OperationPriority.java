package ru.mlc.kapellmeister.constants;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum OperationPriority {

    MAX(0),
    FIRST(1),
    SECOND(2),
    THIRD(3),
    FOURTH(4),
    FIFTH(5),
    SIXTH(6),
    SEVENTH(7),
    EIGHTH(8),
    NINTH(9),
    MIN(10);

    public static final OperationPriority DEFAULT = FIFTH;

    private final int priority;
}
