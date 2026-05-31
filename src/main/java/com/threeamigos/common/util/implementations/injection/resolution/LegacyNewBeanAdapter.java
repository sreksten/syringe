package com.threeamigos.common.util.implementations.injection.resolution;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.InjectionPoint;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Set;

/**
 * Adapter that exposes an existing bean with legacy {@code @New} semantics.
 * Scope is always {@code @Dependent} and instance creation delegates to the original bean.
 */
public final class LegacyNewBeanAdapter<T> implements Bean<T> {

    private final Bean<T> delegate;

    public LegacyNewBeanAdapter(Bean<T> delegate) {
        this.delegate = delegate;
    }

    @Override
    public Class<?> getBeanClass() {
        return delegate.getBeanClass();
    }

    @Override
    public Set<InjectionPoint> getInjectionPoints() {
        return delegate.getInjectionPoints();
    }

    @Override
    public String getName() {
        return delegate.getName();
    }

    @Override
    public Set<Annotation> getQualifiers() {
        return delegate.getQualifiers();
    }

    @Override
    public Class<? extends Annotation> getScope() {
        return Dependent.class;
    }

    @Override
    public Set<Class<? extends Annotation>> getStereotypes() {
        return delegate.getStereotypes();
    }

    @Override
    public Set<Type> getTypes() {
        return delegate.getTypes();
    }

    @Override
    public boolean isAlternative() {
        return delegate.isAlternative();
    }

    @Override
    public T create(CreationalContext<T> creationalContext) {
        return delegate.create(creationalContext);
    }

    @Override
    public void destroy(T instance, CreationalContext<T> creationalContext) {
        delegate.destroy(instance, creationalContext);
    }
}
