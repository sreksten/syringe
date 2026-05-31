package com.threeamigos.common.util.implementations.injection.spi.configured;

import jakarta.enterprise.inject.spi.AnnotatedCallable;
import jakarta.enterprise.inject.spi.AnnotatedParameter;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

class ConfiguredAnnotatedParameter<X> implements AnnotatedParameter<X> {
    private final AnnotatedParameter<X> delegate;
    private final Set<Annotation> annotations;
    private final AnnotatedCallable<X> declaringCallable;

    ConfiguredAnnotatedParameter(AnnotatedParameter<X> delegate,
                                 Set<Annotation> annotations,
                                 AnnotatedCallable<X> declaringCallable) {
        this.delegate = delegate;
        this.annotations = Collections.unmodifiableSet(new HashSet<>(annotations));
        this.declaringCallable = declaringCallable;
    }

    @Override
    public int getPosition() {
        return delegate.getPosition();
    }

    @Override
    public AnnotatedCallable<X> getDeclaringCallable() {
        return declaringCallable;
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
