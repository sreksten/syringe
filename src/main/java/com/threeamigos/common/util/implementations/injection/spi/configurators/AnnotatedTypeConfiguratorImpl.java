package com.threeamigos.common.util.implementations.injection.spi.configurators;

import com.threeamigos.common.util.implementations.injection.spi.configured.ConfiguredAnnotatedType;
import jakarta.enterprise.inject.spi.*;
import jakarta.enterprise.inject.spi.configurator.*;
import java.lang.annotation.Annotation;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Implementation of AnnotatedTypeConfigurator for modifying type metadata.
 *
 * <p>Provides a fluent API for:
 * <ul>
 *   <li>Adding/removing type-level annotations via {@link #add(Annotation)} / {@link #remove(Predicate)}</li>
 *   <li>Configuring fields via {@link #fields()}, {@link #filterFields(Predicate)}</li>
 *   <li>Configuring methods via {@link #methods()}, {@link #filterMethods(Predicate)}</li>
 *   <li>Configuring constructors via {@link #constructors()}, {@link #filterConstructors(Predicate)}</li>
 * </ul>
 *
 * @param <T> the type being configured
 * @see AnnotatedTypeConfigurator
 */
public class AnnotatedTypeConfiguratorImpl<T> implements AnnotatedTypeConfigurator<T> {

    private final AnnotatedType<T> originalType;
    private final Set<Annotation> annotations;
    private final Set<AnnotatedFieldConfiguratorImpl<? super T>> fieldConfigurators;
    private final Set<AnnotatedMethodConfiguratorImpl<? super T>> methodConfigurators;
    private final Set<AnnotatedConstructorConfiguratorImpl<T>> constructorConfigurators;

    public AnnotatedTypeConfiguratorImpl(AnnotatedType<T> type) {
        this.originalType = type;
        this.annotations = new HashSet<>(type.getAnnotations());

        // Create configurators for all fields
        this.fieldConfigurators = new HashSet<>();
        for (AnnotatedField<? super T> field : type.getFields()) {
            this.fieldConfigurators.add(new AnnotatedFieldConfiguratorImpl<>(field));
        }

        // Create configurators for all methods
        this.methodConfigurators = new HashSet<>();
        for (AnnotatedMethod<? super T> method : type.getMethods()) {
            this.methodConfigurators.add(new AnnotatedMethodConfiguratorImpl<>(method));
        }

        // Create configurators for all constructors
        this.constructorConfigurators = new HashSet<>();
        for (AnnotatedConstructor<T> constructor : type.getConstructors()) {
            this.constructorConfigurators.add(new AnnotatedConstructorConfiguratorImpl<>(constructor));
        }
    }

    @Override
    public AnnotatedType<T> getAnnotated() {
        return originalType;
    }

    @Override
    public AnnotatedTypeConfigurator<T> add(Annotation annotation) {
        if (annotation != null) {
            annotations.add(annotation);
        }
        return this;
    }

    @Override
    public AnnotatedTypeConfigurator<T> remove(Predicate<Annotation> predicate) {
        if (predicate != null) {
            annotations.removeIf(predicate);
        }
        return this;
    }

    @Override
    public AnnotatedTypeConfigurator<T> removeAll() {
        annotations.clear();
        return this;
    }

    @Override
    public Set<AnnotatedFieldConfigurator<? super T>> fields() {
        return Collections.unmodifiableSet(fieldConfigurators);
    }

    @Override
    public Set<AnnotatedMethodConfigurator<? super T>> methods() {
        return Collections.unmodifiableSet(methodConfigurators);
    }

    @Override
    public Set<AnnotatedConstructorConfigurator<T>> constructors() {
        return Collections.unmodifiableSet(constructorConfigurators);
    }

    @Override
    public java.util.stream.Stream<AnnotatedFieldConfigurator<? super T>> filterFields(Predicate<AnnotatedField<? super T>> predicate) {
        if (predicate == null) {
            return fieldConfigurators.stream().map(Function.identity());
        }
        return fieldConfigurators.stream()
                .filter(configurator -> predicate.test(configurator.getAnnotated()))
                .map(Function.identity());
    }

    @Override
    public java.util.stream.Stream<AnnotatedMethodConfigurator<? super T>> filterMethods(Predicate<AnnotatedMethod<? super T>> predicate) {
        if (predicate == null) {
            return methodConfigurators.stream().map(Function.identity());
        }
        return methodConfigurators.stream()
                .filter(configurator -> predicate.test(configurator.getAnnotated()))
                .map(Function.identity());
    }

    @Override
    public java.util.stream.Stream<AnnotatedConstructorConfigurator<T>> filterConstructors(Predicate<AnnotatedConstructor<T>> predicate) {
        if (predicate == null) {
            return constructorConfigurators.stream().map(Function.identity());
        }
        return constructorConfigurators.stream()
                .filter(configurator -> predicate.test(configurator.getAnnotated()))
                .map(Function.identity());
    }

    /**
     * Returns the modified set of type-level annotations.
     *
     * @return modified annotations
     */
    public Set<Annotation> getAnnotations() {
        return new HashSet<>(annotations);
    }

    /**
     * Returns the field configurators.
     *
     * @return field configurators
     */
    public Set<AnnotatedFieldConfiguratorImpl<? super T>> getFieldConfigurators() {
        return new HashSet<>(fieldConfigurators);
    }

    /**
     * Returns the method configurators.
     *
     * @return method configurators
     */
    public Set<AnnotatedMethodConfiguratorImpl<? super T>> getMethodConfigurators() {
        return new HashSet<>(methodConfigurators);
    }

    /**
     * Returns the constructor configurators.
     *
     * @return constructor configurators
     */
    public Set<AnnotatedConstructorConfiguratorImpl<T>> getConstructorConfigurators() {
        return new HashSet<>(constructorConfigurators);
    }

    /**
     * Completes the configuration and returns a modified AnnotatedType.
     *
     * <p>This creates a new AnnotatedType instance with the configured annotations
     * and metadata from all sub-configurators.
     *
     * @return the configured AnnotatedType
     */
    public AnnotatedType<T> complete() {
        System.out.println("[AnnotatedTypeConfigurator] Configuration completed for: " +
                          originalType.getJavaClass().getName());
        System.out.println("[AnnotatedTypeConfigurator]   Type annotations modified: " + annotations.size());
        System.out.println("[AnnotatedTypeConfigurator]   Fields configured: " + fieldConfigurators.size());
        System.out.println("[AnnotatedTypeConfigurator]   Methods configured: " + methodConfigurators.size());
        System.out.println("[AnnotatedTypeConfigurator]   Constructors configured: " + constructorConfigurators.size());

        return new ConfiguredAnnotatedType<>(
            originalType,
            annotations,
            fieldConfigurators,
            methodConfigurators,
            constructorConfigurators
        );
    }

}
