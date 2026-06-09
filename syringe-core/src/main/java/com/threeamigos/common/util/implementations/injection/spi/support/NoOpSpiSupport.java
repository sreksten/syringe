package com.threeamigos.common.util.implementations.injection.spi.support;

import com.threeamigos.common.util.implementations.injection.NotEnabledFeatureException;
import com.threeamigos.common.util.implementations.injection.modules.ModulesEnum;
import jakarta.enterprise.inject.spi.AnnotatedType;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanAttributes;
import jakarta.enterprise.inject.spi.InjectionTarget;
import jakarta.enterprise.inject.spi.Producer;
import jakarta.enterprise.inject.spi.configurator.AnnotatedTypeConfigurator;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Set;

/**
 * Fallback SPI support used when syringe-spi-support is not on the classpath.
 */
public class NoOpSpiSupport implements SpiSupport {

    @Override
    public <T> AnnotatedTypeConfigurator<T> createAnnotatedTypeConfigurator(AnnotatedType<T> annotatedType) {
        throw notEnabled("API call found at InjectionTargetFactory.configure()");
    }

    @Override
    public <T> AnnotatedType<T> completeAnnotatedTypeConfigurator(AnnotatedTypeConfigurator<T> configurator) {
        throw notEnabled("Configured AnnotatedType completion requested");
    }

    @Override
    public <T> Bean<T> createSyntheticBean(BeanAttributes<T> attributes,
                                           Class<?> beanClass,
                                           InjectionTarget<T> injectionTarget) {
        throw notEnabled("API call found at BeanManager.createBean(BeanAttributes, Class, InjectionTargetFactory)");
    }

    @Override
    public <T> Bean<T> createSyntheticDecoratorBean(BeanAttributes<T> attributes,
                                                    Class<?> beanClass,
                                                    InjectionTarget<T> injectionTarget,
                                                    Type delegateType,
                                                    Set<Annotation> delegateQualifiers,
                                                    Set<Type> decoratedTypes) {
        throw notEnabled("API call found at BeanManager.createBean(...) for decorator metadata");
    }

    @Override
    public <T, X> Bean<T> createSyntheticProducerBean(BeanAttributes<T> attributes,
                                                      Class<X> beanClass,
                                                      Producer<T> producer) {
        throw notEnabled("API call found at BeanManager.createBean(BeanAttributes, Class, ProducerFactory)");
    }

    private NotEnabledFeatureException notEnabled(String usage) {
        return new NotEnabledFeatureException(
                usage + " but SPI support is not available.",
                ModulesEnum.SPI_SUPPORT);
    }
}
