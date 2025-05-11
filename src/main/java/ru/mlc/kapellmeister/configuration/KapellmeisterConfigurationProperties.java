package ru.mlc.kapellmeister.configuration;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties(prefix = "kapellmeister")
public class KapellmeisterConfigurationProperties {

    /**
     * Количество миллисекунд, которое группа операций не доступна для выполнения шедуллером после создания.
     * Параметр, для разрешения конкурентного доступа для операций выполняемых досрочно после коммита, и операций выполняемых по шедуллеру
     */
    @NotNull
    private Long operationGroupBlockedForRetryAfterCreate;

    @NotNull
    private Boolean afterCommitExecutionEnabled = true;

    @NotNull
    private Boolean schedulingEnabled = true;

    @NotNull
    private Boolean optimizationEnabled = false;

    @NotNull
    private Boolean executingEnabled = true;

    @NotNull
    private Integer maxCountOfOperationsForIteration = -1;
    @NotNull
    private Integer threadsCount = 20;
    @NotNull
    private Integer queueSize = 25;
}
