package com.threeamigos.common.util.implementations.injection.spi.support;

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
 * Optional SPI support bridge loaded via ServiceLoader.
 */
public interface SpiSupport {

    <T> AnnotatedTypeConfigurator<T> createAnnotatedTypeConfigurator(AnnotatedType<T> annotatedType);

    <T> AnnotatedType<T> completeAnnotatedTypeConfigurator(AnnotatedTypeConfigurator<T> configurator);

    <T> Bean<T> createSyntheticBean(BeanAttributes<T> attributes,
                                    Class<?> beanClass,
                                    InjectionTarget<T> injectionTarget);

    <T> Bean<T> createSyntheticDecoratorBean(BeanAttributes<T> attributes,
                                             Class<?> beanClass,
                                             InjectionTarget<T> injectionTarget,
                                             Type delegateType,
                                             Set<Annotation> delegateQualifiers,
                                             Set<Type> decoratedTypes);

    <T, X> Bean<T> createSyntheticProducerBean(BeanAttributes<T> attributes,
                                               Class<X> beanClass,
                                               Producer<T> producer);
}
