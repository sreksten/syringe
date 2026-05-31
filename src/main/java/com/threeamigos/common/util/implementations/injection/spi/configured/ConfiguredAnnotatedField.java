package com.threeamigos.common.util.implementations.injection.spi.configured;

import jakarta.enterprise.inject.spi.AnnotatedField;
import jakarta.enterprise.inject.spi.AnnotatedType;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

class ConfiguredAnnotatedField<X> implements AnnotatedField<X> {
    private final AnnotatedField<X> delegate;
    private final Set<Annotation> annotations;
    private final AnnotatedType<X> declaringType;

    ConfiguredAnnotatedField(AnnotatedField<X> delegate,
                             Set<Annotation> annotations,
                             AnnotatedType<X> declaringType) {
        this.delegate = delegate;
        this.annotations = Collections.unmodifiableSet(new HashSet<>(annotations));
        this.declaringType = declaringType;
    }

    @Override
    public java.lang.reflect.Field getJavaMember() {
        return delegate.getJavaMember();
    }

    @Override
    public boolean isStatic() {
        return delegate.isStatic();
    }

    @Override
    public AnnotatedType<X> getDeclaringType() {
        return declaringType;
    }

    @Override
    public Type getBaseType() {
        return delegate.getBaseType();
    }

    @Override
    public Set<Type> getTypeClosure() {
        return delegate.getTypeClosure();
    }

    @Override
    public <T extends Annotation> T getAnnotation(Class<T> annotationType) {
        for (Annotation annotation : annotations) {
            if (annotationType.isInstance(annotation)) {
                return annotationType.cast(annotation);
            }
        }
        return null;
    }

    @Override
    public Set<Annotation> getAnnotations() {
        return annotations;
    }

    @Override
    public boolean isAnnotationPresent(Class<? extends Annotation> annotationType) {
        return getAnnotation(annotationType) != null;
    }
}
