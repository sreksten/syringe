package com.threeamigos.common.util.implementations.injection.spi.configurators;

import jakarta.enterprise.inject.spi.AnnotatedMethod;
import jakarta.enterprise.inject.spi.AnnotatedParameter;
import jakarta.enterprise.inject.spi.configurator.AnnotatedMethodConfigurator;
import jakarta.enterprise.inject.spi.configurator.AnnotatedParameterConfigurator;

import java.lang.annotation.Annotation;
import java.util.*;
import java.util.function.Predicate;

/**
 * Implementation of AnnotatedMethodConfigurator for modifying method metadata.
 *
 * <p>Provides a fluent API for:
 * <ul>
 *   <li>Adding/removing method annotations via {@link #add(Annotation)} / {@link #remove(Predicate)}</li>
 *   <li>Configuring method parameters via {@link #params()}</li>
 * </ul>
 *
 * @param <T> the declaring type
 * @see AnnotatedMethodConfigurator
 */
public class AnnotatedMethodConfiguratorImpl<T> implements AnnotatedMethodConfigurator<T> {

    private final AnnotatedMethod<T> originalMethod;
    private final Set<Annotation> annotations;
    private final List<AnnotatedParameterConfiguratorImpl<T>> parameterConfigurators;

    public AnnotatedMethodConfiguratorImpl(AnnotatedMethod<T> method) {
        this.originalMethod = method;
        this.annotations = new HashSet<>(method.getAnnotations());

        // Create parameter configurators
        this.parameterConfigurators = new ArrayList<>();
        for (AnnotatedParameter<T> param : method.getParameters()) {
            this.parameterConfigurators.add(new AnnotatedParameterConfiguratorImpl<>(param));
        }
    }

    @Override
    public AnnotatedMethodConfigurator<T> add(Annotation annotation) {
        if (annotation != null) {
            annotations.add(annotation);
        }
        return this;
    }

    @Override
    public AnnotatedMethodConfigurator<T> remove(Predicate<Annotation> predicate) {
        if (predicate != null) {
            annotations.removeIf(predicate);
        }
        return this;
    }

    @Override
    public AnnotatedMethodConfigurator<T> removeAll() {
        annotations.clear();
        return this;
    }

    @Override
    public AnnotatedMethod<T> getAnnotated() {
        return originalMethod;
    }

    @Override
    public List<AnnotatedParameterConfigurator<T>> params() {
        return Collections.unmodifiableList(parameterConfigurators);
    }

    @Override
    public java.util.stream.Stream<AnnotatedParameterConfigurator<T>> filterParams(Predicate<AnnotatedParameter<T>> predicate) {
        if (predicate == null) {
            return parameterConfigurators.stream().map(c -> c);
        }
        return parameterConfigurators.stream()
                .filter(configurator -> predicate.test(configurator.getAnnotated()))
                .map(c -> c);
    }

    /**
     * Returns the original annotated method.
     *
     * @return the original method
     */
    public AnnotatedMethod<T> getOriginalMethod() {
        return originalMethod;
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
