package com.threeamigos.common.util.implementations.injection.spi.configurators;

import jakarta.enterprise.inject.spi.AnnotatedField;
import jakarta.enterprise.inject.spi.configurator.AnnotatedFieldConfigurator;

import java.lang.annotation.Annotation;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Implementation of AnnotatedFieldConfigurator for modifying field metadata.
 *
 * <p>Provides a fluent API for:
 * <ul>
 *   <li>Adding annotations via {@link #add(Annotation)}</li>
 *   <li>Removing annotations via {@link #remove(Predicate)}</li>
 *   <li>Removing all annotations via {@link #removeAll()}</li>
 * </ul>
 *
 * @param <T> the declaring type
 * @see AnnotatedFieldConfigurator
 */
public class AnnotatedFieldConfiguratorImpl<T> implements AnnotatedFieldConfigurator<T> {

    private final AnnotatedField<T> originalField;
    private final Set<Annotation> annotations;

    public AnnotatedFieldConfiguratorImpl(AnnotatedField<T> field) {
        this.originalField = field;
        this.annotations = new HashSet<>(field.getAnnotations());
    }

    @Override
    public AnnotatedFieldConfigurator<T> add(Annotation annotation) {
        if (annotation != null) {
            annotations.add(annotation);
        }
        return this;
    }

    @Override
    public AnnotatedFieldConfigurator<T> remove(Predicate<Annotation> predicate) {
        if (predicate != null) {
            annotations.removeIf(predicate);
        }
        return this;
    }

    @Override
    public AnnotatedFieldConfigurator<T> removeAll() {
        annotations.clear();
        return this;
    }

    @Override
    public AnnotatedField<T> getAnnotated() {
        return originalField;
    }

    /**
     * Returns the original annotated field.
     *
     * @return the original field
     */
    public AnnotatedField<T> getOriginalField() {
        return originalField;
    }

    /**
     * Returns the modified set of annotations.
     *
     * @return modified annotations
     */
    public Set<Annotation> getAnnotations() {
        return new HashSet<>(annotations);
    }
}
