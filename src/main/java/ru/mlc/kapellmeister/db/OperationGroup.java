package ru.mlc.kapellmeister.db;

import jakarta.annotation.Nullable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import ru.mlc.kapellmeister.constants.OperationGroupStatus;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class OperationGroup {

    @Id
    private UUID id;

    @Builder.Default
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<Operation> operations = new ArrayList<>();

    private String description;

    private String comment;

    @Enumerated(EnumType.STRING)
    private OperationGroupStatus status;

    @CreationTimestamp
    private Instant createTimestamp;

    @UpdateTimestamp
    private Instant updateTimestamp;

    @Nullable
    private UUID parentOperationId;
}
