package com.threeamigos.common.util.implementations.injection.spi;

import jakarta.enterprise.inject.spi.BeanAttributes;
import jakarta.enterprise.inject.spi.Decorator;
import jakarta.enterprise.inject.spi.InjectionTarget;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Synthetic bean variant that also exposes decorator metadata.
 */
public class SyntheticDecoratorBeanImpl<T> extends SyntheticBeanImpl<T> implements Decorator<T> {

    private final Type delegateType;
    private final Set<Annotation> delegateQualifiers;
    private final Set<Type> decoratedTypes;

    public SyntheticDecoratorBeanImpl(BeanAttributes<T> attributes,
                                      Class<?> beanClass,
                                      InjectionTarget<T> injectionTarget,
                                      Type delegateType,
                                      Set<Annotation> delegateQualifiers,
                                      Set<Type> decoratedTypes) {
        super(attributes, beanClass, injectionTarget);
        this.delegateType = delegateType;
        this.delegateQualifiers = Collections.unmodifiableSet(new LinkedHashSet<>(delegateQualifiers));
        this.decoratedTypes = Collections.unmodifiableSet(new LinkedHashSet<>(decoratedTypes));
    }

    @Override
    public Type getDelegateType() {
        return delegateType;
    }

    @Override
    public Set<Annotation> getDelegateQualifiers() {
        return delegateQualifiers;
    }

    @Override
    public Set<Type> getDecoratedTypes() {
        return decoratedTypes;
    }
}
