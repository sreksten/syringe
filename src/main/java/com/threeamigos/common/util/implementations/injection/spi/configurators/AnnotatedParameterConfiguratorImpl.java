package com.threeamigos.common.util.implementations.injection.spi.configurators;

import jakarta.enterprise.inject.spi.AnnotatedParameter;
import jakarta.enterprise.inject.spi.configurator.AnnotatedParameterConfigurator;

import java.lang.annotation.Annotation;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Implementation of AnnotatedParameterConfigurator for modifying parameter metadata.
 *
 * <p>Provides a fluent API for:
 * <ul>
 *   <li>Adding annotations via {@link #add(Annotation)}</li>
 *   <li>Removing annotations via {@link #remove(Predicate)}</li>
 *   <li>Removing all annotations via {@link #removeAll()}</li>
 * </ul>
 *
 * @param <T> the declaring type
 * @see AnnotatedParameterConfigurator
 */
public class AnnotatedParameterConfiguratorImpl<T> implements AnnotatedParameterConfigurator<T> {

    private final AnnotatedParameter<T> originalParameter;
    private final Set<Annotation> annotations;

    public AnnotatedParameterConfiguratorImpl(AnnotatedParameter<T> parameter) {
        this.originalParameter = parameter;
        this.annotations = new HashSet<>(parameter.getAnnotations());
    }

    @Override
    public AnnotatedParameterConfigurator<T> add(Annotation annotation) {
        if (annotation != null) {
            annotations.add(annotation);
        }
        return this;
    }

    @Override
    public AnnotatedParameterConfigurator<T> remove(Predicate<Annotation> predicate) {
        if (predicate != null) {
            annotations.removeIf(predicate);
        }
        return this;
    }

    @Override
    public AnnotatedParameterConfigurator<T> removeAll() {
        annotations.clear();
        return this;
    }

    @Override
    public AnnotatedParameter<T> getAnnotated() {
        return originalParameter;
    }

    /**
     * Returns the original annotated parameter.
     *
     * @return the original parameter
     */
    public AnnotatedParameter<T> getOriginalParameter() {
        return originalParameter;
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
