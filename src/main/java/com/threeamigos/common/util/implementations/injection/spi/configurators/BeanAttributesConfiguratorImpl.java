package com.threeamigos.common.util.implementations.injection.spi.configurators;

import com.threeamigos.common.util.implementations.injection.resolution.BeanAttributesImpl;
import jakarta.enterprise.inject.spi.BeanAttributes;
import jakarta.enterprise.inject.spi.configurator.BeanAttributesConfigurator;
import jakarta.enterprise.util.TypeLiteral;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import static com.threeamigos.common.util.implementations.injection.annotations.QualifiersHelper.normalizeBeanQualifiers;

/**
 * Implementation of {@link BeanAttributesConfigurator} used by ProcessBeanAttributes events.
 */
public class BeanAttributesConfiguratorImpl<T> implements BeanAttributesConfigurator<T> {

    private String name;
    private Set<Annotation> qualifiers;
    private Class<? extends Annotation> scope;
    private Set<Class<? extends Annotation>> stereotypes;
    private Set<Type> types;
    private boolean alternative;

    public BeanAttributesConfiguratorImpl(BeanAttributes<T> original) {
        this.name = original.getName();
        this.qualifiers = new HashSet<>(original.getQualifiers());
        this.scope = original.getScope();
        this.stereotypes = new HashSet<>(original.getStereotypes());
        this.types = new HashSet<>(original.getTypes());
        this.alternative = original.isAlternative();
    }

    @Override
    public BeanAttributesConfigurator<T> addType(Type type) {
        if (type != null) {
            types.add(type);
        }
        return this;
    }

    @Override
    public BeanAttributesConfigurator<T> addType(TypeLiteral<?> typeLiteral) {
        if (typeLiteral != null && typeLiteral.getType() != null) {
            types.add(typeLiteral.getType());
        }
        return this;
    }

    @Override
    public BeanAttributesConfigurator<T> addTypes(Type... types) {
        if (types != null) {
            Arrays.stream(types).filter(Objects::nonNull).forEach(this.types::add);
        }
        return this;
    }

    @Override
    public BeanAttributesConfigurator<T> addTypes(Set<Type> types) {
        if (types != null) {
            types.stream().filter(Objects::nonNull).forEach(this.types::add);
        }
        return this;
    }

    @Override
    public BeanAttributesConfigurator<T> addTransitiveTypeClosure(Type type) {
        if (type != null) {
            types.add(type);
            if (type instanceof Class<?>) {
                Class<?> clazz = (Class<?>) type;
                Class<?> current = clazz.getSuperclass();
                while (current != null && current != Object.class) {
                    types.add(current);
                    current = current.getSuperclass();
                }
                types.addAll(Arrays.asList(clazz.getInterfaces()));
            }
        }
        return this;
    }

    @Override
    public BeanAttributesConfigurator<T> types(Type... types) {
        this.types = new HashSet<>();
        addTypes(types);
        return this;
    }

    @Override
    public BeanAttributesConfigurator<T> types(Set<Type> types) {
        this.types = new HashSet<>();
        addTypes(types);
        return this;
    }

    @Override
    public BeanAttributesConfigurator<T> scope(Class<? extends Annotation> scope) {
        if (scope != null) {
            this.scope = scope;
        }
        return this;
    }

    @Override
    public BeanAttributesConfigurator<T> addQualifier(Annotation qualifier) {
        if (qualifier != null) {
            qualifiers.add(qualifier);
        }
        return this;
    }

    @Override
    public BeanAttributesConfigurator<T> addQualifiers(Annotation... qualifiers) {
        if (qualifiers != null) {
            Arrays.stream(qualifiers).filter(Objects::nonNull).forEach(this.qualifiers::add);
        }
        return this;
    }

    @Override
    public BeanAttributesConfigurator<T> addQualifiers(Set<Annotation> qualifiers) {
        if (qualifiers != null) {
            qualifiers.stream().filter(Objects::nonNull).forEach(this.qualifiers::add);
        }
        return this;
    }

    @Override
    public BeanAttributesConfigurator<T> qualifiers(Annotation... qualifiers) {
        this.qualifiers = new HashSet<>();
        addQualifiers(qualifiers);
        return this;
    }

    @Override
    public BeanAttributesConfigurator<T> qualifiers(Set<Annotation> qualifiers) {
        this.qualifiers = new HashSet<>();
        addQualifiers(qualifiers);
        return this;
    }

    @Override
    public BeanAttributesConfigurator<T> addStereotype(Class<? extends Annotation> stereotype) {
        if (stereotype != null) {
            stereotypes.add(stereotype);
        }
        return this;
    }

    @Override
    public BeanAttributesConfigurator<T> addStereotypes(Set<Class<? extends Annotation>> stereotypes) {
        if (stereotypes != null) {
            stereotypes.stream().filter(Objects::nonNull).forEach(this.stereotypes::add);
        }
        return this;
    }

    @Override
    public BeanAttributesConfigurator<T> stereotypes(Set<Class<? extends Annotation>> stereotypes) {
        this.stereotypes = new HashSet<>();
        addStereotypes(stereotypes);
        return this;
    }

    @Override
    public BeanAttributesConfigurator<T> name(String name) {
        this.name = name;
        return this;
    }

    @Override
    public BeanAttributesConfigurator<T> alternative(boolean alternative) {
        this.alternative = alternative;
        return this;
    }

    public BeanAttributes<T> complete() {
        this.qualifiers = new HashSet<>(normalizeBeanQualifiers(this.qualifiers));
        return new BeanAttributesImpl<>(name, qualifiers, scope, stereotypes, types, alternative);
    }
}
