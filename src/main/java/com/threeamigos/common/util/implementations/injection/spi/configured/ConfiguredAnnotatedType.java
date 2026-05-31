package com.threeamigos.common.util.implementations.injection.spi.configured;

import com.threeamigos.common.util.implementations.injection.spi.configurators.AnnotatedConstructorConfiguratorImpl;
import com.threeamigos.common.util.implementations.injection.spi.configurators.AnnotatedFieldConfiguratorImpl;
import com.threeamigos.common.util.implementations.injection.spi.configurators.AnnotatedMethodConfiguratorImpl;
import jakarta.enterprise.inject.spi.AnnotatedConstructor;
import jakarta.enterprise.inject.spi.AnnotatedField;
import jakarta.enterprise.inject.spi.AnnotatedMethod;
import jakarta.enterprise.inject.spi.AnnotatedType;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * AnnotatedType wrapper that applies all configurator changes (type, members, parameters).
 */
public class ConfiguredAnnotatedType<T> implements AnnotatedType<T> {

    private final AnnotatedType<T> delegate;
    private final Set<Annotation> annotations;
    private final Set<AnnotatedField<? super T>> fields;
    private final Set<AnnotatedMethod<? super T>> methods;
    private final Set<AnnotatedConstructor<T>> constructors;

    public ConfiguredAnnotatedType(
            AnnotatedType<T> delegate,
            Set<Annotation> typeAnnotations,
            Set<AnnotatedFieldConfiguratorImpl<? super T>> fieldConfigurators,
            Set<AnnotatedMethodConfiguratorImpl<? super T>> methodConfigurators,
            Set<AnnotatedConstructorConfiguratorImpl<T>> constructorConfigurators) {

        this.delegate = delegate;
        this.annotations = Collections.unmodifiableSet(new HashSet<>(typeAnnotations));
        this.fields = buildFields(fieldConfigurators);
        this.methods = buildMethods(methodConfigurators);
        this.constructors = buildConstructors(constructorConfigurators);
    }

    @Override
    public Class<T> getJavaClass() {
        return delegate.getJavaClass();
    }

    @Override
    public Set<AnnotatedConstructor<T>> getConstructors() {
        return constructors;
    }

    @Override
    public Set<AnnotatedMethod<? super T>> getMethods() {
        return methods;
    }

    @Override
    public Set<AnnotatedField<? super T>> getFields() {
        return fields;
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
    public <X extends Annotation> X getAnnotation(Class<X> annotationType) {
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

    private Set<AnnotatedField<? super T>> buildFields(Set<AnnotatedFieldConfiguratorImpl<? super T>> fieldConfigurators) {
        Set<AnnotatedField<? super T>> configuredFields = new LinkedHashSet<>();
        for (AnnotatedFieldConfiguratorImpl<? super T> configurator : fieldConfigurators) {
            @SuppressWarnings("unchecked")
            AnnotatedField<T> originalField = (AnnotatedField<T>) configurator.getOriginalField();
            configuredFields.add(new ConfiguredAnnotatedField<>(
                    originalField,
                    configurator.getAnnotations(),
                    this
            ));
        }
        return Collections.unmodifiableSet(configuredFields);
    }

    private Set<AnnotatedMethod<? super T>> buildMethods(Set<AnnotatedMethodConfiguratorImpl<? super T>> methodConfigurators) {
        Set<AnnotatedMethod<? super T>> configuredMethods = new LinkedHashSet<>();
        for (AnnotatedMethodConfiguratorImpl<? super T> configurator : methodConfigurators) {
            configuredMethods.add(new ConfiguredAnnotatedMethod<>(configurator, this));
        }
        return Collections.unmodifiableSet(configuredMethods);
    }

    private Set<AnnotatedConstructor<T>> buildConstructors(Set<AnnotatedConstructorConfiguratorImpl<T>> constructorConfigurators) {
        Set<AnnotatedConstructor<T>> configuredConstructors = new LinkedHashSet<>();
        for (AnnotatedConstructorConfiguratorImpl<T> configurator : constructorConfigurators) {
            configuredConstructors.add(new ConfiguredAnnotatedConstructor<>(configurator, this));
        }
        return Collections.unmodifiableSet(configuredConstructors);
    }
}
