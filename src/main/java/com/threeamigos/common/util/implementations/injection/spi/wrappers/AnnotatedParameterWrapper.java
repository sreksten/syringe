package com.threeamigos.common.util.implementations.injection.spi.wrappers;

import jakarta.enterprise.inject.spi.AnnotatedCallable;
import jakarta.enterprise.inject.spi.AnnotatedParameter;

import java.lang.annotation.Annotation;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Simple wrapper for Parameter that implements AnnotatedParameter interface.
 * Used by InjectionPointImpl to provide Annotated metadata for parameter injection points.
 *
 * @param <X> the declaring type
 */
public class AnnotatedParameterWrapper<X> implements AnnotatedParameter<X> {

    private final Parameter parameter;
    private final AnnotatedCallable<X> declaringCallable;
    private final int position;

    public AnnotatedParameterWrapper(Parameter parameter, AnnotatedCallable<X> declaringCallable) {
        this.parameter = parameter;
        this.declaringCallable = declaringCallable;

        // Find the position of this parameter in the callable's parameter list
        Parameter[] params = parameter.getDeclaringExecutable().getParameters();
        int pos = -1;
        for (int i = 0; i < params.length; i++) {
            if (params[i].equals(parameter)) {
                pos = i;
                break;
            }
        }
        this.position = pos;
    }

    @Override
    public int getPosition() {
        return position;
    }

    @Override
    public AnnotatedCallable<X> getDeclaringCallable() {
        return declaringCallable;
    }

    @Override
    public Type getBaseType() {
        return parameter.getParameterizedType();
    }

    @Override
    public Set<Type> getTypeClosure() {
        Set<Type> types = new HashSet<>();
        types.add(parameter.getParameterizedType());
        types.add(Object.class);
        return types;
    }

    @Override
    public <T extends Annotation> T getAnnotation(Class<T> annotationType) {
        return parameter.getAnnotation(annotationType);
    }

    @Override
    public Set<Annotation> getAnnotations() {
        return new HashSet<>(Arrays.asList(parameter.getAnnotations()));
    }

    @Override
    public boolean isAnnotationPresent(Class<? extends Annotation> annotationType) {
        return parameter.isAnnotationPresent(annotationType);
    }
}
