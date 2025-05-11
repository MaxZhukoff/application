package ru.mlc.kapellmeister.configuration;


import config.TestContainersConnectionPropertiesInitializer;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.properties.PropertyMapping;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.annotation.AliasFor;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.jdbc.Sql;
import ru.mlc.test.extensions.testcontainers.EnableTestContainers;
import ru.mlc.test.extensions.testcontainers.MlcContainerFactories;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@EnableTestContainers(factory = MlcContainerFactories.Postgres.class)
@SpringBootTest(
        classes = KapellmeisterConfiguration.class,
        properties = {
                "kapellmeister.afterCommitExecutionEnabled=false",
                "kapellmeister.scheduling-enabled=false",
                "kapellmeister.optimization-enabled=true",
                "kapellmeister.executing-enabled=true",
                "kapellmeister.count-of-operations-for-iteration=-1"
        })
@EnableAutoConfiguration
@ContextConfiguration(
        initializers = TestContainersConnectionPropertiesInitializer.class)
@Sql(scripts = "classpath:clear_all.sql")
public @interface KapellmeisterIntegrationTest {

    @AliasFor(annotation = ContextConfiguration.class, attribute = "classes")
    Class<?>[] value() default {};

    @PropertyMapping("spring.jpa.properties.hibernate.enable_lazy_load_no_trans")
    boolean lazyCollectionWithOutTransaction() default true;
}
