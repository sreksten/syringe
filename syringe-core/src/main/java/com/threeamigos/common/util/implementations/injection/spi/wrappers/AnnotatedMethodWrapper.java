package com.threeamigos.common.util.implementations.injection.spi.wrappers;

import jakarta.enterprise.inject.spi.AnnotatedMethod;
import jakarta.enterprise.inject.spi.AnnotatedParameter;
import jakarta.enterprise.inject.spi.AnnotatedType;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Simple wrapper for Method implementing AnnotatedMethod.
 *
 * @param <X> declaring type
 */
public class AnnotatedMethodWrapper<X> implements AnnotatedMethod<X> {

    private final Method method;
    private final AnnotatedType<X> declaringType;
    private final List<AnnotatedParameter<X>> parameters;

    public AnnotatedMethodWrapper(Method method, AnnotatedType<X> declaringType) {
        this.method = method;
        this.declaringType = declaringType;
        this.parameters = Arrays.stream(method.getParameters())
                .map(p -> new AnnotatedParameterWrapper<>(p, this))
                .collect(Collectors.toList());
    }

    @Override
    public Method getJavaMember() {
        return method;
    }

    @Override
    public boolean isStatic() {
        return java.lang.reflect.Modifier.isStatic(method.getModifiers());
    }

    @Override
    public AnnotatedType<X> getDeclaringType() {
        return declaringType;
    }

    @Override
    public Type getBaseType() {
        return method.getGenericReturnType();
    }

    @Override
    public Set<Type> getTypeClosure() {
        Set<Type> types = new HashSet<>();
        types.add(method.getGenericReturnType());
        types.add(Object.class);
        return types;
    }

    @Override
    public List<AnnotatedParameter<X>> getParameters() {
        return parameters;
    }

    @Override
    public <T extends Annotation> T getAnnotation(Class<T> annotationType) {
        return method.getAnnotation(annotationType);
    }

    @Override
    public Set<Annotation> getAnnotations() {
        return new HashSet<>(Arrays.asList(method.getAnnotations()));
    }

    @Override
    public boolean isAnnotationPresent(Class<? extends Annotation> annotationType) {
        return method.isAnnotationPresent(annotationType);
    }
}
