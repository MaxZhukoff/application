package ru.mlc.kapellmeister.sceduling;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import ru.mlc.kapellmeister.api.KapellmeisterEngine;
import ru.mlc.kapellmeister.configuration.KapellmeisterConfigurationProperties;
import ru.mlc.kapellmeister.configuration.KapellmeisterSchedulingConfigurationProperties;
import ru.mlc.kapellmeister.service.KapellmeisterTimeSynchronizationService;

import java.time.Instant;

@Slf4j
@RequiredArgsConstructor
public class KapellmeisterSpringShedlockScheduler implements KapellmeisterScheduler {

    private final KapellmeisterEngine kapellmeisterEngine;
    private final KapellmeisterSchedulingConfigurationProperties schedulingConfigurationProperties;
    private final KapellmeisterConfigurationProperties configurationProperties;
    private final KapellmeisterTimeSynchronizationService kapellmeisterTimeSynchronizationService;

    @Override
    @Scheduled(initialDelayString = "${kapellmeister.scheduling.first-start-delay}", fixedDelayString = "${kapellmeister.scheduling.restart-delay}")
    @SchedulerLock(name = "kapellmeister", lockAtLeastFor = "${kapellmeister.scheduling.min-lock-time}", lockAtMostFor = "${kapellmeister.scheduling.max-lock-time}")
    public void executeAvailableOperationGroups() {
        Instant jobDeadLine = kapellmeisterTimeSynchronizationService.now().plusMillis(schedulingConfigurationProperties.getForcedStopWhenTimeLeft());
        log.info("Капельмейстер начал работу по шедуллеру (Максимальное время итерации до {})", jobDeadLine);
        kapellmeisterEngine.executeAvailableOperationGroups(jobDeadLine, configurationProperties.getMaxCountOfOperationsForIteration());
        log.info("Итерация работы капельмейстера по шедуллеру закончена");
    }
}
