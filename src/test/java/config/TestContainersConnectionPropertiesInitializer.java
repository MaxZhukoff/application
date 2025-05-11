package config;

import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import ru.mlc.test.extensions.testcontainers.MlcContainerFactories;
import ru.mlc.test.extensions.testcontainers.TestContainerRunner;

/**
 * Контекст инициализатор, настраивающий приложение на использование TestContainers
 */
public class TestContainersConnectionPropertiesInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        TestContainerRunner.ifRun(MlcContainerFactories.Postgres.class,
                postgres -> TestPropertyValues.of(
                        "spring.datasource.url=" + postgres.getConnectionUrl(),
                        "spring.datasource.username=" + postgres.getUsername(),
                        "spring.datasource.password=" + postgres.getPassword()
                ).applyTo(context)
        );
    }
}
