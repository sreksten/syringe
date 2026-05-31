package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter23.par237configuratorsinterfaces;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.discovery.BeanArchiveMode;
import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@DisplayName("23.7 - Configurator interfaces")
public class ConfiguratorInterfacesTest {

    @Test
    @DisplayName("23.7 - Container provides all CDI 2.0 configurators via matching lifecycle events")
    void shouldProvideAllConfiguratorsInMatchingLifecycleEvents() {
        ConfiguratorInterfacesAvailabilityExtension.reset();

        Syringe syringe = new Syringe(
                new InMemoryMessageHandler(),
                ObservedType.class,
                InjectionConsumer.class,
                InjectionDependency.class,
                ProducerOwner.class,
                ProducedType.class
        );
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        syringe.addExtension(ConfiguratorInterfacesAvailabilityExtension.class.getName());
        syringe.setup();

        assertAll(
                () -> assertNotNull(ConfiguratorInterfacesAvailabilityExtension.annotatedTypeConfiguratorFromBeforeBeanDiscovery),
                () -> assertNotNull(ConfiguratorInterfacesAvailabilityExtension.qualifierConfiguratorFromBeforeBeanDiscovery),
                () -> assertNotNull(ConfiguratorInterfacesAvailabilityExtension.interceptorBindingConfiguratorFromBeforeBeanDiscovery),
                () -> assertNotNull(ConfiguratorInterfacesAvailabilityExtension.annotatedTypeConfiguratorFromAfterTypeDiscovery),
                () -> assertNotNull(ConfiguratorInterfacesAvailabilityExtension.annotatedTypeConfiguratorFromProcessAnnotatedType),
                () -> assertNotNull(ConfiguratorInterfacesAvailabilityExtension.injectionPointConfigurator),
                () -> assertNotNull(ConfiguratorInterfacesAvailabilityExtension.beanAttributesConfigurator),
                () -> assertNotNull(ConfiguratorInterfacesAvailabilityExtension.beanConfigurator),
                () -> assertNotNull(ConfiguratorInterfacesAvailabilityExtension.observerMethodConfigurator),
                () -> assertNotNull(ConfiguratorInterfacesAvailabilityExtension.producerConfigurator)
        );
    }

    @Dependent
    public static class ObservedType {
    }

    @Dependent
    public static class InjectionDependency {
    }

    @Dependent
    public static class InjectionConsumer {
        @Inject
        InjectionDependency dependency;
    }

    @Dependent
    public static class ProducerOwner {
        @Produces
        ProducedType produce() {
            return new ProducedType();
        }
    }

    public static class ProducedType {
    }

}
