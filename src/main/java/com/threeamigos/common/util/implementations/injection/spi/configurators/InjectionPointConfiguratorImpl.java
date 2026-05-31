package com.threeamigos.common.util.implementations.injection.spi.configurators;

import com.threeamigos.common.util.implementations.injection.annotations.AnnotationPredicates;

import com.threeamigos.common.util.implementations.injection.spi.configured.ConfiguredInjectionPoint;
import com.threeamigos.common.util.implementations.injection.annotations.DefaultLiteral;
import jakarta.enterprise.inject.spi.InjectionPoint;
import jakarta.enterprise.inject.spi.configurator.InjectionPointConfigurator;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import static com.threeamigos.common.util.implementations.injection.annotations.AnnotationPredicates.hasAnyAnnotation;
import static com.threeamigos.common.util.implementations.injection.annotations.AnnotationPredicates.hasDefaultAnnotation;

/**
 * Implementation of {@link InjectionPointConfigurator} used for ProcessInjectionPoint events.
 */
public class InjectionPointConfiguratorImpl implements InjectionPointConfigurator {

    private final InjectionPoint original;
    private Type type;
    private final Set<Annotation> qualifiers;
    private boolean isDelegate;
    private boolean isTransient;

    public InjectionPointConfiguratorImpl(InjectionPoint original) {
        this.original = original;
        this.type = original.getType();
        this.qualifiers = new HashSet<>(original.getQualifiers());
        this.isDelegate = original.isDelegate();
        this.isTransient = original.isTransient();
    }

    @Override
    public InjectionPointConfigurator type(Type type) {
        if (type != null) {
            this.type = type;
        }
        return this;
    }

    @Override
    public InjectionPointConfigurator addQualifier(Annotation qualifier) {
        if (qualifier != null) {
            qualifiers.add(qualifier);
            normalizeDefaultQualifierForInjectionPoint();
        }
        return this;
    }

    @Override
    public InjectionPointConfigurator addQualifiers(Annotation... qualifiers) {
        if (qualifiers != null) {
            Arrays.stream(qualifiers)
                  .filter(Objects::nonNull)
                  .forEach(this.qualifiers::add);
            normalizeDefaultQualifierForInjectionPoint();
        }
        return this;
    }

    @Override
    public InjectionPointConfigurator addQualifiers(Set<Annotation> qualifiers) {
        if (qualifiers != null) {
            qualifiers.stream()
                      .filter(Objects::nonNull)
                      .forEach(this.qualifiers::add);
            normalizeDefaultQualifierForInjectionPoint();
        }
        return this;
    }

    @Override
    public InjectionPointConfigurator qualifiers(Annotation... qualifiers) {
        this.qualifiers.clear();
        if (qualifiers != null) {
            addQualifiers(qualifiers);
        }
        return normalizeDefaultQualifierForInjectionPoint();
    }

    @Override
    public InjectionPointConfigurator qualifiers(Set<Annotation> qualifiers) {
        this.qualifiers.clear();
        if (qualifiers != null) {
            addQualifiers(qualifiers);
        }
        return normalizeDefaultQualifierForInjectionPoint();
    }

    @Override
    public InjectionPointConfigurator delegate(boolean delegate) {
        this.isDelegate = delegate;
        return this;
    }

    @Override
    public InjectionPointConfigurator transientField(boolean transientField) {
        this.isTransient = transientField;
        return this;
    }

    /**
        * Materializes the configured {@link InjectionPoint}.
        */
    public InjectionPoint complete() {
        normalizeDefaultQualifierForInjectionPoint();
        return new ConfiguredInjectionPoint(original, type, qualifiers, isDelegate, isTransient);
    }

    private InjectionPointConfigurator normalizeDefaultQualifierForInjectionPoint() {
        boolean hasDefault = false;
        boolean hasNonDefaultQualifier = false;

        for (Annotation qualifier : qualifiers) {
            if (qualifier == null) {
                continue;
            }
            if (isDefaultQualifier(qualifier)) {
                hasDefault = true;
                continue;
            }
            if (!isAnyQualifier(qualifier)) {
                hasNonDefaultQualifier = true;
            }
        }

        if (hasNonDefaultQualifier) {
            qualifiers.removeIf(this::isDefaultQualifier);
            qualifiers.removeIf(this::isAnyQualifier);
            return this;
        }

        if (!hasDefault && qualifiers.isEmpty()) {
            qualifiers.add(new DefaultLiteral());
        }
        return this;
    }

    private boolean isDefaultQualifier(Annotation qualifier) {
        return qualifier != null && hasDefaultAnnotation(qualifier.annotationType());
    }

    private boolean isAnyQualifier(Annotation qualifier) {
        return qualifier != null && hasAnyAnnotation(qualifier.annotationType());
    }
}
