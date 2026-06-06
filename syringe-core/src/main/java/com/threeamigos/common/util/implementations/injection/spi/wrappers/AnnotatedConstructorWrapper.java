package com.threeamigos.common.util.implementations.injection.spi.wrappers;

import jakarta.enterprise.inject.spi.AnnotatedConstructor;
import jakarta.enterprise.inject.spi.AnnotatedParameter;
import jakarta.enterprise.inject.spi.AnnotatedType;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Simple wrapper for Constructor implementing AnnotatedConstructor.
 *
 * @param <X> declaring type
 */
public class AnnotatedConstructorWrapper<X> implements AnnotatedConstructor<X> {

    private final Constructor<X> constructor;
    private final AnnotatedType<X> declaringType;
    private final List<AnnotatedParameter<X>> parameters;

    public AnnotatedConstructorWrapper(Constructor<X> constructor, AnnotatedType<X> declaringType) {
        this.constructor = constructor;
        this.declaringType = declaringType;
        this.parameters = Arrays.stream(constructor.getParameters())
                .map(p -> new AnnotatedParameterWrapper<>(p, this))
                .collect(Collectors.toList());
    }

    @Override
    public Constructor<X> getJavaMember() {
        return constructor;
    }

    @Override
    public boolean isStatic() {
        return false;
    }

    @Override
    public AnnotatedType<X> getDeclaringType() {
        return declaringType;
    }

    @Override
    public Type getBaseType() {
        return constructor.getDeclaringClass();
    }

    @Override
    public Set<Type> getTypeClosure() {
        Set<Type> types = new HashSet<>();
        types.add(constructor.getDeclaringClass());
        types.add(Object.class);
        return types;
    }

    @Override
    public List<AnnotatedParameter<X>> getParameters() {
        return parameters;
    }

    @Override
    public <T extends Annotation> T getAnnotation(Class<T> annotationType) {
        return constructor.getAnnotation(annotationType);
    }

    @Override
    public Set<Annotation> getAnnotations() {
        return new HashSet<>(Arrays.asList(constructor.getAnnotations()));
    }

    @Override
    public boolean isAnnotationPresent(Class<? extends Annotation> annotationType) {
        return constructor.isAnnotationPresent(annotationType);
    }
}
