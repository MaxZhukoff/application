package ru.mlc.kapellmeister.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.mlc.kapellmeister.api.KapellmeisterStorageService;
import ru.mlc.kapellmeister.constants.OperationImportanceType;
import ru.mlc.kapellmeister.db.Operation;
import ru.mlc.kapellmeister.db.OperationGroup;
import ru.mlc.kapellmeister.exceptions.OperationConfigValidationException;
import ru.mlc.kapellmeister.exceptions.OperationCycleOrderValidationException;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

@ExtendWith(MockitoExtension.class)
class OperationValidatorTest {

    @Mock
    KapellmeisterStorageService storageService;

    @InjectMocks
    OperationValidator validator;

    @BeforeEach
    void setUp() {
        doReturn(null).when(storageService).setPreviousOperation(any());
    }

    private final OperationGroup transaction = OperationGroup.builder()
            .id(UUID.randomUUID())
            .build();
    private final Operation operation1 = Operation.builder()
            .id(UUID.randomUUID())
            .groupId(transaction.getId())
            .build();
    private final Operation operation2 = Operation.builder()
            .id(UUID.randomUUID())
            .groupId(transaction.getId())
            .build()
            .addPrevious(operation1);
    private final Operation operation3 = Operation.builder()
            .id(UUID.randomUUID())
            .groupId(transaction.getId())
            .build()
            .addPrevious(operation2);
    private final Operation operation4 = Operation.builder()
            .id(UUID.randomUUID())
            .groupId(transaction.getId())
            .build()
            .addPrevious(operation2);
    private final Operation operation5 = Operation.builder()
            .id(UUID.randomUUID())
            .groupId(transaction.getId())
            .build()
            .addPrevious(operation4);

    {
        transaction.setOperations(List.of(operation1, operation2, operation3, operation4, operation5));
    }

    @Test
    void throwIfExistsCycleOrder() {
        operation1.addPrevious(operation3);
        assertThrows(OperationCycleOrderValidationException.class, () -> validator.validateCycleBinding(transaction));
    }

    @Test
    void checkCycleChain() {
        operation1.addPrevious(operation3);
        try {
            validator.validateCycleBinding(transaction);
        } catch (OperationCycleOrderValidationException e) {
            assertEquals(List.of(operation1, operation3, operation2, operation1), e.getCycledChain());
        }
    }

    @Test
    void successForOrderWithoutCycle() {
        validator.validateCycleBinding(transaction);
    }

    @Test
    void throwThenForCriticalExistsNotCriticalOperation() {
        Operation o1 = Operation.builder()
                .importanceType(OperationImportanceType.REQUIRED)
                .build();
        Operation o2 = Operation.builder()
                .importanceType(OperationImportanceType.CRITICAL)
                .build()
                .addPrevious(o1);
        Operation o3 = Operation.builder()
                .importanceType(OperationImportanceType.CRITICAL)
                .build()
                .addPrevious(o2);

        assertThrows(OperationConfigValidationException.class, () -> validator.validatePreviousForCritical(o3));
    }
}