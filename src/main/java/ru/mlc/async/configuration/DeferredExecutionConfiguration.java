package ru.mlc.async.configuration;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;
import ru.mlc.async.DeferredExecutionService;

@EnableAsync
public class DeferredExecutionConfiguration {

    @Bean
    public DeferredExecutionService deferredExecutionService(ApplicationEventPublisher publisher) {
        return new DeferredExecutionService(publisher);
    }
}
