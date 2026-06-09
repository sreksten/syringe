package com.threeamigos.common.util.implementations.injection.spi.support;

import com.threeamigos.common.util.implementations.injection.spi.SyntheticBeanImpl;
import com.threeamigos.common.util.implementations.injection.spi.SyntheticDecoratorBeanImpl;
import com.threeamigos.common.util.implementations.injection.spi.SyntheticProducerBeanImpl;
import com.threeamigos.common.util.implementations.injection.spi.configurators.AnnotatedTypeConfiguratorImpl;
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
 * Default SPI support implementation backed by syringe-spi-support classes.
 */
public class SpiSupportImpl implements SpiSupport {

    @Override
    public <T> AnnotatedTypeConfigurator<T> createAnnotatedTypeConfigurator(AnnotatedType<T> annotatedType) {
        return new AnnotatedTypeConfiguratorImpl<>(annotatedType);
    }

    @Override
    public <T> AnnotatedType<T> completeAnnotatedTypeConfigurator(AnnotatedTypeConfigurator<T> configurator) {
        if (!(configurator instanceof AnnotatedTypeConfiguratorImpl)) {
            throw new IllegalArgumentException("Unsupported AnnotatedTypeConfigurator implementation: " +
                    (configurator == null ? "null" : configurator.getClass().getName()));
        }
        return ((AnnotatedTypeConfiguratorImpl<T>) configurator).complete();
    }

    @Override
    public <T> Bean<T> createSyntheticBean(BeanAttributes<T> attributes,
                                           Class<?> beanClass,
                                           InjectionTarget<T> injectionTarget) {
        return new SyntheticBeanImpl<>(attributes, beanClass, injectionTarget);
    }

    @Override
    public <T> Bean<T> createSyntheticDecoratorBean(BeanAttributes<T> attributes,
                                                    Class<?> beanClass,
                                                    InjectionTarget<T> injectionTarget,
                                                    Type delegateType,
                                                    Set<Annotation> delegateQualifiers,
                                                    Set<Type> decoratedTypes) {
        return new SyntheticDecoratorBeanImpl<>(
                attributes,
                beanClass,
                injectionTarget,
                delegateType,
                delegateQualifiers,
                decoratedTypes
        );
    }

    @Override
    public <T, X> Bean<T> createSyntheticProducerBean(BeanAttributes<T> attributes,
                                                      Class<X> beanClass,
                                                      Producer<T> producer) {
        return new SyntheticProducerBeanImpl<>(attributes, beanClass, producer);
    }
}
