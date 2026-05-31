package com.threeamigos.common.util.implementations.injection.discovery.validation;

import jakarta.enterprise.inject.spi.Annotated;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.InjectionPoint;

import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.lang.reflect.Member;
import java.lang.reflect.Type;
import java.util.Set;

final class ResolvedInjectionPoint implements InjectionPoint, Serializable {
    private static final long serialVersionUID = 1L;
    private final InjectionPoint delegate;
    private final Type resolvedType;

    ResolvedInjectionPoint(InjectionPoint delegate, Type resolvedType) {
        this.delegate = delegate;
        this.resolvedType = resolvedType;
    }

    public Type getType() {
        return resolvedType != null ? resolvedType : delegate.getType();
    }

    public Set<Annotation> getQualifiers() {
        return delegate.getQualifiers();
    }

    public Bean<?> getBean() {
        return delegate.getBean();
    }

    public Member getMember() {
        return delegate.getMember();
    }

    public Annotated getAnnotated() {
        return delegate.getAnnotated();
    }

    public boolean isDelegate() {
        return delegate.isDelegate();
    }

    public boolean isTransient() {
        return delegate.isTransient();
    }
}
