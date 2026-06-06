package com.threeamigos.common.util.implementations.injection.spi.wrappers;

import jakarta.enterprise.inject.spi.AnnotatedField;
import jakarta.enterprise.inject.spi.AnnotatedType;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Simple wrapper for Field that implements AnnotatedField interface.
 * Used by InjectionPointImpl to provide Annotated metadata for field injection points.
 *
 * @param <X> the declaring type
 */
public class AnnotatedFieldWrapper<X> implements AnnotatedField<X> {

    private final Field field;
    private final AnnotatedType<X> declaringType;

    public AnnotatedFieldWrapper(Field field, AnnotatedType<X> declaringType) {
        this.field = field;
        this.declaringType = declaringType;
    }

    @Override
    public Field getJavaMember() {
        return field;
    }

    @Override
    public boolean isStatic() {
        return java.lang.reflect.Modifier.isStatic(field.getModifiers());
    }

    @Override
    public AnnotatedType<X> getDeclaringType() {
        return declaringType;
    }

    @Override
    public Type getBaseType() {
        return field.getGenericType();
    }

    @Override
    public Set<Type> getTypeClosure() {
        Set<Type> types = new HashSet<>();
        types.add(field.getGenericType());
        types.add(Object.class);
        return types;
    }

    @Override
    public <T extends Annotation> T getAnnotation(Class<T> annotationType) {
        return field.getAnnotation(annotationType);
    }

    @Override
    public Set<Annotation> getAnnotations() {
        return new HashSet<>(Arrays.asList(field.getAnnotations()));
    }

    @Override
    public boolean isAnnotationPresent(Class<? extends Annotation> annotationType) {
        return field.isAnnotationPresent(annotationType);
    }
}
