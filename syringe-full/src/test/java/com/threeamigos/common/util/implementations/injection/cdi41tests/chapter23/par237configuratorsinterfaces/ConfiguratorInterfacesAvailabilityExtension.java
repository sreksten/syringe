package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter23.par237configuratorsinterfaces;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.spi.AfterBeanDiscovery;
import jakarta.enterprise.inject.spi.AfterTypeDiscovery;
import jakarta.enterprise.inject.spi.BeforeBeanDiscovery;
import jakarta.enterprise.inject.spi.Extension;
import jakarta.enterprise.inject.spi.ProcessAnnotatedType;
import jakarta.enterprise.inject.spi.ProcessBeanAttributes;
import jakarta.enterprise.inject.spi.ProcessInjectionPoint;
import jakarta.enterprise.inject.spi.ProcessProducer;
import jakarta.enterprise.inject.spi.configurator.AnnotatedTypeConfigurator;
import jakarta.enterprise.inject.spi.configurator.BeanAttributesConfigurator;
import jakarta.enterprise.inject.spi.configurator.BeanConfigurator;
import jakarta.enterprise.inject.spi.configurator.InjectionPointConfigurator;
import jakarta.enterprise.inject.spi.configurator.ObserverMethodConfigurator;
import jakarta.enterprise.inject.spi.configurator.ProducerConfigurator;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@SuppressWarnings("rawtypes")
public class ConfiguratorInterfacesAvailabilityExtension implements Extension {
    static AnnotatedTypeConfigurator<?> annotatedTypeConfiguratorFromBeforeBeanDiscovery;
    static AnnotatedTypeConfigurator<?> qualifierConfiguratorFromBeforeBeanDiscovery;
    static AnnotatedTypeConfigurator<?> interceptorBindingConfiguratorFromBeforeBeanDiscovery;
    static AnnotatedTypeConfigurator<?> annotatedTypeConfiguratorFromAfterTypeDiscovery;
    static AnnotatedTypeConfigurator<?> annotatedTypeConfiguratorFromProcessAnnotatedType;
    static InjectionPointConfigurator injectionPointConfigurator;
    static BeanAttributesConfigurator<?> beanAttributesConfigurator;
    static BeanConfigurator<?> beanConfigurator;
    static ObserverMethodConfigurator<?> observerMethodConfigurator;
    static ProducerConfigurator<?> producerConfigurator;

    public static void reset() {
        annotatedTypeConfiguratorFromBeforeBeanDiscovery = null;
        qualifierConfiguratorFromBeforeBeanDiscovery = null;
        interceptorBindingConfiguratorFromBeforeBeanDiscovery = null;
        annotatedTypeConfiguratorFromAfterTypeDiscovery = null;
        annotatedTypeConfiguratorFromProcessAnnotatedType = null;
        injectionPointConfigurator = null;
        beanAttributesConfigurator = null;
        beanConfigurator = null;
        observerMethodConfigurator = null;
        producerConfigurator = null;
    }

    public void beforeBeanDiscovery(@Observes BeforeBeanDiscovery event) {
        annotatedTypeConfiguratorFromBeforeBeanDiscovery =
                event.addAnnotatedType(AddedInBeforeBeanDiscovery.class, "cfg-23.7-bbd");
        qualifierConfiguratorFromBeforeBeanDiscovery = event.configureQualifier(ConfigQualifier.class);
        interceptorBindingConfiguratorFromBeforeBeanDiscovery =
                event.configureInterceptorBinding(ConfigBinding.class);
    }

    public void afterTypeDiscovery(@Observes AfterTypeDiscovery event) {
        annotatedTypeConfiguratorFromAfterTypeDiscovery =
                event.addAnnotatedType(AddedInAfterTypeDiscovery.class, "cfg-23.7-atd");
    }

    public <T> void processAnnotatedType(@Observes ProcessAnnotatedType<T> event) {
        if (annotatedTypeConfiguratorFromProcessAnnotatedType == null) {
            annotatedTypeConfiguratorFromProcessAnnotatedType = event.configureAnnotatedType();
        }
    }

    public <T, X> void processInjectionPoint(@Observes ProcessInjectionPoint<T, X> event) {
        if (injectionPointConfigurator == null) {
            injectionPointConfigurator = event.configureInjectionPoint();
        }
    }

    public <T> void processBeanAttributes(@Observes ProcessBeanAttributes<T> event) {
        if (beanAttributesConfigurator == null) {
            beanAttributesConfigurator = event.configureBeanAttributes();
        }
    }

    public <T, X> void processProducer(@Observes ProcessProducer<T, X> event) {
        if (producerConfigurator == null) {
            producerConfigurator = event.configureProducer();
        }
    }

    public void afterBeanDiscovery(@Observes AfterBeanDiscovery event) {
        BeanConfigurator beanCfg = event.addBean();
        beanCfg.beanClass(SyntheticBeanFromConfigurator.class)
                .scope(Dependent.class)
                .types(SyntheticBeanFromConfigurator.class, Object.class)
                .createWith(context -> new SyntheticBeanFromConfigurator());
        beanConfigurator = beanCfg;

        ObserverMethodConfigurator<?> observerCfg = event.addObserverMethod();
        observerCfg.beanClass(ConfiguratorInterfacesAvailabilityExtension.class)
                .observedType(Object.class)
                .notifyWith(context -> { });
        observerMethodConfigurator = observerCfg;
    }

    public static class SyntheticBeanFromConfigurator {
    }

    public static class AddedInBeforeBeanDiscovery {
    }

    public static class AddedInAfterTypeDiscovery {
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
    public @interface ConfigQualifier {
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.FIELD, ElementType.METHOD})
    public @interface ConfigBinding {
    }
}
