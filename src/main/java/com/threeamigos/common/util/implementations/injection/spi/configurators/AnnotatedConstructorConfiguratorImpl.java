package com.threeamigos.common.util.implementations.injection.spi.configurators;

import jakarta.enterprise.inject.spi.AnnotatedConstructor;
import jakarta.enterprise.inject.spi.AnnotatedParameter;
import jakarta.enterprise.inject.spi.configurator.AnnotatedConstructorConfigurator;
import jakarta.enterprise.inject.spi.configurator.AnnotatedParameterConfigurator;

import java.lang.annotation.Annotation;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Implementation of AnnotatedConstructorConfigurator for modifying constructor metadata.
 *
 * <p>Provides a fluent API for:
 * <ul>
 *   <li>Adding/removing constructor annotations via {@link #add(Annotation)} / {@link #remove(Predicate)}</li>
 *   <li>Configuring constructor parameters via {@link #params()}</li>
 * </ul>
 *
 * @param <T> the declaring type
 * @see AnnotatedConstructorConfigurator
 */
public class AnnotatedConstructorConfiguratorImpl<T> implements AnnotatedConstructorConfigurator<T> {

    private final AnnotatedConstructor<T> originalConstructor;
    private final Set<Annotation> annotations;
    private final List<AnnotatedParameterConfiguratorImpl<T>> parameterConfigurators;

    public AnnotatedConstructorConfiguratorImpl(AnnotatedConstructor<T> constructor) {
        this.originalConstructor = constructor;
        this.annotations = new HashSet<>(constructor.getAnnotations());

        // Create parameter configurators
        this.parameterConfigurators = new ArrayList<>();
        for (AnnotatedParameter<T> param : constructor.getParameters()) {
            this.parameterConfigurators.add(new AnnotatedParameterConfiguratorImpl<>(param));
        }
    }

    @Override
    public AnnotatedConstructorConfigurator<T> add(Annotation annotation) {
        if (annotation != null) {
            annotations.add(annotation);
        }
        return this;
    }

    @Override
    public AnnotatedConstructorConfigurator<T> remove(Predicate<Annotation> predicate) {
        if (predicate != null) {
            annotations.removeIf(predicate);
        }
        return this;
    }

    @Override
    public AnnotatedConstructorConfigurator<T> removeAll() {
        annotations.clear();
        return this;
    }

    @Override
    public AnnotatedConstructor<T> getAnnotated() {
        return originalConstructor;
    }

    @Override
    public List<AnnotatedParameterConfigurator<T>> params() {
        return Collections.unmodifiableList(parameterConfigurators);
    }

    @Override
    public Stream<AnnotatedParameterConfigurator<T>> filterParams(Predicate<AnnotatedParameter<T>> predicate) {
        if (predicate == null) {
            return parameterConfigurators.stream().map(c -> c);
        }
        return parameterConfigurators.stream()
                .filter(configurator -> predicate.test(configurator.getAnnotated()))
                .map(c -> c);
    }

    /**
     * Returns the original annotated constructor.
     *
     * @return the original constructor
     */
    public AnnotatedConstructor<T> getOriginalConstructor() {
        return originalConstructor;
    }

    /**
     * Returns the modified set of annotations.
     *
     * @return modified annotations
     */
    public Set<Annotation> getAnnotations() {
        return new HashSet<>(annotations);
    }

    /**
     * Returns the parameter configurators.
     *
     * @return parameter configurators
     */
    public List<AnnotatedParameterConfiguratorImpl<T>> getParameterConfigurators() {
        return new ArrayList<>(parameterConfigurators);
    }
}
