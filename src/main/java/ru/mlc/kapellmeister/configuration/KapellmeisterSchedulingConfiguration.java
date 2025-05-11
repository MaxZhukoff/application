package ru.mlc.kapellmeister.configuration;

import lombok.RequiredArgsConstructor;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import ru.mlc.kapellmeister.api.KapellmeisterEngine;
import ru.mlc.kapellmeister.sceduling.KapellmeisterScheduler;
import ru.mlc.kapellmeister.sceduling.KapellmeisterSpringShedlockScheduler;
import ru.mlc.kapellmeister.service.KapellmeisterTimeSynchronizationService;

@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "10m")
@EnableConfigurationProperties(KapellmeisterSchedulingConfigurationProperties.class)
@RequiredArgsConstructor
@ConditionalOnProperty(name = "kapellmeister.scheduling-enabled", havingValue = "true", matchIfMissing = true)
public class KapellmeisterSchedulingConfiguration {

    private final KapellmeisterSchedulingConfigurationProperties schedulingConfigurationProperties;
    private final KapellmeisterConfigurationProperties configurationProperties;

    @Bean
    public KapellmeisterScheduler kapellmeisterScheduler(KapellmeisterEngine kapellmeisterEngine,
                                                         KapellmeisterTimeSynchronizationService kapellmeisterTimeSynchronizationService) {
        return new KapellmeisterSpringShedlockScheduler(kapellmeisterEngine,
                schedulingConfigurationProperties,
                configurationProperties,
                kapellmeisterTimeSynchronizationService);
    }
}
