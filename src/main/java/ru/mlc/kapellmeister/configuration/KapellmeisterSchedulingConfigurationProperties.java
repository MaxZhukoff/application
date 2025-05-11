package ru.mlc.kapellmeister.configuration;

import jakarta.annotation.PostConstruct;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import ru.mlc.kapellmeister.exceptions.KapellmeisterException;

import java.time.Duration;

@Data
@Validated
@ConfigurationProperties(prefix = "kapellmeister.scheduling")
public class KapellmeisterSchedulingConfigurationProperties {

    /**
     * Количество миллисекунд между запусками капеллмеистера (окончание последнего запуска и началом предыдущего)
     */
    @NotNull
    private Long restartDelay;
    /**
     * Количество миллисекунд до первого запуска капеллмеистера после старта приложения
     */
    @NotNull
    private Long firstStartDelay;
    /**
     * Период максимального ожидания освобождения блокировки на выполнение операции в формате ISO8601 (примеры: P1dT2h10m15s PT10m15s)
     */
    @NotNull
    private Duration maxLockTime;
    /**
     * Период минимального периода блокировки на выполнение операции в формате ISO8601 (примеры: P1dT2h10m15s PT10m15s)
     */
    @NotNull
    private Duration minLockTime;
    /**
     * Инициируется остановка итерации шедуллера, когда до окончания максимального периода блокировки остается заданное количество миллисекунд
     */
    @NotNull
    private Long forcedStopWhenTimeLeft = 10000L;

    @PostConstruct
    public void validate() {
        if (maxLockTime.toSeconds() * 1000 < forcedStopWhenTimeLeft) {
            throw new KapellmeisterException("maxLockTime не может быть меньше чем forcedStopWhenTimeLeft");
        }
    }
}
